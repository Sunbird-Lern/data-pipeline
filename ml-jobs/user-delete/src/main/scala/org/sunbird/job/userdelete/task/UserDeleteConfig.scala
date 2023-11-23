package org.sunbird.job.userdelete.task

import com.typesafe.config.Config
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.streaming.api.scala.OutputTag
import org.sunbird.dp.core.job.BaseJobConfig
import org.sunbird.job.userdelete.domain.Event

class UserDeleteConfig (override val config: Config) extends BaseJobConfig(config, "UserDeleteConfig") {

  implicit val mapTypeInfo: TypeInformation[Event] = TypeExtractor.getForClass(classOf[Event])

  // Kafka Topics Configuration
  val inputTopic: String = config.getString("kafka.input.topic")

  // Parallelism
  val userDeletionCleanupParallelism: Int = config.getInt("task.user.deletion.cleanup.parallelism")

  // Consumers
  val userDeletionCleanupConsumer: String = "user-deletion-cleanup-consumer"

  // Functions
  val userDeleteFunction: String = "UserDeleteFunction"


  //MongoDB
  val dbHost: String = config.getString("ml-mongo.host")
  val dbPort: Int = config.getInt("ml-mongo.port")
  val dataBase: String = config.getString("ml-mongo.database")

  val OBSERVATION_COLLECTION = "observations"
  val SURVEY_SUBMISSION_COLLECTION  = "surveySubmissions"
  val OBSERVATION_SUBMISSION_COLLECTION = "observationSubmissions"
  val PROJECTS_COLLECTION = "projects"
  val PROGRAM_USERS_COLLECTION = "programUsers"


  // User deletion cleanup job metrics
  val userDeletionCleanupHit = "user-deletion-cleanup-hit"
  val skipCount = "skipped-message-count"
  val successCount = "success-message-count"
  val dbReadSuccessCount = "db-read-success-count"
  val dbUpdateCount = "db-update-success-count"
  val dbReadMissCount = "db-read-miss-count"
  val apiReadSuccessCount = "api-read-success-count"
  val apiReadMissCount = "api-read-miss-count"
  val totalEventsCount = "total-transfer-events-count"

  // constants
  val CREATEDBY = "createdBy"
  val USERID = "userId"
  val FIRSTNAME = "userProfile.firstName"
  val LAST_NAME = "userProfile.lastName"
  val DOB = "userProfile.dob"
  val EMAIL = "userProfile.email"
  val MASKED_EMAIL = "userProfile.maskedEmail"
  val RECOVERY_EMAIL = "userProfile.recoveryEmail"
  val PREV_USED_EMAIL = "userProfile.prevUsedEmail"
  val PHONE = "userProfile.phone"
  val MASKED_PHONE = "userProfile.maskedPhone"
  val RECOVERY_PHONE = "userProfile.recoveryPhone"
  val PREV_USED_PHONE = "userProfile.prevUsedPhone"


}
