package org.sunbird.job.merge.user.courses.task

import com.typesafe.config.Config
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.streaming.api.scala.OutputTag
import org.sunbird.job.BaseJobConfig

import java.util

class MergeUserCoursesConfig(override val config: Config) extends BaseJobConfig(config, "merge-user-courses") {

  private val serialVersionUID = 2905979434303791379L

  implicit val mapTypeInfo: TypeInformation[util.Map[String, AnyRef]] = TypeExtractor.getForClass(classOf[util.Map[String, AnyRef]])
  implicit val stringTypeInfo: TypeInformation[String] = TypeExtractor.getForClass(classOf[String])

  // Kafka Topics Configuration
  val kafkaInputTopic: String = config.getString("kafka.input.topic")
  val kafkaOutputTopic: String = config.getString("kafka.output.topic")
  val kafkaOutputFailedTopic: String = config.getString("kafka.output.failed.topic")
  val courseBatchUpdaterTopic: String = config.getString("kafka.output.course.batch.updater.topic")


  override val kafkaConsumerParallelism: Int = config.getInt("task.consumer.parallelism")
  val courseBatchUpdaterProducer = "course-batch-updater-sink"
  val courseBatchUpdaterParallelism:Int = config.getInt("task.course_batch_updater.parallelism")

  // Metric List
  val totalEventsCount = "total-events-count"
  val successEventCount = "success-events-count"
  val failedEventCount = "failed-events-count"
  val skippedEventCount = "skipped-event-count"
  val dbUpdateCount = "db-update-user-course-batch"


  // Consumers
  val mergeUserCoursesConsumer = "merge-user-courses-consumer"

  // Cassandra Configurations
  val contentConsumptionTable: String = config.getString("lms-cassandra.content_consumption.table")
  val userEnrolmentsTable: String = config.getString("lms-cassandra.user_enrolments.table")
  val userActivityAggTable: String = config.getString("lms-cassandra.user_activity_agg.table")
  val dbKeyspace: String = config.getString("lms-cassandra.keyspace")
  val dbHost: String = config.getString("lms-cassandra.host")
  val dbPort: Int = config.getInt("lms-cassandra.port")

  //basic merge components
  val COURSE_DATE_FORMAT = if (config.hasPath("course.date.format")) config.getString("course.date.format") else "yyyy-MM-dd HH:mm:ss:SSSZ"
  val userId = "userId"
  val contentId = "contentId"
  val batchId = "batchId"
  val courseId = "courseId"
  val lastUpdatedTime = "lastupdatedtime"
  val lastCompletedTime = "lastcompletedtime"
  val lastAccessTime= "lastaccesstime"
  val last_updated_time = "last_updated_time"
  val last_completed_time = "last_completed_time"
  val last_access_time= "last_access_time"
  val dateTime = "datetime"
  val completedCount = "completedcount"
  val viewCount = "viewcount"
  val progress = "progress"
  val status = "status"
  val activity_type = "activity_type"
  val activity_id = "activity_id"
  val user_id = "user_id"

  val eventOutputTagName = "output-event"
  val eventOutputTag: OutputTag[String] = OutputTag[String](eventOutputTagName)
  val failedEventOutputTag: OutputTag[String] = OutputTag[String]("failed-output-event")


}
