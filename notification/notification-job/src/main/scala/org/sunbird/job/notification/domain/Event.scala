package org.sunbird.job.notification.domain

import org.sunbird.job.domain.reader.JobRequest

class Event(eventMap: java.util.Map[String, Any], partition: Int, offset: Long) extends JobRequest(eventMap, partition , offset) {
    
    import scala.collection.JavaConverters._
    
    def actor: Map[String, AnyRef] = readOrDefault[Map[String, AnyRef]]("actor", Map[String, AnyRef]())
    def eid: String = readOrDefault[String]("eid", "")
    def action: String = readOrDefault[String]("edata.action", "")
    def requestMap: Map[String, AnyRef] = readOrDefault[Map[String, AnyRef]]("edata.request", Map[String, AnyRef]())
    def msgId: String = readOrDefault[String]("mid", "")
    def traceMap: Map[String, Any] = readOrDefault[Map[String, Any]]("trace", Map[String, Any]())
    //def ets: String = readOrDefault[String]("ets","")
    def edataMap: Map[String, Any] = readOrDefault[Map[String, Any]]("edata", Map[String, Any]())
    def objectMap: Map[String, AnyRef] = readOrDefault[Map[String, AnyRef]]("object", Map[String, AnyRef]())
    def contextMap: Map[String, AnyRef] = readOrDefault[Map[String, AnyRef]]("context", Map[String, AnyRef]())
}
