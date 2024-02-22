package org.sunbird.job.usercache.domain

import java.util
import org.sunbird.job.domain.reader.{Event => BaseEvent}
import org.sunbird.job.usercache.util.UserReadResult

class Event(eventMap: util.Map[String, Any]) extends BaseEvent(eventMap) {

  override def kafkaKey(): String = {
    did()
  }

  def getId: String = {
    Option(objectType()).map({ t => if (t.equalsIgnoreCase("User")) objectID() else null
    }).getOrElse(null)
  }

  def getState: String = {
    telemetry.read[String]("edata.state").getOrElse(null)
  }

  def getContextDataId(cDataType: String): String = {
    val cdata = telemetry.read[List[Map[String, AnyRef]]]("context.cdata").getOrElse(null)
    var signInType: String = null
    Option(cdata).map(data => {
      data.foreach(cdataMap => {
        if (cdataMap.get("type").get.asInstanceOf[String].equalsIgnoreCase(cDataType)) signInType = cdataMap.get("id").get.toString else signInType
      })
    }).getOrElse(signInType)
    signInType
  }

  def userMetaData(): util.ArrayList[String] = {
    telemetry.read[util.ArrayList[String]]("edata.props").getOrElse(new util.ArrayList[String]())
  }

  def isValid(userReadRes: UserReadResult) = {
    if (userReadRes.responseCode.toUpperCase.equalsIgnoreCase("OK") && !userReadRes.result.isEmpty && userReadRes.result.containsKey("response")) true else false
  }

}
