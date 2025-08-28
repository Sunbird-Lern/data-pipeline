package org.sunbird.job.deletioncleanup.task

import com.typesafe.config.Config
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.streaming.api.scala.OutputTag
import org.sunbird.job.BaseJobConfig
import org.sunbird.job.deletioncleanup.domain.Event

class UserDeletionCleanupConfig(override val config: Config) extends BaseJobConfig(config, "UserDeletionCleanupConfig") {

  implicit val mapTypeInfo: TypeInformation[Event] = TypeExtractor.getForClass(classOf[Event])

  // Kafka Topics Configuration
  val inputTopic: String = config.getString("kafka.input.topic")

  // User deletion cleanup job metrics
  val skipCount = "skipped-message-count"
  val successCount = "success-message-count"
  val dbUpdateCount = "db-update-success-count"
  val apiReadSuccessCount = "api-read-success-count"
  val apiReadMissCount = "api-read-miss-count"
  val totalEventsCount ="total-delete-events-count"

  val auditEventOutputTagName = "audit-events"
  val auditEventOutputTag: OutputTag[String] = OutputTag[String](auditEventOutputTagName)

  val dbHost: String = config.getString("lms-cassandra.host")
  val dbPort: Int = config.getInt("lms-cassandra.port")

  val userDeletionCleanupParallelism: Int = config.getInt("task.user.deletion.cleanup.parallelism")

  // constants
  val EMAIL = "email"
  val PHONE = "phone"
  val USER_LOOKUP_FILED_EXTERNAL_ID = "externalid"
  val EXTERNAL_ID = "externalId"
  val TYPE = "type"
  val VALUE = "value"
  val ID = "id"
  val STATUS = "status"
  val DELETION_STATUS = 2
  val MASKED_EMAIL = "maskedEmail"
  val MASKED_PHONE = "maskedPhone"
  val FIRST_NAME = "firstName"
  val LAST_NAME = "lastName"
  val DOB = "dob"
  val PREV_USED_EMAIL = "prevusedemail"
  val PREV_USED_PHONE = "prevusedphone"
  val RECOVERY_EMAIL = "recoveryemail"
  val RECOVERY_PHONE = "recoveryphone"
  val USERNAME = "username"
  val USERID = "userid"
  val ORGID = "organisationid"
  val IS_DELETED = "isdeleted"
  val ORG_LEFT_DATE = "orgleftdate"

  //API URL
  val lmsServiceBasePath: String = config.getString("service.lms.basePath")
  val userOrgServiceBasePath: String = config.getString("service.userorg.basePath")
  val userReadApi: String = config.getString("user_read_api")
  val batchSearchApi: String = config.getString("batch_search_api")

  val userKeyspace: String = config.getString("user.keyspace")
  val userOrgTable: String = config.getString("user.org.table")
  val userLookUpTable: String = config.getString("user.lookup.table")
  val userTable: String = config.getString("user.table")
  val userExternalIdentityTable: String = config.getString("user.externalIdentity.table")

  //Keycloak configurations that are needed for authentication when the delete api fails to delete the user
  val keycloakBaseUrl: String = config.getString("sunbird_sso_url")
  val keycloakRealm: String = config.getString("sunbird_sso_realm")
  val keycloakClientId: String = config.getString("sunbird_sso_client_id")
  val keycloakClientSecret: String = config.getString("sunbird_sso_client_secret")
  val keycloakPassword: String = config.getString("sunbird_sso_password")
  val keycloakUsername: String = config.getString("sunbird_sso_username")
  val keycloakPoolSize: Int = if (config.hasPath("sunbird_sso_pool_size")) config.getInt("sunbird_sso_pool_size") else 2
  val SUNBIRD_KEYCLOAK_USER_FEDERATION_PROVIDER_ID: String = config.getString("sunbird_keycloak_user_federation_provider_id")

  //Redis configurations required to delete the user cache
  //Redis configurations required to delete the user cache. DB index 12 is the dedicated index for user data in our system
  val userDBIndex: Int = if (config.hasPath("user.redis.store")) config.getInt("user.redis.store") else 12
  val userStoreKeyPrefix: String = "user:"
  // Consumers
  val userDeletionCleanupConsumer: String = "user-deletion-cleanup-consumer"

  // Functions
  val userDeletionCleanupFunction: String = "UserDeletionCleanupFunction"

}
