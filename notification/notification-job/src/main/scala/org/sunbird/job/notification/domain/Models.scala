package org.sunbird.job.notification.domain

import java.io.Serializable
import java.util
import java.util.{HashMap, Map, UUID}

class Models extends Serializable {}

case class NotificationMessage(actor: Actor, eid: String, mid: String, trace: util.Map[String, String], ets: Long, edata: EventData, context: Context, val `object`: util.Map[String, AnyRef])

@SerialVersionUID(3676213939777740836L)
class EventData() extends Serializable {
    private var action: String = null
    private var iteration = 0
    private var request: util.Map[String, AnyRef] = null
    
    def getAction: String = action
    
    def setAction(action: String): Unit = {
        this.action = action
    }
    
    def getRequest: util.Map[String, AnyRef] = request
    
    def setRequest(request: util.Map[String, AnyRef]): Unit = {
        this.request = request
    }
    
    def getIteration: Int = iteration
    
    def setIteration(iteration: Int): Unit = {
        this.iteration = iteration
    }
}

@SerialVersionUID(6603396659308070730L)
class Actor() extends Serializable {
    private var id: String = null
    private var `type`: String = null
    
    def this(id: String, `type`: String) {
        this()
        this.id = id
        this.`type` = `type`
    }
    
    def getId: String = id
    
    def setId(id: String): Unit = {
        this.id = id
    }
    
    def getType: String = `type`
    
    def setType(`type`: String): Unit = {
        this.`type` = `type`
    }
}

@SerialVersionUID(-5267883245767530083L)
class Context extends Serializable {
    private var pdata: util.Map[String, AnyRef] = null
    
    def getPdata: util.Map[String, AnyRef] = pdata
    
    def setPdata(pdata: util.Map[String, AnyRef]): Unit = {
        this.pdata = pdata
    }
}

