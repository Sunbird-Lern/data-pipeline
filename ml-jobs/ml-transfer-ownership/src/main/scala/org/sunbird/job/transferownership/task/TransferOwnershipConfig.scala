package org.sunbird.job.transferownership.task

import com.typesafe.config.Config
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.sunbird.job.BaseJobConfig
import org.sunbird.job.transferownership.domain.Event

class TransferOwnershipConfig(override val config: Config) extends BaseJobConfig(config, "TransferOwnershipConfig") {

  implicit val mapTypeInfo: TypeInformation[Event] = TypeExtractor.getForClass(classOf[Event])

  // Kafka Topics Configuration
  val inputTopic: String = config.getString("kafka.input.topic")

  // Parallelism
  val mlTransferOwnershipParallelism: Int = config.getInt("task.ml.transfer.ownership.parallelism")

  // Consumers
  val mlTransferOwnershipConsumer: String = "ml-transfer-ownership-consumer"

  // Functions
  val transferOwnershipFunction: String = "TransferOwnershipFunction"

  //MongoDB
  val dbHost: String = config.getString("ml-mongo.host")
  val dbPort: Int = config.getInt("ml-mongo.port")
  val dataBase: String = config.getString("ml-mongo.database")

  val PROGRAMS_COLLECTION = "programs"
  val SOLUTIONS_COLLECTION = "solutions"
  val USER_EXTENSION = "userExtension"
  val USER_ROLES= "userRoles"

  // User delete job metrics
  val transferOwnershipCleanupHit = "transfer-ownership-cleanup-hit"
  val skipCount = "skipped-message-count"
  val successCount = "success-message-count"
  val totalEventsCount = "total-transfer-events-count"

  // constants
  val CREATED_BY = "createdBy"
  val USERID = "userId"
  val AUTHOR = "author"
  val OWNER = "owner"
  val ID = "_id"
  val CREATOR = "creator"
  val LICENSE_AUTHOR = "license.author"
  val LICENSE_CREATOR = "license.creator"
  val ROLES_CODE = "code"
  val EXTERNAL_ID = "externalId"
  val STATUS = "status"
  val IS_DELETED = "isDeleted"
  val DEVICES = "devices"
  val PLATFORM_ROLES = "platformRoles"
  val UPDATED_BY = "updatedBy"


}