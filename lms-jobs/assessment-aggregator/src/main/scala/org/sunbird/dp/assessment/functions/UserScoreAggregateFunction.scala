package org.sunbird.dp.assessment.functions

import java.lang.reflect.Type
import java.util
import java.util.{Date, UUID}
import com.datastax.driver.core.Row
import com.datastax.driver.core.querybuilder.QueryBuilder
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.slf4j.LoggerFactory
import org.sunbird.dp.assessment.domain.Event
import org.apache.flink.configuration.Configuration
import org.sunbird.dp.assessment.task.AssessmentAggregatorConfig
import org.sunbird.dp.core.job.{BaseProcessFunction, Metrics}
import org.sunbird.dp.core.util.{CassandraUtil, JSONUtil}

import java.text.SimpleDateFormat
import scala.collection.JavaConverters._

case class AggDetails(attempt_id: String, last_attempted_on: Date, score: Double, content_id: String, max_score: Double, `type`: String)

case class UserActivityAgg(aggregates: Map[String, Double], aggDetails: List[String])

class UserScoreAggregateFunction(config: AssessmentAggregatorConfig,
                                 @transient var cassandraUtil: CassandraUtil = null
                                )(implicit val mapTypeInfo: TypeInformation[Event])
  extends BaseProcessFunction[Event, Event](config) {

  val mapType: Type = new TypeToken[util.Map[String, AnyRef]]() {}.getType
  private[this] val logger = LoggerFactory.getLogger(classOf[UserScoreAggregateFunction])
  override def metricsList() = List(config.dbScoreAggUpdateCount, config.dbScoreAggReadCount,
    config.failedEventCount, config.batchSuccessCount,
    config.skippedEventCount, config.certIssueEventsCount)

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    cassandraUtil = new CassandraUtil(config.dbHost, config.dbPort, config.isMultiDCEnabled)
  }

  override def close(): Unit = {
    super.close()
  }

  def getAggregateDetails(assessAggRows: List[Row]): List[String] = {
    if (null != assessAggRows && assessAggRows.nonEmpty) {
      assessAggRows.map { row =>
        val aggMap = AggDetails(row.getString("attempt_id"), row.getTimestamp("last_attempted_on"),
          row.getDouble("score"), row.getString("content_id"), row.getDouble("total_max_score"), config.aggType)
        new Gson().toJson(aggMap)
      }
    } else List()
  }

  def getAggregates(assessAggRows: List[Row]): Map[String, Double] = {
    if (null != assessAggRows && assessAggRows.nonEmpty) {
      val attemptGroupList = assessAggRows.groupBy(row => row.getString("content_id")).values
      attemptGroupList.flatMap(row => {
        val scoreRow = row.maxBy(r => r.getDouble("score"))

        Map(s"score:${scoreRow.getString("content_id")}" -> scoreRow.getDouble("score"),
          s"max_score:${scoreRow.getString("content_id")}" -> scoreRow.getDouble("total_max_score"),
          s"attempts_count:${scoreRow.getString("content_id")}" -> row.length.toDouble)
      }).toMap
    } else Map[String, Double]()
  }

  def getBestScore(event: Event): UserActivityAgg = {
    val query = QueryBuilder.select().column("content_id").column("attempt_id").column("last_attempted_on").column("total_max_score").column("total_score").as("score").from(config.dbKeyspace, config.dbTable)
      .where(QueryBuilder.eq("course_id", event.courseId)).and(QueryBuilder.eq("batch_id", event.batchId))
      .and(QueryBuilder.eq("user_id", event.userId))
    val rows = cassandraUtil.find(query.toString).asScala.toList
    UserActivityAgg(aggregates = getAggregates(rows), aggDetails = getAggregateDetails(rows))

  }

  def updateUserActivity(event: Event, score: UserActivityAgg): Unit = {
    val scoreLastUpdatedTime: Map[String, Long] = score.aggregates.map(m => m._1 -> System.currentTimeMillis())
    val updateQuery = QueryBuilder.update(config.dbKeyspace, config.activityTable)
      .`with`(QueryBuilder.putAll(config.aggregates, score.aggregates.asJava))
      .and(QueryBuilder.set(config.aggDetails, score.aggDetails.asJava))
      .and(QueryBuilder.putAll(config.aggLastUpdated, scoreLastUpdatedTime.asJava))
      .where(QueryBuilder.eq(config.activityId, event.courseId))
      .and(QueryBuilder.eq(config.activityType, "Course"))
      .and(QueryBuilder.eq(config.contextId, "cb:" + event.batchId))
      .and(QueryBuilder.eq(config.activityUser, event.userId))
    cassandraUtil.upsert(updateQuery.toString)
    logger.info("Successfully updated scores in user activity  - batchid: "
      + event.batchId + " ,userid: " + event.userId + " ,courseid: "
      + event.courseId)
  }

  override def processElement(event: Event,
                              context: ProcessFunction[Event, Event]#Context,
                              metrics: Metrics): Unit = {
    val score: UserActivityAgg = getBestScore(event)
    metrics.incCounter(config.dbScoreAggReadCount)
    if (score.aggregates.nonEmpty || score.aggDetails.nonEmpty) {
      updateUserActivity(event, score)
      metrics.incCounter(config.dbScoreAggUpdateCount)
    } else {
      logger.info("No scores to update for batchid: "
        + event.batchId + " ,userid: " + event.userId + " ,couserid: "
        + event.courseId)
    }
    val DATE_FORMAT = "MMM dd, yyyy, h:mm:ss a"
    val dateFormat = new SimpleDateFormat(DATE_FORMAT)
    val aggDetailsMapList: List[Map[String,AnyRef]] = score.aggDetails.map(rec=> {
      val deserMap = JSONUtil.deserialize[util.Map[String, AnyRef]](rec)
      deserMap.put("last_attempted_on", dateFormat.parse(deserMap.get("last_attempted_on").asInstanceOf[String]))
      deserMap.asScala.toMap
    })
    val latestAttempt: Map[String, AnyRef] = aggDetailsMapList.sortBy(_("last_attempted_on").asInstanceOf[Date])(Ordering[Date].reverse).head

    val latestAttemptId: String = if(score.aggDetails.nonEmpty) latestAttempt.getOrElse("attempt_id", "").asInstanceOf[String] else ""
    createIssueCertEvent(event, context, metrics, latestAttemptId)
  }


  /**
   * Generation of Certificate Issue event for the enrolment completed users to validate and generate certificate.
   *
   * @param batchEvent: Event
   * @param context: ProcessFunction[Event, Event]#Context
   * @param metrics: Metrics
   */
  def createIssueCertEvent(batchEvent: Event, context: ProcessFunction[Event, Event]#Context,
                           metrics: Metrics, latestAttemptId: String): Unit = {
    val ets = System.currentTimeMillis
    val mid = s"""LP.$ets.${UUID.randomUUID}"""
    val event =
      s"""{"eid": "BE_JOB_REQUEST",
         |"ets": $ets,
         |"mid": "$mid",
         |"actor": {"id": "Course Certificate Generator","type": "System"},
         |"context": {"pdata": {"ver": "1.0","id": "org.sunbird.platform"}},
         |"object": {"id": "${batchEvent.batchId}_${batchEvent.courseId}","type": "CourseCertificateGeneration"},
         |"edata": {"userIds": ["${batchEvent.userId}"],"action": "issue-certificate","iteration": 1, "trigger": "auto-issue","batchId": "${batchEvent.batchId}","reIssue": false,"courseId": "${batchEvent.courseId}","attemptId":"$latestAttemptId"}}"""
        .stripMargin.replaceAll("\n", "")
    logger.info("UserScoreAggregateFunction:: createIssueCertEvent:: " + event)
    context.output(config.certIssueOutputTag, event)
    metrics.incCounter(config.certIssueEventsCount)
  }


}
