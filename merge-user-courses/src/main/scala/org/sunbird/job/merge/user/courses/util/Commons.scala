package org.sunbird.job.merge.user.courses.util

import com.datastax.driver.core.{ColumnDefinitions, Row}
import com.datastax.driver.core.querybuilder.{QueryBuilder, Update}
import org.apache.commons.collections.MapUtils
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory
import org.sunbird.job.Metrics
import org.sunbird.job.merge.user.courses.task.MergeUserCoursesConfig
import org.sunbird.job.util.CassandraUtil

import java.util
import java.util.{Date, HashMap, List, Map, UUID}
import org.sunbird.job.merge.user.courses.domain._

import java.text.{ParseException, SimpleDateFormat}

class Commons (config: MergeUserCoursesConfig, @transient var cassandraUtil: CassandraUtil){
  private[this] val LOGGER = LoggerFactory.getLogger(classOf[Commons])

  private val DateFormatter = new SimpleDateFormat(config.COURSE_DATE_FORMAT);

  def readAsListOfMap(keyspace: String, table: String, keys: Map[String, AnyRef]): List[Map[String, AnyRef]] = {
    val selectQuery = QueryBuilder.select.all.from(keyspace, table).where
    convertKeyCase(keys).entrySet().forEach(key => {
      if (key.getValue.isInstanceOf[util.List[_]])
        selectQuery.and(QueryBuilder.in(key.getKey, key.getValue.asInstanceOf[util.List[_]]))
      else
        selectQuery.and(QueryBuilder.eq(key.getKey, key.getValue))
    })
    val records: List[Row] = cassandraUtil.find(selectQuery.toString)
    val response: List[Map[String, AnyRef]] = new util.ArrayList[Map[String, AnyRef]]
    records.forEach((row: Row) => {
      val rowMap: Map[String, AnyRef] = new util.HashMap[String, AnyRef]
      row.getColumnDefinitions.forEach((column: ColumnDefinitions.Definition) => rowMap.put(column.getName, row.getObject(column.getName)))
      response.add(rowMap)
    })
    response
  }

  def insertDbRecord(keyspace: String, table: String, keys: Map[String, AnyRef]) = {
    val insertQuery = QueryBuilder.insertInto(keyspace, table)
    convertKeyCase(keys).entrySet.forEach((entry: util.Map.Entry[String, AnyRef]) => insertQuery.value(entry.getKey, entry.getValue))
    cassandraUtil.upsert(insertQuery.toString)
  }

  private def convertKeyCase(properties: util.Map[String, AnyRef]) = {
    val keyLowerCaseMap = new util.HashMap[String, AnyRef]
    if (MapUtils.isNotEmpty(properties)) {
      properties.entrySet.stream()
        .filter(e => e != null && e.getKey != null)
        .forEach((entry: util.Map.Entry[String, AnyRef]) => {
          keyLowerCaseMap.put(entry.getKey.toLowerCase, entry.getValue)
        })
    }
    keyLowerCaseMap
  }

  def updateQueryByFields(keyspace: String, table: String, keysToUpdate: Map[String, AnyRef], keysToSelect: Map[String, AnyRef]): Update.Where = {
    val updateQuery = QueryBuilder.update(keyspace, table).where
    keysToUpdate.entrySet.forEach((entry: util.Map.Entry[String, AnyRef]) => updateQuery.`with`(QueryBuilder.set(entry.getKey, entry.getValue)))
    keysToSelect.entrySet.forEach((entry: util.Map.Entry[String, AnyRef]) => {
      if (entry.getValue.isInstanceOf[util.List[_]])
        updateQuery.and(QueryBuilder.in(entry.getKey, entry.getValue.asInstanceOf[util.List[_]]))
      else updateQuery.and(QueryBuilder.eq(entry.getKey, entry.getValue))
    })
    updateQuery
  }

  private def getUpdatedValue(dataType: String, operation: String, fieldName: String, oldRecord: util.Map[String, AnyRef], newRecord: util.Map[String, AnyRef]): AnyRef = {
    if (null == oldRecord.get(fieldName)) return newRecord.get(fieldName)
    if (null == newRecord.get(fieldName)) return oldRecord.get(fieldName)
    dataType match {
      case "Integer" =>
        if (oldRecord.get(fieldName).isInstanceOf[Integer] && newRecord.get(fieldName).isInstanceOf[Integer]) {
          val val1 = oldRecord.get(fieldName).asInstanceOf[java.lang.Integer]
          val val2 = newRecord.get(fieldName).asInstanceOf[java.lang.Integer]
          if (StringUtils.equalsIgnoreCase("Sum", operation)) return (val1 + val2).asInstanceOf[AnyRef]
          else if (StringUtils.equalsIgnoreCase("Max", operation)) return if (val1 > val2) val1 else val2
        }

      case "DateString" =>
        if (oldRecord.get(fieldName).isInstanceOf[String] && newRecord.get(fieldName).isInstanceOf[String]) {
          val dateStr1 = oldRecord.get(fieldName).asInstanceOf[String]
          val dateStr2 = newRecord.get(fieldName).asInstanceOf[String]
          var date1:Date = null
          var date2:Date = null
          try date1 = DateFormatter.parse(dateStr1)
          catch {
            case pe: ParseException =>
              LOGGER.info("MergeUserCourses :getUpdatedValue: Date Parsing failed for field:" + fieldName + " value:" + dateStr1)
              return dateStr2
          }
          try date2 = DateFormatter.parse(dateStr2)
          catch {
            case pe: ParseException =>
              LOGGER.info("MergeUserCourses :getUpdatedValue: Date Parsing failed for field:" + fieldName + " value:" + dateStr2)
              return dateStr1
          }
          if (StringUtils.equalsIgnoreCase("Max", operation)) if (date1.after(date2)) return dateStr1
          else return dateStr2
        }

      case "Date" =>
        if (oldRecord.get(fieldName).isInstanceOf[Date] && newRecord.get(fieldName).isInstanceOf[Date]) {
          val date1 = oldRecord.get(fieldName).asInstanceOf[Date]
          val date2 = newRecord.get(fieldName).asInstanceOf[Date]
          if (StringUtils.equalsIgnoreCase("Max", operation)) if (date1.after(date2)) return date1 else return date2
        }
    }
    newRecord.get(fieldName)
  }

  def getBatchEnrollmentSyncEvent(model: BatchEnrollmentSyncModel) = new util.HashMap[String, AnyRef]() {
    new util.HashMap[String, AnyRef]() {
      put("actor", new HashMap[String, AnyRef]() {
        put("id", "Course Batch Updater")
        put("type", "System")
      })
      put("eid", "BE_JOB_REQUEST")
      put("edata", new HashMap[String, AnyRef]() {
        put("action", "batch-enrolment-sync")
        put("iteration", 1.asInstanceOf[java.lang.Integer])
        put("batchId", model.batchId)
        put("userId", model.userId)
        put("courseId", model.courseId)
        put("reset", util.Arrays.asList("completionPercentage", "status", "progress"))
      })
      put("ets", System.currentTimeMillis.asInstanceOf[java.lang.Long])
      put("context", new HashMap[String, AnyRef]() {
        put("pdata", new util.HashMap[String, AnyRef]() {
          put("ver", "1.0")
          put("id", "org.sunbird.platform")
        })
      })
      put("mid", "LP." + System.currentTimeMillis + "." + UUID.randomUUID)
      put("object", new HashMap[String, AnyRef]() {
        put("id", model.batchId + "_" + model.userId)
        put("type", "CourseBatchEnrolment")
      })
    }

  }

  def mergeContentConsumptionRecord(oldRecord: util.Map[String, AnyRef], newRecord: util.Map[String, AnyRef]): Unit = {
    newRecord.put(config.status, getUpdatedValue("Integer", "Max", config.status, oldRecord, newRecord))
    newRecord.put(config.progress, getUpdatedValue("Integer", "Max", config.progress, oldRecord, newRecord))
    newRecord.put(config.viewCount, getUpdatedValue("Integer", "Sum", config.viewCount, oldRecord, newRecord))
    newRecord.put(config.completedCount, getUpdatedValue("Integer", "Sum", config.completedCount, oldRecord, newRecord))
    newRecord.put(config.dateTime, getUpdatedValue("Date", "Max", config.dateTime, oldRecord, newRecord))
    newRecord.put(config.lastAccessTime, getUpdatedValue("DateString", "Max", config.lastAccessTime, oldRecord, newRecord))
    newRecord.put(config.lastCompletedTime, getUpdatedValue("DateString", "Max", config.lastCompletedTime, oldRecord, newRecord))
    newRecord.put(config.lastUpdatedTime, getUpdatedValue("DateString", "Max", config.lastUpdatedTime, oldRecord, newRecord))
    newRecord.put(config.last_access_time, getUpdatedValue("Date", "Max", config.last_access_time, oldRecord, newRecord))
    newRecord.put(config.last_completed_time, getUpdatedValue("Date", "Max", config.last_completed_time, oldRecord, newRecord))
    newRecord.put(config.last_updated_time, getUpdatedValue("Date", "Max", config.last_updated_time, oldRecord, newRecord))
  }

  def getMatchingRecord(contentConsumption: Map[String, AnyRef], toContentConsumptionList: List[Map[String, AnyRef]]): Map[String, AnyRef] = {
    var matchingRecord: util.HashMap[String, AnyRef] = new util.HashMap()
    toContentConsumptionList.forEach(toContentConsumption => {
      if (StringUtils.equalsIgnoreCase(contentConsumption.get(config.contentId).asInstanceOf[String], toContentConsumption.get(config.contentId).asInstanceOf[String])
        && StringUtils.equalsIgnoreCase(contentConsumption.get(config.batchId).asInstanceOf[String], toContentConsumption.get(config.batchId).asInstanceOf[String])
        && StringUtils.equalsIgnoreCase(contentConsumption.get(config.courseId).asInstanceOf[String], toContentConsumption.get(config.courseId).asInstanceOf[String])) {
        matchingRecord = toContentConsumption.asInstanceOf[util.HashMap[String, AnyRef]]
      }
    })
    matchingRecord
  }


}
