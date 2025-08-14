package org.sunbird.job.deletioncleanup.domain

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import org.sunbird.job.domain.reader.{Event => BaseEvent}

import java.util

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
    val mapper = new ObjectMapper()
    mapper.registerModule(DefaultScalaModule)
    telemetry.read[Map[String, Any]]("context").map { contextMap =>
      mapper.writeValueAsString(contextMap)
    }.orNull
  }

  def suggestedUsers: util.ArrayList[util.Map[String, AnyRef]] = {
    telemetry.read[util.ArrayList[util.Map[String, AnyRef]]]("edata.suggested_users").orNull
  }

  def managedUsers: util.ArrayList[String] = {
    val raw = telemetry.read[Any]("edata.managed_users")
    raw match {
      case None => new util.ArrayList[String]()
      case Some(null) => new util.ArrayList[String]()
      case Some(s: scala.collection.Iterable[_]) =>
        if (s.isEmpty) new util.ArrayList[String]()
        else {
          val out = new util.ArrayList[String]()
          s.foreach { v =>
            if (v != null) out.add(String.valueOf(v))
          }
          out
        }
      case Some(l: util.List[_]) => new util.ArrayList[String](l.asInstanceOf[util.List[String]])
      case _ => new util.ArrayList[String]()
    }
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
