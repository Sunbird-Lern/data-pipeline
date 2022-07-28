package org.sunbird.job.notification.domain

object NotificationType extends Enumeration {
    type NotificationType = Value
    
    val phone = Value("phone")
    val email =  Value("email")
    val device = Value("device")
}
