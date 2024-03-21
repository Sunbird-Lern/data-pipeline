package org.sunbird.job.ownershiptransfer.domain

import org.sunbird.job.Metrics
import org.sunbird.job.domain.reader.{Event => BaseEvent}
import org.sunbird.job.ownershiptransfer.task.UserOwnershipTransferConfig
import org.sunbird.job.util.{HttpUtil, JSONUtil}

import java.util
import scala.collection.convert.ImplicitConversions.`map AsScala`

class Event(eventMap: util.Map[String, Any]) extends BaseEvent(eventMap) {

  var fromUserId: String = _
  var toUserId: String = _
  var organisation: String = _

  fromUserId = telemetry.read[String]("edata.fromUserId").orNull
  toUserId = telemetry.read[String]("edata.toUserId").orNull
  organisation = telemetry.read[String]("edata.organisationId").orNull

  override def kafkaKey(): String = {
    did()
  }

  def isValid(metrics: Metrics, config: UserOwnershipTransferConfig, httpUtil: HttpUtil): Boolean = {
    fromUserId != null && toUserId != null &&
      validateUser(fromUserId, organisation)(metrics, config, httpUtil) &&
      validateUser(toUserId, organisation)(metrics, config, httpUtil)
  }

  def validateUser(userId: String, organisation: String)(metrics: Metrics, config: UserOwnershipTransferConfig, httpUtil: HttpUtil): Boolean = {
    if (userId != null) {
      val url = config.userOrgServiceBasePath + config.userReadApi + "/" + userId + "?identifier,rootOrgId"
      val userReadResp = httpUtil.get(url)
      if (userReadResp.status == 200) {
        metrics.incCounter(config.apiReadSuccessCount)
        val response = JSONUtil.deserialize[Map[String, AnyRef]](userReadResp.body)
        val userDetails = response.getOrElse("result", Map.empty[String, AnyRef]).asInstanceOf[Map[String, AnyRef]].getOrElse("response", Map.empty[String, AnyRef]).asInstanceOf[Map[String, AnyRef]]
        userDetails.getOrElse("identifier", "").asInstanceOf[String].equalsIgnoreCase(userId) &&
          userDetails.getOrElse("rootOrgId", "").asInstanceOf[String].equalsIgnoreCase(organisation)
      } else false
    } else false
  }
}