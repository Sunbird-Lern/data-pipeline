package org.sunbird.job.userdelete.domain

import org.sunbird.dp.core.domain.Events

import java.util

class Event(eventMap: util.Map[String, Any]) extends Events(eventMap) {

  def userId: String = {
    telemetry.read[String]("edata.userId").orNull
  }

}
