package org.sunbird.job.transferownership.functions

import com.twitter.util.Config.intoList
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.bson.{BsonDocument, BsonObjectId}
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.bson.{BsonArray, ObjectId}
import org.mongodb.scala.model.Filters.equal
import org.mongodb.scala.model.Updates.addToSet
import org.mongodb.scala.model.{Filters, Updates}
import org.mongodb.scala.{Document, bson}
import org.slf4j.LoggerFactory
import org.sunbird.job.transferownership.domain.Event
import org.sunbird.job.transferownership.task.TransferOwnershipConfig
import org.sunbird.job.util.MongoUtil
import org.sunbird.job.{BaseProcessFunction, Metrics}

import scala.collection.JavaConverters._
import scala.collection.immutable._
import scala.collection.mutable


class TransferOwnershipFunction(config: TransferOwnershipConfig)(implicit val mapTypeInfo: TypeInformation[Event], @transient var mongoUtil: MongoUtil = null)
  extends BaseProcessFunction[Event, Event](config) {

  private[this] val logger = LoggerFactory.getLogger(classOf[TransferOwnershipFunction])

  override def metricsList(): List[String] = {
    List(config.transferOwnershipCleanupHit, config.skipCount, config.successCount, config.totalEventsCount)
  }

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    mongoUtil = new MongoUtil(config.dbHost, config.dbPort, config.dataBase)
  }

  override def close(): Unit = {
    mongoUtil.close()
    super.close()
  }

  override def processElement(event: Event, context: ProcessFunction[Event, Event]#Context, metrics: Metrics): Unit = {

    logger.info(s"Processing ml-transfer-ownership event triggered by the UserId: ${event.eventTriggeredBy}")
    metrics.incCounter(config.totalEventsCount)

    if (event.validateAssetInformation() == true) {

      if (event.assetInformationType == "solution") {

        /**
         * Update only one Solutions Collection document for 1-to-1 solution asset transfer
         */
        val oneToOneSolutionsFilter: Bson = Filters.and(Filters.equal(config.AUTHOR, event.fromUserId), Filters.equal(config.ID, new ObjectId(event.assetInformationId)))
        logger.info(s"Transferring one Solution Asset with Id ${event.assetInformationId} to To-User with Id ${event.toUserId} in Solutions collections")
        updateSolutionsCollection(oneToOneSolutionsFilter)
      } else if (event.assetInformationType == "program") {

        /**
         * Update only one Programs Collection document for 1-to-1 program asset transfer
         */
        val oneToOneProgramFilter: Bson = Filters.and(Filters.equal(config.OWNER, event.fromUserId), Filters.equal(config.ID, new ObjectId(event.assetInformationId)))
        logger.info(s"Transferring one Program Asset with Id ${event.assetInformationId} to To-User with Id ${event.toUserId} in Programs collections")
        updateProgramsCollection(oneToOneProgramFilter)

        /**
         * Update User Extension Collection documents for 1-to-1 program asset transfer
         */
        val fromUserFilter: Bson = Filters.equal(config.USERID, event.fromUserId)
        val toUserFilter: Bson = Filters.equal(config.USERID, event.toUserId)
        val toUserExtensionData = getUserExtensionData(toUserFilter)
        if (toUserExtensionData.isEmpty) {
          logger.info("To-User Data is not present in userExtension Collection, So inserting new Document into userExtension")
          insertToUserDataInUserExtensionCollection(toUserFilter)
          matchToUserPlatformRoles(toUserFilter)
          oneToOneAssetTransfer(fromUserFilter)
        } else {
          logger.info("To-User Data is present in userExtension Collection")
          matchToUserPlatformRoles(toUserFilter)
          oneToOneAssetTransfer(fromUserFilter)
        }
      }
    } else {

      /**
       * Update all solutionsCollection documents when author Id is equal to fromUserId
       */
      val solutionsFilter: Bson = Filters.equal(config.AUTHOR, event.fromUserId)
      logger.info(s"Transferring all Solutions Assets to To-User with Id ${event.toUserId} in Solutions collections")
      updateSolutionsCollection(solutionsFilter)

      /**
       * Update all programsCollection documents when owner Id is equal to fromUserId
       */
      val programsFilter: Bson = Filters.equal(config.OWNER, event.fromUserId)
      logger.info(s"Transferring all Programs Assets to To-User with Id ${event.toUserId} in Programs collections")
      updateProgramsCollection(programsFilter)

      /**
       * Update User Extension Collection documents for all in one programs asset transfer
       */
      val fromUserFilter: Bson = Filters.equal(config.USERID, event.fromUserId)
      val toUserFilter: Bson = Filters.equal(config.USERID, event.toUserId)
      val toUserExtensionData = getUserExtensionData(toUserFilter)
      if (toUserExtensionData.isEmpty) {
        logger.info("To-User Data is not present in userExtension Collection, So inserting new Document into userExtension for all in one transfer")
        insertToUserDataInOneGo(fromUserFilter)
      } else {
        logger.info("Performing all in one asset transfer when To-User Data is present in userExtension Collection")
        matchToUserPlatformRoles(toUserFilter)
        appendingPlatformRoles(fromUserFilter, toUserFilter)
      }
    }

    def updateSolutionsCollection(filters: Bson) = {
      mongoUtil.updateMany(config.SOLUTIONS_COLLECTION, filters, updateSolutionsData)
    }

    def updateProgramsCollection(filters: Bson) = {
      mongoUtil.updateMany(config.PROGRAMS_COLLECTION, filters, updateProgramsData)
    }

    def getUserExtensionData(filters: Bson) = {
      mongoUtil.find(config.USER_EXTENSION, filters)
    }

    def insertToUserDataInUserExtensionCollection(filters: Bson) = {
      mongoUtil.insertOne(config.USER_EXTENSION, insertToUserData)
    }

    def updateSolutionsData: Bson = {
      val update: Bson = Updates.combine(
        Updates.set(config.AUTHOR, event.toUserId),
        Updates.set(config.CREATOR, event.toUserFirstName),
        Updates.set(config.LICENSE_AUTHOR, event.toUserFirstName),
        Updates.set(config.LICENSE_CREATOR, event.toUserFirstName)
      )
      update
    }

    def updateProgramsData: Bson = {
      Updates.set(config.OWNER, event.toUserId)
    }

    def insertToUserData: Document = {
      val devices: BsonArray = new BsonArray()
      val platformRoles: BsonArray = new BsonArray()
      val requiredData = Document(
        config.USERID -> event.toUserId,
        config.EXTERNAL_ID -> event.toUserName,
        config.STATUS -> "active",
        config.IS_DELETED -> false,
        config.DEVICES -> devices,
        config.PLATFORM_ROLES -> platformRoles,
        config.CREATED_BY -> event.eventTriggeredBy,
        config.UPDATED_BY -> event.eventTriggeredBy
      )
      requiredData
    }

    def matchToUserPlatformRoles(filters: Bson) = {
      val toUserRolesData = mongoUtil.find(config.USER_EXTENSION, filters)
      val toUserPlatformRoles = if (toUserRolesData.iterator().hasNext) toUserRolesData.iterator().next().get("platformRoles") else None
      val toUserRoleCodeList: List[String] = toUserPlatformRoles.map {
        case array: BsonArray =>
          array.asScala.flatMap(_.asDocument().getString("code").map(_.getValue)).toList
        case _ => List.empty[String]
      }.getOrElse(List.empty[String])

      val missingRoles = event.toUserRoles.diff(toUserRoleCodeList)
      missingRoles.foreach { role =>
        val roleFilter = Filters.equal(config.ROLES_CODE, role)
        val rolesIds = mongoUtil.find(config.USER_ROLES, roleFilter)
        rolesIds.forEach { document =>
          val id = document.get("_id").map(_.asObjectId().getValue.toString).getOrElse("")
          val addNewRoleElement = Document("roleId" -> new ObjectId(id), "code" -> role, "programs" -> new BsonArray())
          logger.info(s"Updating the missing ${role} platformRoles for To-User")
          mongoUtil.updateOne(config.USER_EXTENSION, filters, Updates.push("platformRoles", addNewRoleElement))
        }
      }
    }

    def oneToOneAssetTransfer(fromUserFilter: Bson) = {
      val fromUserExtensionData = getUserExtensionData(fromUserFilter)
      val fromUserPlatformRoles = if (fromUserExtensionData.iterator().hasNext) fromUserExtensionData.iterator().next().get("platformRoles") else None
      val toUserRolesToUpdate: List[String] = fromUserPlatformRoles.map {
        case bsonArray: BsonArray =>
          bsonArray.getValues.asScala.collect {
            case doc: BsonDocument if doc.containsKey("programs") && doc.getArray("programs").contains(new BsonObjectId(new ObjectId(event.assetInformationId))) =>
              doc.getString("code").getValue
          }.toList
      }.getOrElse {
        logger.info("From User platformRoles is empty")
        List.empty[String]
      }

      val toUserUpdateFilter = Filters.and(
        Filters.equal(config.USERID, event.toUserId),
        Filters.elemMatch("platformRoles", Filters.exists("programs")),
        Filters.in("platformRoles.code", toUserRolesToUpdate: _*)
      )
      val updateToUserDocument = addToSet("platformRoles.$[].programs", new ObjectId(event.assetInformationId))
      //TODO : replace logger.info
      logger.info("Transferring Programs with Id " + event.assetInformationId + " to User Id " + event.toUserId)
      mongoUtil.updateOne(config.USER_EXTENSION, toUserUpdateFilter, updateToUserDocument)
      val updateFromUserDocument = Updates.pull("platformRoles.$[].programs", new ObjectId(event.assetInformationId))
      //TODO : replace logger.info
      logger.info("Removing Programs with Id " + event.assetInformationId + " from User Id " + event.fromUserId)
      mongoUtil.updateOne(config.USER_EXTENSION, fromUserFilter, updateFromUserDocument)
    }

    def insertToUserDataInOneGo(fromUserFilter: Bson) {
      val toUserRolesData = mongoUtil.find(config.USER_EXTENSION, fromUserFilter)
      val toUserPlatformRoles = if (toUserRolesData.iterator().hasNext) toUserRolesData.iterator().next().get("platformRoles") else None
      val devices: BsonArray = new BsonArray()
      val platformRoles: BsonArray = toUserPlatformRoles match {
        case Some(value) => value.asArray()
      }
      val requiredData = Document(config.USERID -> event.toUserId,
        config.EXTERNAL_ID -> event.toUserName,
        config.STATUS -> "active",
        config.IS_DELETED -> false,
        config.DEVICES -> devices,
        config.PLATFORM_ROLES -> platformRoles,
        config.CREATED_BY -> event.eventTriggeredBy,
        config.UPDATED_BY -> event.eventTriggeredBy)
      logger.info("Transferring all Programs to new User with Id " + event.toUserId + " from Deleted User " + event.fromUserId)
      mongoUtil.insertOne(config.USER_EXTENSION, requiredData)
      val updateFromUserDocument = Updates.unset("platformRoles")
      logger.info("Removing platformRoles from Deleted User " + event.fromUserId)
      mongoUtil.updateOne(config.USER_EXTENSION, fromUserFilter, updateFromUserDocument)
    }

    def appendingPlatformRoles(fromUserFilter: Bson, toUserFilter: Bson) = {
      val fromUserExtensionData = getUserExtensionData(fromUserFilter)
      val fromUserPlatformRoles = if (fromUserExtensionData.iterator().hasNext) fromUserExtensionData.iterator().next().get("platformRoles") else None
      val toUserExtensionData = getUserExtensionData(toUserFilter)
      val toUserPlatformRoles = if (toUserExtensionData.iterator().hasNext) toUserExtensionData.iterator().next().get("platformRoles") else None
      val fromUserProgramIdsByCode = getUsersProgramIdsWithCode(fromUserPlatformRoles)
      val toUserProgramIdsByCode = getUsersProgramIdsWithCode(toUserPlatformRoles)

      val missingProgramIdsByCode = collection.mutable.Map[String, List[String]]()
      fromUserProgramIdsByCode.foreach { case (fromCode, fromProgramIds) =>
        toUserProgramIdsByCode.get(fromCode) match {
          case Some(toProgramIds) =>
            val missingProgramIds = fromProgramIds.filterNot(toProgramIds.toSet)
            if (missingProgramIds.nonEmpty) {
              missingProgramIdsByCode += (fromCode -> missingProgramIds)
            }
          case None =>
            logger.info("Missing a roles in To-User data: matchToUserPlatformRoles method did not execute as expected")
        }
      }
      logger.info("Transferring all Programs to new User with Id " + event.toUserId + " from Deleted User " + event.fromUserId)
      missingProgramIdsByCode.foreach { case (code, programIds) =>
        val filter = Filters.and(
          equal(config.USERID, event.toUserId),
          equal("platformRoles.code", code)
        )
        val bsonProgramIds = programIds.map(new ObjectId(_))
        val update = Updates.pushEach("platformRoles.$.programs", bsonProgramIds: _*)
        mongoUtil.updateMany(config.USER_EXTENSION, filter, update)
      }
      val updateFromUserDocument = Updates.unset("platformRoles")
      logger.info("Removing platformRoles from Deleted User " + event.fromUserId)
      mongoUtil.updateOne(config.USER_EXTENSION, fromUserFilter, updateFromUserDocument)
    }

    def getUsersProgramIdsWithCode(platformRolesKey: Option[bson.BsonValue]): mutable.Map[String, List[String]] = {
      val programIdsByCode = mutable.Map[String, List[String]]()
      platformRolesKey match {
        case Some(bsonArray: bson.BsonArray) =>
          bsonArray.forEach {
            case bsonDocument: bson.BsonDocument =>
              val code = bsonDocument.getString("code").getValue
              val programs = bsonDocument.getArray("programs")
              val programIds = programs.getValues.asScala.map(_.asObjectId().getValue.toString).toList
              programIdsByCode += (code -> programIds)
            case _ =>
              logger.info("Key platformRoles is not in expected type")
          }
        case None =>
          logger.info("Error when matching program Ids with role code: platformRoles Key is not present")
      }
      programIdsByCode
    }

  }

}