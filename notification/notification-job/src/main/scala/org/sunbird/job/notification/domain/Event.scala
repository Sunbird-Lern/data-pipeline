package org.sunbird.job.notification.domain

import java.util

import org.sunbird.job.domain.reader.JobRequest

class Event(eventMap: java.util.Map[String, Any], partition: Int, offset: Long) extends JobRequest(eventMap, partition , offset) {
    
    private val jobName = "CollectionCertificateGenerator"
    
    import scala.collection.JavaConverters._
    
    def actor: Map[String, AnyRef] = readOrDefault[Map[String, AnyRef]]("action", Map[String, AnyRef]())
    def action: String = readOrDefault[String]("edata.action", "")
    def msgId: String = readOrDefault[String]("mid", "")
    def edataMap: Map[String, Any] = readOrDefault[Map[String, Any]]("edata", Map[String, Any]())
    def objectMap: Map[String, AnyRef] = readOrDefault[Map[String, AnyRef]]("object", Map[String, AnyRef]())
    
}
