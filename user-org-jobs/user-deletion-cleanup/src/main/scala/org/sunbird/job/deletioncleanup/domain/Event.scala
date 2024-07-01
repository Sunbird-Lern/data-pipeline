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

  def isValid(responseUserId:String,responseOrgId:String): Boolean = {
    userId.nonEmpty && validateUser(userId,organisation,responseUserId,responseOrgId)
  }

  def validateUser(userId: String, organisation: String, responseUserId:String
                   ,responseOrgId:String): Boolean = {
    if(userId.nonEmpty) {
      responseUserId.equalsIgnoreCase(userId) && responseOrgId.equalsIgnoreCase(organisation)
      } else false
  }
}
