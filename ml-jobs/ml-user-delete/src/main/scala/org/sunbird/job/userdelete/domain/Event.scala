package org.sunbird.job.userdelete.domain

import org.sunbird.job.domain.reader.JobRequest

class Event(eventMap: java.util.Map[String, Any], partition: Int, offset: Long) extends JobRequest(eventMap, partition, offset) {

  def userId: String = readOrDefault[String]("edata.userId", "")

}
