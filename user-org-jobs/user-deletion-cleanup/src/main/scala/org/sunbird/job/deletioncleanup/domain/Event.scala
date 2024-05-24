package org.sunbird.job.deletioncleanup.domain

import com.google.gson.Gson
import org.sunbird.job.domain.reader.{Event => BaseEvent}

import java.util
import scala.collection.JavaConverters._

class Event(eventMap: util.Map[String, Any]) extends BaseEvent(eventMap) {

  override def kafkaKey(): String = {
    did()
  }

  def userId: String = {
    telemetry.read[String]("edata.userId").orNull
  }

  def organisation: String = {
    telemetry.read[String]("edata.organisationId").orNull
  }

  def context: String = {
    val gson = new Gson()
    telemetry.read[Any]("context") match {
      case Some(scalaMap: scala.collection.immutable.Map[_, _]) =>
        gson.toJson(scalaMap.asJava)
      case Some(javaMap: java.util.Map[_, _]) =>
        gson.toJson(javaMap)
      case _ => null
    }
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
