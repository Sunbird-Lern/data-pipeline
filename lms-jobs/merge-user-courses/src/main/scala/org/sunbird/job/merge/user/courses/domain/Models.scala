package org.sunbird.job.merge.user.courses.domain

class Models {}

case class BatchEnrollmentSyncModel(batchId: String, userId: String, courseId: String) {
  def this() = this("", "", "")
}


