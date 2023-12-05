package org.sunbird.job.userdelete.functions

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.model.{Filters, Updates}
import org.mongodb.scala.result.UpdateResult
import org.slf4j.LoggerFactory
import org.sunbird.job.userdelete.domain.Event
import org.sunbird.job.userdelete.task.UserDeleteConfig
import org.sunbird.job.util.{JSONUtil, MongoUtil}
import org.sunbird.job.{BaseProcessFunction, Metrics}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class UserDeleteFunction(config: UserDeleteConfig)(implicit val mapTypeInfo: TypeInformation[Event], @transient var mongoUtil: MongoUtil = null)
  extends BaseProcessFunction[Event, Event](config) {

  private[this] val logger = LoggerFactory.getLogger(classOf[UserDeleteFunction])

  override def metricsList(): List[String] = {
    List(config.userDeletionCleanupHit, config.skipCount, config.successCount, config.totalEventsCount)
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
    logger.info(s"Processing ml-delete cleanup event from user: ${event.userId}")
    metrics.incCounter(config.totalEventsCount)
    val userId: String = event.userId

    if (!userId.isBlank) {

      try {

        /**
         * Remove user related data from observation collection
         */
        val removeUserDataFromObservation = updateObservationData(userId)
        removeUserDataFromObservation.map(handleUpdateResult(userId, config.OBSERVATION_COLLECTION, _))

        /**
         * Remove user related data from surveySubmission collection
         */
        val removeUserDataFromSurveySubmission = updateSurveySubmissionData(userId)
        removeUserDataFromSurveySubmission.map(handleUpdateResult(userId, config.SURVEY_SUBMISSION_COLLECTION, _))

        /**
         * Remove user related data from observationSubmission collection
         */
        val removeUserDataFromObservationSubmission = updateObservationSubmissionData(userId)
        removeUserDataFromObservationSubmission.map(handleUpdateResult(userId, config.OBSERVATION_SUBMISSION_COLLECTION, _))

        /**
         * Remove user related data from projects collection
         */
        val removeUserDataFromProjects = updateProjectsData(userId)
        removeUserDataFromProjects.map(handleUpdateResult(userId, config.PROJECTS_COLLECTION, _))

        /**
         * Remove user related data from programUsers collection
         */
        val removeUserDataFromProgramsUsers = updateProgramUsersData(userId)
        removeUserDataFromProgramsUsers.map(handleUpdateResult(userId, config.PROGRAM_USERS_COLLECTION, _))

        metrics.incCounter(config.successCount)

      } catch {
        case ex: Exception =>
          logger.info("Event throwing exception: ", JSONUtil.serialize(event))
          throw ex

      }
    } else logger.info("UserID from the Event is empty")
      metrics.incCounter(config.skipCount)

  }

  def updateObservationData(userId: String): Future[UpdateResult] = {
    val filter: Bson = Filters.equal(config.CREATEDBY, userId)
    val result = mongoUtil.updateMany(config.OBSERVATION_COLLECTION, filter, updateUserData)
    result
  }

  def updateSurveySubmissionData(userId: String): Future[UpdateResult] = {
    val filter: Bson = Filters.equal(config.CREATEDBY, userId)
    val result = mongoUtil.updateMany(config.SURVEY_SUBMISSION_COLLECTION, filter, updateUserData)
    result
  }

  def updateObservationSubmissionData(userId: String): Future[UpdateResult] = {
    val filter: Bson = Filters.equal(config.CREATEDBY, userId)
    val result = mongoUtil.updateMany(config.OBSERVATION_SUBMISSION_COLLECTION, filter, updateUserData)
    result
  }

  def updateProjectsData(userId: String): Future[UpdateResult] = {
    val filter: Bson = Filters.equal(config.USERID, userId)
    val result = mongoUtil.updateMany(config.PROJECTS_COLLECTION, filter, updateUserData)
    result
  }

  def updateProgramUsersData(userId: String): Future[UpdateResult] = {
    val filter: Bson = Filters.equal(config.USERID, userId)
    val result = mongoUtil.updateMany(config.PROGRAM_USERS_COLLECTION, filter, updateUserData)
    result
  }

  def updateUserData: Bson = {
    val update: Bson = Updates.combine(
      Updates.set(config.FIRSTNAME, "Deleted User"),
      Updates.unset(config.LAST_NAME),
      Updates.unset(config.DOB),
      Updates.unset(config.EMAIL),
      Updates.unset(config.MASKED_EMAIL),
      Updates.unset(config.RECOVERY_EMAIL),
      Updates.unset(config.PREV_USED_EMAIL),
      Updates.unset(config.PHONE),
      Updates.unset(config.MASKED_PHONE),
      Updates.unset(config.RECOVERY_PHONE),
      Updates.unset(config.PREV_USED_PHONE)
    )
    update
  }

  def handleUpdateResult(userId: String, collection: String, result: UpdateResult): Unit = {
    if (result != null && result.getMatchedCount > 0) {
      logger.info(s"Fetched ${result.getMatchedCount} documents for the UserID $userId in $collection collection, And modified ${result.getModifiedCount} documents.")
    } else {
      logger.info(s"UserId $userId is not present in the $collection collection")
    }
  }

}