package org.sunbird.job.notification.task

import org.sunbird.job.BaseJobConfig
import com.typesafe.config.Config
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.streaming.api.scala.OutputTag
import org.sunbird.job.notification.domain.NotificationMessage

class NotificationConfig(override val config: Config) extends BaseJobConfig(config, "notification-trigger") {
    
    implicit val stringTypeInfo: TypeInformation[String] = TypeExtractor.getForClass(classOf[String])
    implicit val notificationFailedMetaTypeInfo: TypeInformation[NotificationMessage] = TypeExtractor.getForClass(classOf[NotificationMessage])
    
    
    // Kafka Topics Configuration
    val kafkaInputTopic: String = config.getString("kafka.input.topic")
    override val kafkaConsumerParallelism: Int = config.getInt("task.consumer.parallelism")
    
    // Consumers
    val notificationConsumer = "notification-consumer"
    val notificationFailedProducer = "notification-failed-producer"
    val fcm_account_key : String = config.getString("fcm_account_key")
    val sms_auth_key : String = config.getString("sms_auth_key")
    val sms_default_sender : String = config.getString("sms_default_sender")
    val mail_server_from_email : String = config.getString("mail_server_from_email")
    val mail_server_username : String = config.getString("mail_server_username")
    val mail_server_password : String = config.getString("mail_server_password")
    val mail_server_host : String = config.getString("mail_server_host")
    val mail_server_port : String = config.getString("mail_server_port")
    val max_iteration_count_samza_job : Int = 2
    
    // Metric List
    val totalEventsCount = "total-events-count"
    val successEventCount = "success-events-count"
    val failedEventCount = "failed-events-count"
    val skippedEventCount = "skipped-event-count"
    
    //val notificationFailedOutputTag: OutputTag[NotificationMessage] = OutputTag[NotificationMessage]("notification-failed")
    val notificationFailedOutputTagName = "notification-failed-events"
    val notificationFailedOutputTag: OutputTag[String] = OutputTag[String](notificationFailedOutputTagName)
    
}
