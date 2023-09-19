package org.sunbird.job.ownershiptransfer.domain

import org.sunbird.dp.core.domain.Events
import org.sunbird.dp.core.job.Metrics
import org.sunbird.dp.core.util.{HttpUtil, JSONUtil}
import org.sunbird.job.ownershiptransfer.task.UserOwnershipTransferConfig

import java.util
import scala.collection.convert.ImplicitConversions.`map AsScala`

class Event(eventMap: util.Map[String, Any]) extends Events(eventMap) {

  override def kafkaKey(): String = {
    did()
  }

  def getId: String = {
    Option(objectType()).map({ t => if (t.equalsIgnoreCase("User")) objectID() else null
    }).orNull
  }

  def fromUserId: String = {
    telemetry.read[String]("edata.fromUserId").orNull
  }

  def toUserId: String = {
    telemetry.read[String]("edata.toUserId").orNull
  }

  def organisation: String = {
    telemetry.read[String]("edata.organisationId").orNull
  }

  def assets: util.ArrayList[String] = {
    telemetry.read[util.ArrayList[String]]("edata.asset").orNull
  }

  def isValid()(metrics: Metrics, config:UserOwnershipTransferConfig, httpUtil: HttpUtil): Boolean = {
    fromUserId.nonEmpty && toUserId.nonEmpty && validateUser(fromUserId, organisation)(metrics, config, httpUtil) && validateUser(toUserId, organisation)(metrics, config, httpUtil)
  }

  def validateUser(userId: String, organisation: String)(metrics: Metrics, config:UserOwnershipTransferConfig, httpUtil: HttpUtil): Boolean = {
    if(userId.nonEmpty) {
      val url = config.userOrgServiceBasePath + config.userReadApi + "/" + userId + "?organisations,roles"
      val userReadResp = httpUtil.get(url)

      if (200 == userReadResp.status) {
        metrics.incCounter(config.apiReadSuccessCount)
        val response = JSONUtil.deserialize[util.HashMap[String, AnyRef]](userReadResp.body)
        val userDetails = response.getOrElse("result", Map[String, AnyRef]()).asInstanceOf[Map[String, AnyRef]].getOrElse("response", Map[String, AnyRef]()).asInstanceOf[Map[String, AnyRef]]
        userDetails.getOrElse("identifier", "").asInstanceOf[String].equalsIgnoreCase(userId) && userDetails.getOrElse("rootOrgId","").asInstanceOf[String].equalsIgnoreCase(organisation)
      } else false
    } else false
  }
}
