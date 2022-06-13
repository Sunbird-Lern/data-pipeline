package org.sunbird.job.merge.user.courses.domain

import org.apache.commons.lang3.StringUtils
import org.sunbird.job.domain.reader.JobRequest

import java.util

class Event(eventMap: java.util.Map[String, Any], partition: Int, offset: Long) extends JobRequest(eventMap, partition, offset) {

  val jobName = "MergeUserCourses"

  def fromAccountId: String = readOrDefault[String]("edata.fromAccountId", "")
  def toAccountId: String = readOrDefault[String]("edata.toAccountId", "")
  def action: String = readOrDefault[String]("edata.action", "")
  def eData: Map[String, AnyRef] = readOrDefault("edata", new util.HashMap[String, AnyRef]()).asInstanceOf[Map[String, AnyRef]]


  def isValidEvent(): Boolean = {
    this.eData.nonEmpty && this.fromAccountId.nonEmpty && this.toAccountId.nonEmpty
  }

}