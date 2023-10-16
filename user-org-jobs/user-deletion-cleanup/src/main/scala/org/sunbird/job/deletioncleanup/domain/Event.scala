package org.sunbird.job.deletioncleanup.domain

import org.sunbird.dp.core.domain.Events
import org.sunbird.dp.core.job.Metrics
import org.sunbird.dp.core.util.{HttpUtil, JSONUtil}
import org.sunbird.job.deletioncleanup.task.UserDeletionCleanupConfig

import java.util
import scala.collection.convert.ImplicitConversions.`map AsScala`

class Event(eventMap: util.Map[String, Any]) extends Events(eventMap) {

  override def kafkaKey(): String = {
    did()
  }

  def userId: String = {
    telemetry.read[String]("edata.userId").orNull
  }

  def organisation: String = {
    telemetry.read[String]("edata.organisationId").orNull
  }

  def suggestedUsers: util.ArrayList[util.Map[String, AnyRef]] = {
    telemetry.read[util.ArrayList[util.Map[String, AnyRef]]]("edata.suggested_users").orNull
  }

  def managedUsers: util.ArrayList[String] = {
    telemetry.read[util.ArrayList[String]]("edata.managed_users").orNull
  }

  def isValid()(metrics: Metrics, config:UserDeletionCleanupConfig, httpUtil: HttpUtil): Boolean = {
    userId.nonEmpty && validateUser(userId, organisation)(metrics, config, httpUtil)
  }

  def validateUser(userId: String, organisation: String)(metrics: Metrics, config:UserDeletionCleanupConfig, httpUtil: HttpUtil): Boolean = {
    if(userId.nonEmpty) {
      val url = config.userOrgServiceBasePath + config.userReadApi + "/" + userId + "?identifier,rootOrgId"
      val userReadResp = httpUtil.get(url)

      if (200 == userReadResp.status) {
        metrics.incCounter(config.apiReadSuccessCount)
        val response = JSONUtil.deserialize[util.HashMap[String, AnyRef]](userReadResp.body)
        val userDetails = response.getOrElse("result", new util.HashMap[String, AnyRef]()).asInstanceOf[util.HashMap[String, AnyRef]].getOrElse("response", new util.HashMap[String, AnyRef]()).asInstanceOf[util.HashMap[String, AnyRef]]
        userDetails.getOrElse("identifier", "").asInstanceOf[String].equalsIgnoreCase(userId) && userDetails.getOrElse("rootOrgId","").asInstanceOf[String].equalsIgnoreCase(organisation)
      } else false
    } else false
  }
}
