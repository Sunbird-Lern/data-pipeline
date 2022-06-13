package org.sunbird.job.merge.user.courses.util

import org.apache.commons.collections.{CollectionUtils, MapUtils}
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.slf4j.LoggerFactory
import org.sunbird.job.Metrics
import org.sunbird.job.merge.user.courses.domain.{BatchEnrollmentSyncModel, Event}
import org.sunbird.job.merge.user.courses.task.MergeUserCoursesConfig
import org.sunbird.job.util.{CassandraUtil, ScalaJsonUtil}

import java.util
import java.util.{Date, HashMap, List, Map}
import scala.collection.JavaConverters._


class MergeOperations(config: MergeUserCoursesConfig, @transient var cassandraUtil: CassandraUtil) {
  private[this] val logger = LoggerFactory.getLogger(classOf[MergeOperations])
  private val commons = new Commons(config, cassandraUtil)

  @throws[Exception]
  def generateBatchEnrollmentSyncEvents(userId: String, context: ProcessFunction[Event, String]#Context)(implicit metrics: Metrics): Unit = {
    val objects = getBatchDetailsOfUser(userId)
    if (CollectionUtils.isNotEmpty(objects)) {
      objects.forEach(model => {
        val event = ScalaJsonUtil.serialize(commons.getBatchEnrollmentSyncEvent(model))
        logger.info("MergeCourseBatch: Course batch updated successfully")
        metrics.incCounter(config.dbUpdateCount)
        context.output(config.eventOutputTag, event)
      })
    }
  }

  @throws[Exception]
  def mergeEnrolments(fromUserId: String, toUserId: String): Unit = {
    val fromBatches = getBatchDetailsOfUser(fromUserId)
    val toBatches = getBatchDetailsOfUser(toUserId)
    val fromBatchIds = new util.HashMap[String, BatchEnrollmentSyncModel]
    val toBatchIds = new util.HashMap[String, BatchEnrollmentSyncModel]

    fromBatches.forEach(b => fromBatchIds.put(b.batchId, b))
    toBatches.forEach(b => toBatchIds.put(b.batchId, b))

    val batchIdsToBeMigrated = CollectionUtils.subtract(fromBatchIds.keySet(), toBatchIds.keySet()).asInstanceOf[util.List[String]]
    System.out.println("batchIdsToBeMigrated =>"+batchIdsToBeMigrated)
    //Migrate batch records in Cassandra
    batchIdsToBeMigrated.forEach(batchId => {
      val courseId = fromBatchIds.get(batchId).courseId
      val userCourse = getUserCourse(batchId, fromUserId, courseId)
      if (MapUtils.isNotEmpty(userCourse)) {
        userCourse.put(config.userId, toUserId)
        logger.info("MergeUserCourses:mergeUserBatches: Merging batch:" + batchId + " updated record:" + userCourse)
        commons.insertDbRecord(config.dbKeyspace, config.userEnrolmentsTable, userCourse)
      }
      else logger.info("MergeUserCoursesS:mergeUserBatches: user_courses cs record with batchId:" + batchId + " userId:" + fromUserId + " not found in Cassandra")
    })
  }

  @throws[Exception]
  def mergeUserActivityAggregates(fromUserId: String, toUserId: String): Unit = {
    val fromBatches: util.List[BatchEnrollmentSyncModel] = getBatchDetailsOfUser(fromUserId)
    val toBatches: util.List[BatchEnrollmentSyncModel] = getBatchDetailsOfUser(toUserId)

    if (CollectionUtils.isNotEmpty(fromBatches)) {
      val fromCourseIds: util.List[String] = fromBatches.asScala.toStream.map((enrol: BatchEnrollmentSyncModel) => enrol.courseId).toList.asJava
      val toCourseIds: util.List[String] = toBatches.asScala.toStream.map((enrol: BatchEnrollmentSyncModel) => enrol.courseId).toList.asJava
      val key = new util.HashMap[String, AnyRef]
      key.put(config.activity_type, "Course")
      key.put(config.user_id, fromUserId)
      key.put(config.activity_id, fromCourseIds)
      val fromData = commons.readAsListOfMap(config.dbKeyspace, config.userActivityAggTable, key)
      key.put(config.activity_id, toCourseIds)
      val toData: util.List[util.Map[String, AnyRef]] = commons.readAsListOfMap(config.dbKeyspace, config.userActivityAggTable, key)

      val toDataMap: util.Map[String, AnyRef] = toData.asScala.toStream.map(m => (m.get("context_id"), m)).toMap.asInstanceOf[util.Map[String, AnyRef]]

      fromData.forEach(data => {
        data.put(config.user_id, toUserId)
        val fromAgg: Map[String, Double] = data.get("aggregates").asInstanceOf[Map[String, Double]]
        val toAgg: Map[String, Double] = (toDataMap.getOrDefault(data.get("context_id"), new util.HashMap[String, AnyRef]).asInstanceOf[Map[String, AnyRef]]).getOrDefault("aggregates", new HashMap[String, Double]).asInstanceOf[Map[String, Double]]
        data.put("aggregates", new util.HashMap[String, Double]() {
          put("completedCount", Math.max(fromAgg.getOrDefault("completedCount", 0), toAgg.getOrDefault("completedCount", 0)))
        })
        data.put("agg_last_updated", new util.HashMap[String, Date]() {
          put("completedCount", new Date)
        })
        val dataToSelect = new util.HashMap[String, AnyRef]() {
          put(config.activity_type, "Course")
          put(config.activity_id, data.get("activity_id"))
          put(config.user_id, toUserId)
          put("context_id", data.get("context_id"))
        }
        //TODO: cassandra batch update
        cassandraUtil.upsert(commons.updateQueryByFields(config.dbKeyspace, config.userActivityAggTable, data, dataToSelect).toString)
      })
    }
  }

  def mergeContentConsumption(fromUserId: String, toUserId: String): Unit = {
    val key = new util.HashMap[String, AnyRef]
    key.put(config.userId, fromUserId)
    val fromContentConsumptionList = commons.readAsListOfMap(config.dbKeyspace, config.contentConsumptionTable, key)
    key.put(config.userId, toUserId)
    val toContentConsumptionList = commons.readAsListOfMap(config.dbKeyspace, config.contentConsumptionTable, key)

    fromContentConsumptionList.forEach(contentConsumption => {
      var matchingRecord = commons.getMatchingRecord(contentConsumption, toContentConsumptionList)
      if (MapUtils.isEmpty(matchingRecord)) {
        matchingRecord = contentConsumption
        matchingRecord.put(config.userId, toUserId)
      }
      else commons.mergeContentConsumptionRecord(contentConsumption, matchingRecord)
      commons.insertDbRecord(config.dbKeyspace, config.contentConsumptionTable, matchingRecord)
    })
  }

  private def getUserCourse(batchId: String, userId: String, courseId: String): util.HashMap[String, AnyRef] = {
    val key = new util.HashMap[String, AnyRef]
    key.put(config.batchId, batchId)
    key.put(config.userId, userId)
    key.put(config.courseId, courseId)

    val response: List[Map[String, AnyRef]] = commons.readAsListOfMap(config.dbKeyspace, config.userEnrolmentsTable, key)
    if (response.size() >= 1) response.get(0).asInstanceOf[util.HashMap[String, AnyRef]] else new util.HashMap[String, AnyRef]()
  }

  @throws[Exception]
  private def getBatchDetailsOfUser(userId: String): util.List[BatchEnrollmentSyncModel] = {
    val objects = new util.ArrayList[BatchEnrollmentSyncModel]
    val searchQuery = new util.HashMap[String, AnyRef]
    val userIdList = new util.ArrayList[String]
    userIdList.add(userId)
    searchQuery.put(config.userId, userIdList)
    val key = new util.HashMap[String, AnyRef]
    key.put(config.userId, userIdList)
    val documents = commons.readAsListOfMap(config.dbKeyspace, config.userEnrolmentsTable, key)
    System.out.println("documents  "+documents)

    documents.forEach((doc: util.Map[String, AnyRef]) => {
      objects.add(BatchEnrollmentSyncModel(doc.get(config.batchId.toLowerCase()).toString, doc.get(config.userId.toLowerCase()).toString, doc.get(config.courseId.toLowerCase()).toString))
    })
    objects
  }

}
