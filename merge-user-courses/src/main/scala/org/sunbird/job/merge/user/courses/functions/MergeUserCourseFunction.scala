package org.sunbird.job.merge.user.courses.functions

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.slf4j.LoggerFactory
import org.sunbird.job.exception.InvalidEventException
import org.sunbird.job.{BaseProcessFunction, Metrics}
import org.sunbird.job.merge.user.courses.domain.Event
import org.sunbird.job.merge.user.courses.task.MergeUserCoursesConfig
import org.sunbird.job.merge.user.courses.util.MergeOperations
import org.sunbird.job.util.{CassandraUtil, ScalaJsonUtil}

import java.util
import java.util.HashMap

class MergeUserCourseFunction(config: MergeUserCoursesConfig)
                             (implicit val stringTypeInfo: TypeInformation[String],
                          @transient var cassandraUtil: CassandraUtil = null)
  extends BaseProcessFunction[Event, String](config) {

  private[this] val logger = LoggerFactory.getLogger(classOf[MergeUserCourseFunction])
  lazy private val mapper: ObjectMapper = new ObjectMapper()

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    cassandraUtil = new CassandraUtil(config.dbHost, config.dbPort)
  }

  override def close(): Unit = {
    cassandraUtil.close()
    super.close()
  }

  override def processElement(event: Event, context: ProcessFunction[Event, String]#Context, metrics: Metrics): Unit = {

    logger.info("Started merged user course")
    metrics.incCounter(config.totalEventsCount)

    try{
      if (event.isValidEvent()) {
        val fromAccountId = event.fromAccountId
        val toAccountId = event.toAccountId
        logger.info("Processing - fromAccountId: " + fromAccountId)
        logger.info("Processing - toAccountId: " + toAccountId)

        val mergeOperations = new MergeOperations(config, cassandraUtil)
        mergeOperations.mergeContentConsumption(fromUserId = fromAccountId, toUserId = toAccountId)
        mergeOperations.mergeUserActivityAggregates(fromUserId = fromAccountId, toUserId = toAccountId)
        mergeOperations.mergeEnrolments(fromUserId = fromAccountId, toUserId = toAccountId)
        mergeOperations.generateBatchEnrollmentSyncEvents(toAccountId, context)(metrics)

        logger.info("Merged user course successfully")
        metrics.incCounter(config.successEventCount)
      } else {
        logger.error("Failed merge user course for invalid data in event: " +event)
        generateFailedEvents(event.jobName, "ERR_MERGE_USER_COURSES_SAMZA", context)
        metrics.incCounter(config.skippedEventCount)
      }
    } catch {
      case e: Exception =>
        metrics.incCounter(config.failedEventCount)
        logger.error("Failed merge user course event for userid "+event.toAccountId+" message:"+e.getMessage, Map("partition" -> event.partition, "offset" -> event.offset))
        generateFailedEvents(event.jobName, e.getMessage, context)
        throw new InvalidEventException(e.getMessage, Map("partition" -> event.partition, "offset" -> event.offset), e)
    }

  }

  private def generateFailedEvents(jobName: String, errMessage: String, context: ProcessFunction[Event, String]#Context): Unit = {
    val objects = new util.HashMap[String, AnyRef]() {
      put("jobName", jobName)
      put("failInfo", new HashMap[String, AnyRef]() {
        put("errorCode", "DATA_ERROR")
        put("error", errMessage)
      })
    }
    val event = ScalaJsonUtil.serialize(objects)
    logger.info("MergeCourseBatch: Course batch updated failed")
    context.output(config.failedEventOutputTag, event)
  }

  override def metricsList(): List[String] = {
    List(config.successEventCount, config.failedEventCount, config.skippedEventCount, config.totalEventsCount, config.dbUpdateCount)
  }

}
