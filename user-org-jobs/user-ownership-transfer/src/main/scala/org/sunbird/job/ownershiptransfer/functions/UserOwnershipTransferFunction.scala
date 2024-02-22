package org.sunbird.job.ownershiptransfer.functions

import com.datastax.driver.core.querybuilder.{QueryBuilder, Update}
import org.apache.commons.lang3.StringUtils
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.slf4j.LoggerFactory
import org.sunbird.job.{BaseProcessFunction, Metrics}
import org.sunbird.job.ownershiptransfer.domain.Event
import org.sunbird.job.ownershiptransfer.task.UserOwnershipTransferConfig
import org.sunbird.job.util.{CassandraUtil, ElasticSearchUtil, HttpUtil, JSONUtil}

import scala.collection.JavaConverters._
import java.util

class UserOwnershipTransferFunction(config: UserOwnershipTransferConfig, httpUtil: HttpUtil, esUtil: ElasticSearchUtil)(implicit val mapTypeInfo: TypeInformation[Event], @transient var cassandraUtil: CassandraUtil = null)
  extends BaseProcessFunction[Event, Event](config) {

  private[this] val logger = LoggerFactory.getLogger(classOf[UserOwnershipTransferFunction])

  override def metricsList(): List[String] = {
    List(config.userOwnershipTransferHit, config.skipCount, config.successCount, config.totalEventsCount, config.apiReadMissCount, config.apiReadSuccessCount, config.dbUpdateCount)
  }

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    cassandraUtil = new CassandraUtil(config.dbHost, config.dbPort, config.isMultiDCEnabled)
  }

  override def close(): Unit = {
    cassandraUtil.close()
    super.close()
  }

  override def processElement(event: Event, context: ProcessFunction[Event, Event]#Context, metrics: Metrics): Unit = {
    logger.info(s"Processing ownership transfer event from user: ${event.fromUserId} to user: ${event.toUserId}")
    metrics.incCounter(config.totalEventsCount)
    if(event.isValid()(metrics, config, httpUtil)) {
      try {
        // search for batches of the From_user. (and also mentor)
        val requestBody = s"""{
                             |    "request": {
                             |        "filters": {
                             |            "createdBy": "${event.fromUserId}",
                             |            "status": [0,1]
                             |        },
                             |        "fields": ["identifier", "createdFor","batchId","courseId","startDate","enrollmentType"]
                             |    }
                             |}""".stripMargin

        val response = httpUtil.post(config.lmsServiceBasePath + config.batchSearchApi, requestBody)
        if (response.status == 200) {
          val responseBody = JSONUtil.deserialize[util.HashMap[String, AnyRef]](response.body)
          val result = responseBody.getOrDefault("result", Map[String, AnyRef]()).asInstanceOf[Map[String, AnyRef]].getOrElse("response", Map[String, AnyRef]()).asInstanceOf[Map[String, AnyRef]]
          val count = result.getOrElse("count", 0.asInstanceOf[Number]).asInstanceOf[Number].intValue()
          if (count > 0) {
            val batchesList = result.getOrElse("content", List[Map[String, AnyRef]]()).asInstanceOf[List[Map[String, AnyRef]]]

            // course_batch update with createdBy to toUserId.
            val batchCreatedByQueries = getCreatedByUpdateQueries(batchesList, event.toUserId)
            updateDB(config.thresholdBatchWriteSize, batchCreatedByQueries)(metrics)

            // update ES
            updateES(batchesList, event)

          } else throw new Exception(s"Could not fetch Batches of user : ${event.fromUserId}")
        } else {
          logger.info("search-service error: " + response.body)
          throw new Exception("search-service not returning error:" + response.status)
        }

        val mentorRequestBody = s"""{
                             |    "request": {
                             |        "filters": {
                             |            "mentors": ["${event.fromUserId}"],
                             |            "status": [0,1]
                             |        },
                             |        "fields": ["identifier", "createdFor","batchId","courseId","startDate","enrollmentType","mentors]
                             |    }
                             |}""".stripMargin

        val mentorResponse = httpUtil.post(config.lmsServiceBasePath + config.batchSearchApi, mentorRequestBody)
        if (mentorResponse.status == 200) {
          val mentorResponseBody = JSONUtil.deserialize[Map[String, AnyRef]](mentorResponse.body)
          val result = mentorResponseBody.getOrElse("result", Map[String, AnyRef]()).asInstanceOf[Map[String, AnyRef]].getOrElse("response", Map[String, AnyRef]()).asInstanceOf[Map[String, AnyRef]]
          val count = result.getOrElse("count", 0.asInstanceOf[Number]).asInstanceOf[Number].intValue()
          if (count > 0) {
            val batchesList = result.getOrElse("content", List[Map[String, AnyRef]]()).asInstanceOf[List[Map[String, AnyRef]]]

            // course_batch update with mentors to toUserId.
            val batchCreatedByQueries = getMentorsUpdateQueries(batchesList, event.fromUserId, event.toUserId)
            updateDB(config.thresholdBatchWriteSize, batchCreatedByQueries)(metrics)

            // update ES
            updateES(batchesList, event)
          } else throw new Exception(s"Could not fetch Batches of user : ${event.fromUserId}")
        } else {
          logger.info("search-service error: " + response.body)
          throw new Exception("search-service not returning error:" + response.status)
        }
      } catch {
        case ex: Exception =>
          ex.printStackTrace()
          logger.info("Event throwing exception: ", JSONUtil.serialize(event))
          throw ex
      }
    } else metrics.incCounter(config.skipCount)
  }


  /**
   * Method to update the specific table in a batch format.
   */
  def updateDB(batchSize: Int, queriesList: List[Update.Where])(implicit metrics: Metrics): Unit = {
    val groupedQueries = queriesList.grouped(batchSize).toList
    groupedQueries.foreach(queries => {
      val cqlBatch = QueryBuilder.batch()
      queries.map(query => cqlBatch.add(query))
      val result = cassandraUtil.upsert(cqlBatch.toString)
      if (result) {
        metrics.incCounter(config.dbUpdateCount)
      } else {
        val msg = "Database update has failed: " + cqlBatch.toString
        logger.info(msg)
        throw new Exception(msg)
      }
    })
  }

  def getCreatedByUpdateQueries(batchesList: List[Map[String, AnyRef]], toUserId: String): List[Update.Where] = {
    batchesList.map(batchInfo => {
      QueryBuilder.update(config.dbKeyspace, config.dbCourseBatchTable)
        .`with`(QueryBuilder.set(config.createdBy, toUserId))
        .where(QueryBuilder.eq(config.batchId.toLowerCase(), batchInfo.getOrElse("batchId","").asInstanceOf[String]))
        .and(QueryBuilder.eq(config.courseId.toLowerCase(), batchInfo.getOrElse("courseId","").asInstanceOf[String]))
    })
  }

  def getMentorsUpdateQueries(batchesList: List[Map[String, AnyRef]], fromUserId: String, toUserId: String): List[Update.Where] = {
    batchesList.map(batchInfo => {
      val batchMentors = JSONUtil.deserialize[util.ArrayList[String]](JSONUtil.serialize(batchInfo.getOrElse("mentors", new util.ArrayList[String]())))
      batchMentors.remove(fromUserId)
      batchMentors.add(toUserId)
      QueryBuilder.update(config.dbKeyspace, config.dbCourseBatchTable)
        .`with`(QueryBuilder.set(config.mentors, batchMentors))
        .where(QueryBuilder.eq(config.batchId.toLowerCase(), batchInfo.getOrElse("batchId","").asInstanceOf[String]))
        .and(QueryBuilder.eq(config.courseId.toLowerCase(), batchInfo.getOrElse("courseId","").asInstanceOf[String]))
    })
  }

  def updateES(batchesList: List[Map[String, AnyRef]], event: Event): Unit = {
    batchesList.foreach(batchInfo => {
      val batchId = batchInfo.getOrElse("batchId","").asInstanceOf[String]
      if(batchId.nonEmpty) {
        val esBatchDoc = esUtil.getDocumentAsString(batchId)
        val updatedESBatchDoc = StringUtils.replace(esBatchDoc, event.fromUserId, event.toUserId)
        esUtil.updateDocument(batchId, updatedESBatchDoc)
      }
    })
  }
}

