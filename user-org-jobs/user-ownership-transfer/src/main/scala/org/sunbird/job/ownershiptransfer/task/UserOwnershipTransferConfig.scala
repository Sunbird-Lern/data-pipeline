package org.sunbird.job.ownershiptransfer.task

import com.typesafe.config.Config
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.sunbird.job.BaseJobConfig
import org.sunbird.job.ownershiptransfer.domain.Event

class UserOwnershipTransferConfig(override val config: Config) extends BaseJobConfig(config, "UserOwnershipTransferConfig") {

  implicit val mapTypeInfo: TypeInformation[Event] = TypeExtractor.getForClass(classOf[Event])

  // Kafka Topics Configuration
  val inputTopic: String = config.getString("kafka.input.topic")

  // User ownership transfer job metrics
  val skipCount = "skipped-message-count"
  val successCount = "success-message-count"
  val dbUpdateCount = "db-update-success-count"
  val apiReadSuccessCount = "api-read-success-count"
  val totalEventsCount ="total-transfer-events-count"

  val dbCourseBatchTable: String = config.getString("lms-cassandra.course_batch.table")
  val dbKeyspace: String = config.getString("lms-cassandra.keyspace")
  val dbHost: String = config.getString("lms-cassandra.host")
  val dbPort: Int = config.getInt("lms-cassandra.port")

  val userOwnershipTransferParallelism: Int = config.getInt("task.user.ownership.transfer.parallelism")

  //ES configuration
  val esConnection: String = config.getString("es.basePath")
  val searchIndex: String = "course-batch"
  val courseBatchIndexType: String = "_doc"

  //Thresholds
  val thresholdBatchWriteSize: Int = config.getInt("threshold.batch.write.size")

  // constants
  val courseId = "courseid"
  val batchId = "batchid"
  val createdBy = "createdby"
  val mentors = "mentors"

  //API URL
  val lmsServiceBasePath: String = config.getString("service.lms.basePath")
  val userOrgServiceBasePath: String = config.getString("service.userorg.basePath")
  val userReadApi: String = config.getString("user_read_api")
  val batchSearchApi: String = config.getString("batch_search_api")

  // Consumers
  val userOwnershipTransferConsumer: String = "user-ownership-transfer-consumer"

  // Functions
  val userOwnershipTransferFunction: String = "UserOwnershipTransferFunction"


}