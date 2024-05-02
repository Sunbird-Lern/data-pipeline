package org.sunbird.job.transferownership.domain

import org.sunbird.job.domain.reader.JobRequest

class Event(eventMap: java.util.Map[String, Any], partition: Int, offset: Long) extends JobRequest(eventMap, partition, offset) {

  def eventTriggeredBy: String = readOrDefault[String]("edata.actionBy.userId", "")

  def fromUserId: String = readOrDefault[String]("edata.fromUserProfile.userId", "")

  //def fromUserRoles: List[String] = readOrDefault[List[String]]("edata.fromUserProfile.roles", List.empty[String])

  def toUserId: String = readOrDefault[String]("edata.toUserProfile.userId", "")

  def toUserRoles: List[String] = readOrDefault[List[String]]("edata.toUserProfile.roles", List.empty[String])

  def toUserName: String = readOrDefault[String]("edata.toUserProfile.userName", "")

  def toUserFirstName: String = readOrDefault[String]("edata.toUserProfile.firstName", "")

  def assetInformationType: String = readOrDefault[String]("edata.assetInformation.objectType", "")

  def assetInformationId: String = readOrDefault[String]("edata.assetInformation.identifier", "")

  def validateAssetInformation():Boolean = {
    assetInformationType.nonEmpty && assetInformationId.nonEmpty
  }

}