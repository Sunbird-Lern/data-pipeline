package org.sunbird.job.deletioncleanup.functions

import com.datastax.driver.core.Row
import com.datastax.driver.core.querybuilder.QueryBuilder
import com.google.gson.Gson
import org.apache.commons.lang3.StringUtils
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.keycloak.admin.client.resource.UserResource
import org.keycloak.representations.idm.UserRepresentation
import org.slf4j.LoggerFactory
import org.sunbird.job.deletioncleanup.domain._
import org.sunbird.job.deletioncleanup.task.UserDeletionCleanupConfig
import org.sunbird.job.deletioncleanup.util.KeyCloakConnectionProvider
import org.sunbird.job.util.{CassandraUtil, HttpUtil, JSONUtil}
import org.sunbird.job.{BaseProcessFunction, Metrics}

import java.text.SimpleDateFormat
import java.util.Date
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer

class UserDeletionCleanupFunction(config: UserDeletionCleanupConfig, httpUtil: HttpUtil)(implicit val mapTypeInfo: TypeInformation[Event], @transient var cassandraUtil: CassandraUtil = null)
  extends BaseProcessFunction[Event, Event](config) {

  private[this] val logger = LoggerFactory.getLogger(classOf[UserDeletionCleanupFunction])
  lazy private val gson = new Gson()

  override def metricsList(): List[String] = {
    List(config.skipCount, config.successCount, config.totalEventsCount, config.apiReadMissCount, config.apiReadSuccessCount, config.dbUpdateCount)
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
    logger.info(s"${event}")
    val entryLog = s"Entry Log:UserDeletionCleanup, Message:Context ${event.context}"
    logger.info(entryLog)
    metrics.incCounter(config.totalEventsCount)
    val url = config.userOrgServiceBasePath + config.userReadApi + "/" + event.userId + "?identifier,rootOrgId"
    val userReadResp = httpUtil.get(url)
    if (200 == userReadResp.status) {
      logger.info(s"The user is not yet deleted/blocked, processing the cleanup for: ${event.userId}")
      metrics.incCounter(config.apiReadSuccessCount)
      val response = JSONUtil.deserialize[Map[String, AnyRef]](userReadResp.body)
      val userDetails = response.getOrElse("result", Map[String, AnyRef]()).asInstanceOf[Map[String, AnyRef]].getOrElse("response", Map[String, AnyRef]()).asInstanceOf[Map[String, AnyRef]]
      val responseUserId = userDetails.getOrElse("identifier", "").asInstanceOf[String]
      val responseOrgId = userDetails.getOrElse("rootOrgId","").asInstanceOf[String]
      if(event.isValid(responseUserId,responseOrgId)) {
        try {
          val userDBMap: Map[String, AnyRef] = getUserProfileById(event.userId)(config, cassandraUtil)

          // update organisation table
          updateUserOrg(event.userId, event.organisation)(config, cassandraUtil)

          if(userDBMap.getOrElse(config.STATUS, 0).asInstanceOf[Int] != config.DELETION_STATUS) {
            var deletionStatus: Map[String, Boolean] = Map[String, Boolean]("keycloakCredentials" -> false, "userLookUpTable" -> false, "userExternalIdTable" -> false, "userTable" -> false)

            try {
              // remove user credentials from keycloak if exists
              removeEntryFromKeycloak(event.userId)(config)
              deletionStatus + ("keycloakCredentials" -> true)
            } catch {
              case ex: Exception =>
                val exitLog = s"Exit Log:UserDeletionCleanup, Message:Context ${event.context},error:${ex}"
                logger.info(exitLog)
                logger.error("Error occurred : ", ex)
            }

            // remove user entries from lookup table
            removeEntryFromUserLookUp(userDetails)(config, cassandraUtil)
            deletionStatus + ("userLookUpTable" -> true)

            // update user entry in user table
            updateUser(event.userId)(config, cassandraUtil)
            deletionStatus + ("userTable" -> true)

            // remove user entries from externalId table
            val dbUserExternalIds: List[Map[String, String]] = getUserExternalIds(event.userId)(config, cassandraUtil)
            if(dbUserExternalIds.nonEmpty) deleteUserExternalIds(dbUserExternalIds)(config, cassandraUtil)
            deletionStatus + ("userExternalIdTable" -> true)


            //Generate AUDIT telemetry event
            val props = deletionStatus.map{case (k, v) => k + ":" + v}.mkString("{", ", ", "}")
            generateAuditEvent(Map[String,AnyRef]("userId" -> event.userId, "channel" -> event.organisation, "props" -> props), context) (config)
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
            val exitLog = s"Exit Log:UserDeletionCleanup, Message:Context ${event.context},error:${ex}"
            logger.info(exitLog)
            throw ex
        }
        val exitLog = s"Exit Log:UserDeletionCleanup, Message:Context ${event.context}"
        logger.info(exitLog)
      } else{
        val exitLog = s"Exit Log:UserDeletionCleanup, Message:Context ${event.context},error:" +
          s"The user details for the given Event is invalid"
        logger.info(exitLog)
        metrics.incCounter(config.skipCount)
      }
    }else if(400 == userReadResp.status){
      logger.info(s"User ${event.userId} is already deleted. Updating org table and removing managed users if any.")
      metrics.incCounter(config.apiReadSuccessCount)
      val query = QueryBuilder.select().column("userid").column("rootorgid")
        .from(config.userKeyspace, config.userTable).
        where(QueryBuilder.eq("userid", event.userId)).allowFiltering().toString
      val record: Row = cassandraUtil.findOne(query)
      val recordMap: Map[String, AnyRef] = record.getColumnDefinitions.asList.asScala.toList.flatMap { column =>
        Map(column.getName -> record.getObject(column.getName))
      }.toMap[String, AnyRef]
      val userDetails: Seq[(String, AnyRef)] = recordMap.toSeq
      val responseUserId: String = userDetails.find(_._1 == "userid").map(_._2.toString).getOrElse("")
      val responseOrgId: String = userDetails.find(_._1 == "rootorgid").map(_._2.toString).getOrElse("")
      if (event.isValid(responseUserId,responseOrgId)) {
        try {
          // update organisation table
          updateUserOrg(event.userId, event.organisation)(config, cassandraUtil)

          // delete managed users
          if (event.managedUsers != null && !event.managedUsers.isEmpty) {
            event.managedUsers.forEach(managedUser => {
              // update user entry in user table
              updateUser(managedUser)(config, cassandraUtil)
            })
          }
        } catch {
          case ex: Exception =>
            val exitLog = s"Exit Log:UserDeletionCleanup, Message:Context ${event.context}error: ${ex}"
            logger.info(exitLog)
            throw ex
        }
        val exitLog = s"Exit Log:UserDeletionCleanup, Message:Context ${event.context}"
        logger.info(exitLog)
      } else{
        val exitLog = s"Exit Log:UserDeletionCleanup, Message:Context ${event.context},error:," +
          s"The user details for the given Event is invalid"
        logger.info(exitLog)
        metrics.incCounter(config.skipCount)
      }
    }
    else{
      val exitLog = s"Exit Log:UserDeletionCleanup, Message:Context ${event.context},error:," +
        s"The user details for the given Event is not found"
      logger.info(exitLog)
      metrics.incCounter(config.skipCount)
    }
  }

  def removeEntryFromKeycloak(userId: String)(implicit config: UserDeletionCleanupConfig): Unit = {
    val keycloak = new KeyCloakConnectionProvider().getConnection
    val fedUserId = getFederatedUserId(userId)
    val resource: UserResource = keycloak.realm(System.getenv("SUNBIRD_SSO_RELAM")).users.get(fedUserId)
    try {
      if (null != resource) resource.remove()
    } catch {
      case ex: Exception =>
        logger.error("Error occurred : ", ex)
        val userRep: UserRepresentation = resource.toRepresentation
        userRep.setEmail("")
        userRep.setEmailVerified(false)
        userRep.setEnabled(false)
        userRep.setFirstName("")
        userRep.setLastName("")
        resource.update(userRep)
    }
  }

  def getFederatedUserId(userId: String): String = String.join(":", "f", config.SUNBIRD_KEYCLOAK_USER_FEDERATION_PROVIDER_ID, userId)

  def removeEntryFromUserLookUp(userDetails: Map[String, AnyRef]) (config: UserDeletionCleanupConfig, cassandraUtil: CassandraUtil): Unit = {
    val reqMap: ListBuffer[Map[String, String]] = new ListBuffer[Map[String, String]]()

    if (StringUtils.isNotBlank(userDetails.getOrElse(config.EMAIL, "").asInstanceOf[String])) {
      val deleteLookUp = Map[String, String] (config.TYPE -> config.EMAIL, config.VALUE -> userDetails.getOrElse(config.EMAIL, "").asInstanceOf[String])
      reqMap+=deleteLookUp
    }
    if (StringUtils.isNotBlank(userDetails.getOrElse(config.PHONE, "").asInstanceOf[String])) {
      val deleteLookUp = Map[String, String] (config.TYPE -> config.PHONE, config.VALUE -> userDetails.getOrElse(config.PHONE, "").asInstanceOf[String])
      reqMap+=deleteLookUp
    }

    if (StringUtils.isNotBlank(userDetails.getOrElse(config.EXTERNAL_ID, "").asInstanceOf[String])) {
      val deleteLookUp = Map[String, String](config.TYPE -> config.USER_LOOKUP_FILED_EXTERNAL_ID, config.VALUE -> userDetails.getOrElse(config.EXTERNAL_ID, "").asInstanceOf[String])
      reqMap += deleteLookUp
    }

    if (StringUtils.isNotBlank(userDetails.getOrElse(config.USERNAME, "").asInstanceOf[String])) {
      val deleteLookUp = Map[String, String](config.TYPE -> config.USERNAME, config.VALUE -> userDetails.getOrElse(config.USERNAME, "").asInstanceOf[String])
      reqMap += deleteLookUp
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
    val query = QueryBuilder.select().column("provider").column("idtype").column("userid")
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
      cassandraUtil.deleteRecordByCompositeKey(config.userKeyspace, config.userExternalIdentityTable, dataMap)
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
      .and(QueryBuilder.set(config.EMAIL, ""))
      .and(QueryBuilder.set(config.PHONE, ""))
      .where(QueryBuilder.eq(config.ID, id))

    cassandraUtil.upsert(updateUserQuery.toString)
  }

  def updateUserOrg(userId: String, organisationId: String)(config: UserDeletionCleanupConfig, cassandraUtil: CassandraUtil): Unit = {
    val updateUserQuery = QueryBuilder.update(config.userKeyspace, config.userOrgTable)
      .`with`(QueryBuilder.set(config.IS_DELETED, true))
      .and(QueryBuilder.set(config.ORG_LEFT_DATE, getDateFormatter.format(new Date)))
      .where(QueryBuilder.eq(config.USERID, userId))
      .and(QueryBuilder.eq(config.ORGID, organisationId))
    logger.info(s"query: $updateUserQuery")
    cassandraUtil.upsert(updateUserQuery.toString)
  }

  def getDateFormatter: SimpleDateFormat = {
    val simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss:SSSZ")
    simpleDateFormat.setLenient(false)
    simpleDateFormat
  }


  def generateAuditEvent(data: Map[String, AnyRef], context: ProcessFunction[Event, Event]#Context)(config: UserDeletionCleanupConfig) = {
    val auditEvent = TelemetryEvent(
      actor = ActorObject(id = data.getOrElse("userId","").asInstanceOf[String]),
      edata = EventData(props = Array(data.getOrElse("props","").asInstanceOf[String]), `type` = "DeleteUserStatus", status="Delete"),
      context = EventContext(channel = data.getOrElse("channel","").asInstanceOf[String], cdata = Array(Map("type" -> "User", "id" -> data.getOrElse("userId","").asInstanceOf[String]).asJava)),
      `object` = EventObject(id = data.getOrElse("userId","").asInstanceOf[String], `type` = "User")
    )
    logger.info("audit event =>"+gson.toJson(auditEvent))
    context.output(config.auditEventOutputTag, gson.toJson(auditEvent))

  }


}

