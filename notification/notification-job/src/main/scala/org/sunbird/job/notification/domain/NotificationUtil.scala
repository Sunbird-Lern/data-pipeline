package org.sunbird.job.notification.function

import java.util

import scala.collection.immutable.List
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.ScalaObjectMapper
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.slf4j.LoggerFactory
import org.sunbird.job.{BaseProcessKeyedFunction, Metrics}
import org.sunbird.job.notification.task.NotificationConfig
import org.sunbird.job.notification.domain.{Event, NotificationMessage, NotificationType}
import org.sunbird.job.notification.util.datasecurity.OneWayHashing
import org.sunbird.notification.beans.{EmailConfig, EmailRequest, SMSConfig}
import org.sunbird.notification.email.service.{IEmailFactory, IEmailService}
import org.sunbird.notification.email.service.impl.IEmailProviderFactory
import org.sunbird.notification.fcm.provider.{IFCMNotificationService, NotificationFactory}
import org.sunbird.notification.fcm.providerImpl.FCMHttpNotificationServiceImpl
import org.sunbird.notification.sms.provider.ISmsProvider
import org.sunbird.notification.utils.{FCMResponse, SMSFactory}

class NotificationFunction(config: NotificationConfig, @transient var ifcmNotificationService: IFCMNotificationService = null) extends BaseProcessKeyedFunction[String, Event, String](config) {
    
    private[this] val logger = LoggerFactory.getLogger(classOf[NotificationFunction])
    
    private val mapper = new ObjectMapper with ScalaObjectMapper
    private var smsProvider: ISmsProvider = null
    private var accountKey: String = null
    private var emailFactory : IEmailFactory = null
    private var emailService : IEmailService = null
    ifcmNotificationService = NotificationFactory.getInstance(NotificationFactory.instanceType.httpClinet.name)
    private var maxIterations = 0
    private val MAXITERTIONCOUNT = 2
    val ACTOR = "actor"
    val ID = "id"
    val TYPE = "type"
    val EID = "eid"
    val EDATA = "edata"
    val ACTION = "action"
    val REQUEST = "request"
    val NOTIFICATION = "notification"
    val MODE = "mode"
    val DELIVERY_TYPE = "deliveryType"
    val CONFIG = "config"
    val IDS = "ids"
    val OBJECT = "object"
    val ACTION_NAME = "broadcast-topic-notification-all"
    val NOTIFICATIONS = "notifications"
    val RAW_DATA = "rawData"
    val TOPIC = "topic"
    val TEMPLATE = "template"
    val DATA = "data"
    val MID = "mid"
    val SUBJECT = "subject"
    val ITERATION = "iteration"
    
    
    override def open(parameters: Configuration): Unit = {
        super.open(parameters)
        accountKey = config.fcm_account_key
        FCMHttpNotificationServiceImpl.setAccountKey(accountKey)
        val smsConfig = new SMSConfig(config.sms_auth_key, config.sms_default_sender)
        smsProvider = SMSFactory.getInstance("91SMS", smsConfig)
        emailFactory = new IEmailProviderFactory
        emailService = emailFactory.create(new EmailConfig(config.mail_server_from_email, config.mail_server_username, config.mail_server_password, config.mail_server_host, config.mail_server_port))
        maxIterations = getMaxIterations
        logger.info("NotificationService:initialize: Service config initialized")
    }
    
    override def close(): Unit = {
        super.close()
    }
    
    override def metricsList(): scala.List[String] = {
        List(config.successEventCount, config.failedEventCount, config.skippedEventCount, config.totalEventsCount, config.totalEventsCount)
    }
    
    override def processElement(event: Event,
                                context: KeyedProcessFunction[String, Event, String]#Context, metrics: Metrics): Unit = {
        import scala.collection.JavaConverters._
        println("Certificate data: " + event)
        metrics.incCounter(config.totalEventsCount)
        var requestHash: String = ""
        var isSuccess: Boolean = false
        if (event.edataMap != null && event.edataMap.size > 0) {
            val actionValue: String = event.edataMap.get("action").get.asInstanceOf[String]
            if (ACTION_NAME.equalsIgnoreCase(actionValue)) {
                val requestMap: scala.collection.immutable.Map[String, Object] = event.edataMap.get(REQUEST).get.asInstanceOf[scala.collection.immutable.Map[String, Object]]
                requestHash = OneWayHashing.encryptVal(mapper.writeValueAsString(requestMap))
                /*if (!(requestHash == event.objectMap.get(ID).get.asInstanceOf[String]))
                    logger.info("NotificationService:processMessage: hashValue is not matching - " + requestHash)
                else {*/
                    val notificationMap: scala.collection.immutable.HashMap[String, AnyRef] = requestMap.get(NOTIFICATION).get.asInstanceOf[scala.collection.immutable.HashMap[String, AnyRef]]
                    val notificationMode : String = notificationMap.get(MODE).get.toString
                    logger.info("Notification mode0: "+ NotificationType.email)
                    logger.info("Notification mode1: "+ NotificationType.phone)
                    logger.info("Notification mode2: "+ NotificationType.device)
                    logger.info("Notification mode value: "+ notificationMode)
                    if (notificationMode.equalsIgnoreCase(NotificationType.phone.toString)) {
                        logger.info("phone......")
                        isSuccess = sendSmsNotification(notificationMap, event.msgId)
                    } else if (notificationMode == NotificationType.email.toString) {
                        logger.info("mail......")
                        isSuccess = sendEmailNotification(notificationMap)
                    } else if (notificationMode == NotificationType.device.toString) {
                        logger.info("device......")
                        isSuccess = notifyDevice(notificationMap)
                    }
                    if (isSuccess) {
                        metrics.incCounter(config.successEventCount)
                        logger.info("Notification sent successfully.")
                    } else {
                        logger.info("Notification sent failure")
                        handleFailureMessage(event, context, metrics)
                    }
                //}
            }
            else logger.info("NotificationService:processMessage action name is incorrect: " + actionValue + "for message id:" + event.msgId)
        }
        else logger.info("NotificationService:processMessage event data map is either null or empty for message id:" + event.msgId)
    }
    
    protected def getMaxIterations: Int = {
        maxIterations = config.max_iteration_count_samza_job
        if (maxIterations == 0) maxIterations = MAXITERTIONCOUNT
        maxIterations
    }
    
    def sendEmailNotification(notificationMap: scala.collection.immutable.HashMap[String, AnyRef]) = {
        import scala.collection.JavaConverters._
        logger.info("NotificationService:sendEmailNotification map: "+ notificationMap)
        val emailIds : util.List[String] = notificationMap.get(IDS).get.asInstanceOf[List[String]].asJava
        logger.info("NotificationService:sendEmailNotification emailids: "+ emailIds)
        val templateMap : util.Map[String, AnyRef] = notificationMap.get(TEMPLATE).get.asInstanceOf[scala.collection.immutable.Map[String, AnyRef]].asJava
        val config = notificationMap.get(CONFIG).get.asInstanceOf[scala.collection.immutable.Map[String, AnyRef]].asJava
        val subject = config.get(SUBJECT).asInstanceOf[String]
        val emailText = templateMap.get(DATA).asInstanceOf[String]
        val emailRequest = new EmailRequest(subject, emailIds, null, null, "", emailText, null)
        emailService.sendEmail(emailRequest)
    }
    
    def sendSmsNotification(notificationMap: scala.collection.immutable.HashMap[String, AnyRef], msgId: String) = {
        import scala.collection.JavaConverters._
        logger.info("NotificationService:sendSmsNotification map: "+ notificationMap)
        val mobileNumbers :util.List[String] = notificationMap.get(IDS).get.asInstanceOf[List[String]].asJava
        logger.info("NotificationService:sendEmailNotification emailids: "+ mobileNumbers)
        if (mobileNumbers != null) {
            val templateMap = notificationMap.get(TEMPLATE).get.asInstanceOf[scala.collection.immutable.Map[String, AnyRef]].asJava
            val smsText = templateMap.get(DATA).asInstanceOf[String]
            smsProvider.bulkSms(mobileNumbers, smsText)
        }
        else {
            logger.info("mobile numbers not provided for message id:" + msgId)
            true
        }
    }
    
    @throws[JsonProcessingException]
    private def notifyDevice(notificationMap: scala.collection.immutable.HashMap[String, AnyRef]) = {
        import scala.collection.JavaConverters._
        var topic: String = null
        var response: FCMResponse = null
        val deviceIds = notificationMap.get(IDS).get.asInstanceOf[List[String]].asJava
        val dataMap = new util.HashMap[String, String]
        dataMap.put(RAW_DATA, mapper.writeValueAsString(notificationMap.get(RAW_DATA)))
        logger.info("NotificationService:processMessage: calling send notification ")
        if (deviceIds != null) {
            response = ifcmNotificationService.sendMultiDeviceNotification(deviceIds, dataMap, false)
        } else {
            val configMap: util.Map[String, AnyRef] = notificationMap.get(CONFIG).asInstanceOf[util.Map[String, AnyRef]]
            topic = configMap.getOrDefault(TOPIC, "").asInstanceOf[String]
            response = ifcmNotificationService.sendTopicNotification(topic, dataMap, false)
        }
        if (response != null) {
            logger.info("Send device notiifcation response with canonicalId,ErrorMsg,successCount,FailureCount" + response.getCanonical_ids + "," + response.getError + ", " + response.getSuccess + " " + response.getFailure)
            true
        }
        else {
            logger.info("response is improper from fcm:" + response + "for device ids" + deviceIds + "or topic" + topic)
            false
        }
    }
    
    def generateKafkaFailureEvent(data: Event) (implicit m : Manifest[NotificationMessage]): NotificationMessage = {
        logger.info("NotificationService:generateKafkaFailureEvent data event: " + data.getJson())
        mapper.readValue(data.getJson(), new TypeReference[NotificationMessage]() {})
    }
    
    private def handleFailureMessage(event: Event, context: KeyedProcessFunction[String, Event, String]#Context, metrics: Metrics): Unit = {
        logger.info("NotificationService:handleFailureMessage started")
        var iteration : Int = event.edataMap.get(ITERATION).get.asInstanceOf[Int]
        if (iteration < maxIterations) {
            var eMap = event.edataMap;
            iteration = iteration + 1
            eMap += (ITERATION -> iteration)
            event.edataMap+(ITERATION -> iteration)
            //val notificationEvent = generateKafkaFailureEvent(event)
            logger.info("pushAuditEvent: audit event generated for certificate : " + event.getJson())
            metrics.incCounter(config.failedEventCount)
            context.output(config.notificationFailedOutputTag, event.getJson())
        } else {
            metrics.incCounter(config.skippedEventCount)
        }
    }
}
