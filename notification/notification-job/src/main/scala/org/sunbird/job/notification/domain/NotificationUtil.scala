package org.sunbird.job.notification.domain

import org.slf4j.LoggerFactory
import org.sunbird.notification.beans.{EmailConfig, EmailRequest, SMSConfig}
import org.sunbird.notification.email.service.impl.IEmailProviderFactory
import org.sunbird.notification.fcm.provider.NotificationFactory
import org.sunbird.notification.fcm.providerImpl.FCMHttpNotificationServiceImpl
import org.sunbird.notification.utils.SMSFactory

import java.util

class NotificationUtil(fromEmail : String, mailUsername: String, mailPassword: String, mailHost: String, mailPort: String, smsAuthKey: String, smsDefaultSender: String,
                       accountKey: String) {
    
    private[this] val logger = LoggerFactory.getLogger(classOf[NotificationUtil])

    val emailFactory = new IEmailProviderFactory
    val emailService = emailFactory.create(new EmailConfig(fromEmail, mailUsername, mailPassword, mailHost, mailPort))
    val smsConfig = new SMSConfig(smsAuthKey, smsDefaultSender)
    val smsProvider = SMSFactory.getInstance("91SMS", smsConfig)
    val ifcmNotificationService = NotificationFactory.getInstance(NotificationFactory.instanceType.httpClinet.name)
    FCMHttpNotificationServiceImpl.setAccountKey(accountKey)

    def sendEmail(emailRequest: EmailRequest) = {
        emailService.sendEmail(emailRequest)
    }
    
    def sendSmsNotification(mobileNumbers: util.List[String], smsText: String) = {
        smsProvider.bulkSms(mobileNumbers, smsText)
    }

    def sendMultiDeviceNotification(deviceIds: util.List[String], dataMap: util.HashMap[String, String]) = {
        ifcmNotificationService.sendMultiDeviceNotification(deviceIds, dataMap, false)
    }

    def sendTopicNotification(topic: String, dataMap: util.HashMap[String, String]) = {
        ifcmNotificationService.sendTopicNotification(topic, dataMap, false)
    }
}
