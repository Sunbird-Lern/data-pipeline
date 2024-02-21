package org.sunbird.job.userinfo.domain

import java.util
import org.sunbird.job.domain.reader.{Event => BaseEvent}

class Event(eventMap: util.Map[String, Any]) extends BaseEvent(eventMap) {

  def _id: String = {
    Option(telemetry.readOrDefault[String]("_id", null)).filter(_.nonEmpty).orNull
  }

  def program_id: String = {
    Option(telemetry.readOrDefault[String]("programId", null)).filter(_.nonEmpty).orNull
  }

  def program_externalId: String = {
    Option(telemetry.readOrDefault[String]("programExternalId", null)).filter(_.nonEmpty).orNull
  }

  def program_name: String = {
    Option(telemetry.readOrDefault[String]("programName", null)).filter(_.nonEmpty).orNull
  }

  def pii_consent_required: Boolean = {
    telemetry.readOrDefault[Boolean]("requestForPIIConsent", false)
  }

  def user_id: String = {
    Option(telemetry.readOrDefault[String]("userId", null)).filter(_.nonEmpty).orNull
  }

  def created_at_string: String = {
    Option(telemetry.readOrDefault[String]("createdAt", null)).filter(_.nonEmpty).orNull
  }

  def updated_at_string: String = {
    Option(telemetry.readOrDefault[String]("updatedAt", null)).filter(_.nonEmpty).orNull
  }

  def user_Location: util.ArrayList[util.Map[String, Any]] = {
    telemetry.read[util.ArrayList[util.Map[String, Any]]]("userProfile.userLocations").getOrElse(null)
  }

  def user_Types: util.ArrayList[util.Map[String, Any]] = {
    telemetry.read[util.ArrayList[util.Map[String, Any]]]("userProfile.profileUserTypes").getOrElse(null)
  }

  def organisations: util.Map[String, Any] = {
    telemetry.read[util.Map[String, Any]]("userProfile.rootOrg").getOrElse(null)
  }
}

