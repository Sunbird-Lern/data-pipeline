package org.sunbird.job.userdelete.task

import com.typesafe.config.Config
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.sunbird.job.BaseJobConfig
import org.sunbird.job.userdelete.domain.Event

class UserDeleteConfig (override val config: Config) extends BaseJobConfig(config, "UserDeleteConfig") {

  implicit val mapTypeInfo: TypeInformation[Event] = TypeExtractor.getForClass(classOf[Event])

  // Kafka Topics Configuration
  val inputTopic: String = config.getString("kafka.input.topic")

  // Parallelism
  val mlUserDeleteParallelism: Int = config.getInt("task.ml.user.delete.parallelism")

  // Consumers
  val mlUserDeleteConsumer: String = "ml-user-delete-consumer"

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
  val SOLUTIONS_COLLECTION = "solutions"

  // User delete job metrics
  val userDeletionCleanupHit = "user-deletion-cleanup-hit"
  val skipCount = "skipped-message-count"
  val successCount = "success-message-count"
  val totalEventsCount = "total-transfer-events-count"

  // constants
  val CREATEDBY = "createdBy"
  val USERID = "userId"
  val AUTHOR = "author"
  val FIRST_NAME = "userProfile.firstName"
  val LAST_NAME = "userProfile.lastName"
  val DOB = "userProfile.dob"
  val EMAIL = "userProfile.email"
  val MASKED_EMAIL = "userProfile.maskedEmail"
  val RECOVERY_EMAIL = "userProfile.recoveryEmail"
  val PREV_USED_EMAIL = "userProfile.prevUsedEmail"
  val ENC_EMAIL = "userProfile.encEmail"
  val PHONE = "userProfile.phone"
  val MASKED_PHONE = "userProfile.maskedPhone"
  val RECOVERY_PHONE = "userProfile.recoveryPhone"
  val PREV_USED_PHONE = "userProfile.prevUsedPhone"
  val ENC_PHONE = "userProfile.encPhone"
  val CREATOR = "creator"
  val LICENSE_AUTHOR = "license.author"
  val LICENSE_CREATOR = "license.creator"
  val OBS_INFO_FIRST_NAME = "observationInformation.userProfile.firstName"
  val OBS_INFO_LAST_NAME = "observationInformation.userProfile.lastName"
  val OBS_INFO_DOB = "observationInformation.userProfile.dob"
  val OBS_INFO_EMAIL = "observationInformation.userProfile.email"
  val OBS_INFO_MASKED_EMAIL = "observationInformation.userProfile.maskedEmail"
  val OBS_INFO_RECOVERY_EMAIL = "observationInformation.userProfile.recoveryEmail"
  val OBS_INFO_PREV_USED_EMAIL = "observationInformation.userProfile.prevUsedEmail"
  val OBS_INFO_ENC_EMAIL = "observationInformation.userProfile.encEmail"
  val OBS_INFO_PHONE = "observationInformation.userProfile.phone"
  val OBS_INFO_MASKED_PHONE = "observationInformation.userProfile.maskedPhone"
  val OBS_INFO_RECOVERY_PHONE = "observationInformation.userProfile.recoveryPhone"
  val OBS_INFO_PREV_USED_PHONE = "observationInformation.userProfile.prevUsedPhone"
  val OBS_INFO_ENC_PHONE = "observationInformation.userProfile.encPhone"
}
