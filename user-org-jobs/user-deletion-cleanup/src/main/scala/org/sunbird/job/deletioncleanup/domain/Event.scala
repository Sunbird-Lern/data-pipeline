package org.sunbird.job.deletioncleanup.domain

import org.sunbird.dp.core.domain.Events

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

  def isValid(userDetails: util.HashMap[String, AnyRef]): Boolean = {
    userId.nonEmpty && validateUser(userDetails, userId, organisation)
  }

  def validateUser(userDetails: util.HashMap[String, AnyRef], userId: String, organisation: String): Boolean = {
    if(userId.nonEmpty) {
        userDetails.getOrElse("identifier", "").asInstanceOf[String].equalsIgnoreCase(userId) && userDetails.getOrElse("rootOrgId","").asInstanceOf[String].equalsIgnoreCase(organisation)
      } else false
  }
}
