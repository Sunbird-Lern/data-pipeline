package org.sunbird.job.deletioncleanup.functions

import com.datastax.driver.core.Row
import com.datastax.driver.core.querybuilder.QueryBuilder
import org.apache.commons.lang3.StringUtils
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.keycloak.admin.client.resource.UserResource
import org.slf4j.LoggerFactory
import org.sunbird.dp.core.job.{BaseProcessFunction, Metrics}
import org.sunbird.dp.core.util.{CassandraUtil, ElasticSearchUtil, HttpUtil, JSONUtil}
import org.sunbird.job.deletioncleanup.domain.Event
import org.sunbird.job.deletioncleanup.task.UserDeletionCleanupConfig
import org.sunbird.job.deletioncleanup.util.KeyCloakConnectionProvider

import java.text.SimpleDateFormat
import java.util.Date
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

class UserDeletionCleanupFunction(config: UserDeletionCleanupConfig, httpUtil: HttpUtil, esUtil: ElasticSearchUtil)(implicit val mapTypeInfo: TypeInformation[Event], @transient var cassandraUtil: CassandraUtil = null)
  extends BaseProcessFunction[Event, Event](config) {

  private[this] val logger = LoggerFactory.getLogger(classOf[UserDeletionCleanupFunction])

  override def metricsList(): List[String] = {
    List(config.userDeletionCleanupHit, config.skipCount, config.successCount, config.totalEventsCount, config.apiReadMissCount, config.apiReadSuccessCount, config.dbUpdateCount)
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
    logger.info(s"Processing deletion cleanup event from user: ${event.userId}")
    metrics.incCounter(config.totalEventsCount)
    if(event.isValid()(metrics, config, httpUtil)) {
      try {
        val userDBMap: Map[String, AnyRef] = getUserProfileById(event.userId)(config, cassandraUtil)

        // update organisation table
        updateUserOrg(event.userId, event.organisation)(config, cassandraUtil)

        if(userDBMap.getOrElse(config.STATUS, 0).asInstanceOf[Int] != config.DELETION_STATUS) {
          // remove user credentials from keycloak if exists
          removeEntryFromKeycloak(event.userId)(config)

          // remove user entries from lookup table
          removeEntryFromUserLookUp(userDBMap)(config, cassandraUtil)

          // update user entry in user table
          updateUser(event.userId)(config, cassandraUtil)

          // remove user entries from externalId table
          val dbUserExternalIds: List[Map[String, String]] = getUserExternalIds(event.userId)(config, cassandraUtil)
          if(dbUserExternalIds.nonEmpty) deleteUserExternalIds(dbUserExternalIds)(config, cassandraUtil)
        }

        // delete managed users
        if(event.managedUsers != null && !event.managedUsers.isEmpty) {
          event.managedUsers.forEach(managedUser => {
            // update user entry in user table
            updateUser(managedUser)(config, cassandraUtil)
          })
        }

      } catch {
        case ex: Exception =>
          ex.printStackTrace()
          logger.info("Event throwing exception: ", JSONUtil.serialize(event))
          throw ex
      }
    } else metrics.incCounter(config.skipCount)
  }

  def removeEntryFromKeycloak(userId: String)(implicit config: UserDeletionCleanupConfig): Unit = {
    val keycloak = new KeyCloakConnectionProvider().getConnection
    try {
      val fedUserId = getFederatedUserId(userId)
      val resource: UserResource = keycloak.realm(System.getenv("SUNBIRD_SSO_RELAM")).users.get(fedUserId)
      if (null != resource) resource.remove()
    } catch {
      case ex: Exception =>
        logger.error("Error occurred : ", ex)
    }
  }

  def getFederatedUserId(userId: String): String = String.join(":", "f", config.SUNBIRD_KEYCLOAK_USER_FEDERATION_PROVIDER_ID, userId)

  def removeEntryFromUserLookUp(userDbMap: Map[String, AnyRef]) (config: UserDeletionCleanupConfig, cassandraUtil: CassandraUtil): Unit = {
    val identifiers: List[String] = List[String](config.EMAIL, config.PHONE, config.USER_LOOKUP_FILED_EXTERNAL_ID)
    logger.debug("UserDeletionCleanupFunction:removeEntryFromUserLookUp remove following identifiers from lookUp table " + identifiers)
    val reqMap: ListBuffer[Map[String, String]] = new ListBuffer[Map[String, String]]()

    if (identifiers.contains(config.EMAIL) && StringUtils.isNotBlank(userDbMap.get(config.EMAIL).asInstanceOf[String])) {
      val deleteLookUp = Map[String, String] (config.TYPE -> config.EMAIL, config.VALUE -> userDbMap.get(config.EMAIL).asInstanceOf[String])
      reqMap+=deleteLookUp
    }
    if (identifiers.contains(config.PHONE) && StringUtils.isNotBlank(userDbMap.get(config.PHONE).asInstanceOf[String])) {
      val deleteLookUp = Map[String, String] (config.TYPE -> config.PHONE, config.VALUE -> userDbMap.get(config.PHONE).asInstanceOf[String])
      reqMap+=deleteLookUp
    }
    if (identifiers.contains(config.USER_LOOKUP_FILED_EXTERNAL_ID) && StringUtils.isNotBlank(userDbMap.get(config.EXTERNAL_ID).asInstanceOf[String])) {
      val deleteLookUp = Map[String, String] (config.TYPE -> config.USER_LOOKUP_FILED_EXTERNAL_ID, config.VALUE -> userDbMap.get(config.EXTERNAL_ID).asInstanceOf[String])
      reqMap+=deleteLookUp
    }

    if (reqMap.nonEmpty) deleteUserLookUp(reqMap.toList)(config, cassandraUtil)
  }

  def deleteUserLookUp(reqMap: List[Map[String, String]])(config: UserDeletionCleanupConfig, cassandraUtil: CassandraUtil): Unit = {
    logger.info("UserDeletionCleanupFunction:: UserLookUp:: deleteRecords removing " + reqMap.size + " lookups from table")
    reqMap.foreach(dataMap => {
      cassandraUtil.deleteRecordByCompositeKey(config.userKeyspace, config.userLookUpTable, dataMap)
    })
  }

  def getUserProfileById(userId: String)(config: UserDeletionCleanupConfig, cassandraUtil: CassandraUtil): Map[String, AnyRef] = {
    val query = QueryBuilder.select().all()
      .from(config.userKeyspace, config.userTable).
      where(QueryBuilder.eq("id", userId)).toString

    val record:Row = cassandraUtil.findOne(query)
    val recordMap: Map[String, AnyRef] = record.getColumnDefinitions.asList.asScala.toList.flatMap(column => {
      Map(column.getName -> record.getObject(column.getName))
    }).toMap[String, AnyRef]
    recordMap
  }

  def getUserExternalIds(userId: String)(config: UserDeletionCleanupConfig, cassandraUtil: CassandraUtil): List[Map[String, String]] = {
    val query = QueryBuilder.select().all()
      .from(config.userKeyspace, config.userExternalIdentityTable).
      where(QueryBuilder.eq("userid", userId)).toString

    val records:java.util.List[Row] = cassandraUtil.find(query)
    val externalIds: List[Map[String, String]] = records.asScala.toList.map(record => {
      record.getColumnDefinitions.asList.asScala.toList.flatMap(column => {
        Map(column.getName -> record.getString(column.getName))
      }).toMap[String, String]
    })
    externalIds
  }

  def deleteUserExternalIds(reqMap: List[Map[String, String]])(config: UserDeletionCleanupConfig, cassandraUtil: CassandraUtil): Unit = {
    logger.info("UserDeletionCleanupFunction:: UserLookUp:: deleteRecords removing " + reqMap.size + " external Ids from table")
    reqMap.foreach(dataMap => {
      val keymap = dataMap.-("createdby","createdon","lastupdatedby","lastupdatedon","originalexternalid","originalidtype","originalprovider","userid")
      cassandraUtil.deleteRecordByCompositeKey(config.userKeyspace, config.userLookUpTable, keymap)
    })
  }

  def updateUser(id: String)(config: UserDeletionCleanupConfig, cassandraUtil: CassandraUtil): Unit = {
    val updateUserQuery = QueryBuilder.update(config.userKeyspace, config.userTable)
      .`with`(QueryBuilder.set(config.STATUS, config.DELETION_STATUS))
      .and(QueryBuilder.set(config.MASKED_EMAIL, ""))
      .and(QueryBuilder.set(config.MASKED_PHONE, ""))
      .and(QueryBuilder.set(config.RECOVERY_EMAIL, ""))
      .and(QueryBuilder.set(config.RECOVERY_PHONE, ""))
      .and(QueryBuilder.set(config.PREV_USED_EMAIL, ""))
      .and(QueryBuilder.set(config.PREV_USED_PHONE, ""))
      .and(QueryBuilder.set(config.USERNAME, ""))
      .and(QueryBuilder.set(config.FIRST_NAME, ""))
      .and(QueryBuilder.set(config.LAST_NAME, ""))
      .and(QueryBuilder.set(config.DOB, ""))
      .where(QueryBuilder.eq(config.ID, id))

    cassandraUtil.upsert(updateUserQuery.toString)
  }

  def updateUserOrg(userId: String, organisationId: String)(config: UserDeletionCleanupConfig, cassandraUtil: CassandraUtil): Unit = {
    val updateUserQuery = QueryBuilder.update(config.userKeyspace, config.userTable)
      .`with`(QueryBuilder.set(config.IS_DELETED, true))
      .and(QueryBuilder.set(config.ORG_LEFT_DATE, getDateFormatter.format(new Date)))
      .where(QueryBuilder.eq(config.USERID, userId))
      .and(QueryBuilder.eq(config.ORGID, organisationId))

    cassandraUtil.upsert(updateUserQuery.toString)
  }

  def getDateFormatter: SimpleDateFormat = {
    val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSSZ")
    simpleDateFormat.setLenient(false)
    simpleDateFormat
  }

}

