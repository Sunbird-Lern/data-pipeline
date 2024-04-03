package org.sunbird.job.certmigrator.task

import com.typesafe.config.Config
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.streaming.api.scala.OutputTag
import org.sunbird.job.BaseJobConfig

import java.util

class CertificateGeneratorConfig(override val config: Config) extends BaseJobConfig(config, "legacy-certificate-migrator") {

  private val serialVersionUID = 2905979434303791379L

  implicit val mapTypeInfo: TypeInformation[util.Map[String, AnyRef]] = TypeExtractor.getForClass(classOf[util.Map[String, AnyRef]])
  implicit val stringTypeInfo: TypeInformation[String] = TypeExtractor.getForClass(classOf[String])

  // Kafka Topics Configuration
  val kafkaInputTopic: String = config.getString("kafka.input.topic")
  val kafkaAuditEventTopic: String = config.getString("kafka.output.audit.topic")

  val enableSuppressException: Boolean = if(config.hasPath("enable.suppress.exception")) config.getBoolean("enable.suppress.exception") else false
  val enableRcCertificate: Boolean = if(config.hasPath("enable.rc.certificate")) config.getBoolean("enable.rc.certificate") else false


  // Producers
  val certificateGeneratorAuditProducer = "legacy-certificate-migrator-audit-events-sink"

  override val kafkaConsumerParallelism: Int = config.getInt("task.consumer.parallelism")

  //ES configuration
  val esConnection: String = config.getString("es.basePath")
  val certIndex: String = "certs"

  // Cassandra Configurations
  val sbKeyspace: String = config.getString("lms-cassandra.sbkeyspace")
  val certRegTable: String = config.getString("lms-cassandra.certreg.table")
  val dbEnrollmentTable: String = config.getString("lms-cassandra.user_enrolments.table")
  val dbKeyspace: String = config.getString("lms-cassandra.keyspace")
  val dbHost: String = config.getString("lms-cassandra.host")
  val dbPort: Int = config.getInt("lms-cassandra.port")
  val dbBatchId = "batchid"
  val dbCourseId = "courseid"
  val dbUserId = "userid"
  val issuedCertificates: String = "issued_certificates"

  // Metric List
  val totalEventsCount = "total-events-count"
  val successEventCount = "success-events-count"
  val failedEventCount = "failed-events-count"
  val skippedEventCount = "skipped-event-count"
  val enrollmentDbReadCount = "enrollment-db-read-count"
  val dbUpdateCount = "db-update-user-enrollment-count"

  // Consumers
  val certificateGeneratorConsumer = "certificate"

  // env vars
  val domainUrl: String = config.getString("cert_domain_url")
  val encServiceUrl: String = config.getString("service.enc.basePath")
  val basePath: String = domainUrl.concat("/").concat("certs")
  val rcBaseUrl: String = config.getString("service.rc.basePath")
  val rcEntity: String = config.getString("service.rc.entity")
  val rcApiKey: String = config.getString("service.rc.rcApiKey")
  val rcCreateApi: String = "service.rc.create.api"
  val rcDeleteApi: String = "service.rc.delete.api"
  val rcSearchApi: String = "service.rc.search.api"
  val rcPKSearchApi: String = "service.rc.publickey.search.api"


  //constant
  val DATA: String = "data"
  val RECIPIENT_NAME: String = "recipientName"
  val ISSUER_URL: String = basePath.concat("/Issuer.json")
  val EVIDENCE_URL: String = basePath.concat("/Evidence.json")
  val CONTEXT: String = basePath.concat( "/v1/context.json")
  val SIGNATORY_EXTENSION: String = basePath.concat("v1/extensions/SignatoryExtension/context.json")
  val EDATA: String = "edata"
  val OLD_ID: String = "oldId"
  val BATCH_ID: String = "batchId"
  val COURSE_ID: String = "courseId"

  val courseId = "courseId"
  val batchId = "batchId"
  val userId = "userId"
  val identifier = "identifier"
  val body = "body"
  val request = "request"
  val issued_certificates = "issued_certificates"
  val eData = "edata"
  val name = "name"
  val token = "token"
  val lastIssuedOn = "lastIssuedOn"
  val templateUrl = "templateUrl"
  val `type` = "type"
  val certificate = "certificate"
  val courseName = "courseName"
  val templateId = "templateId"
  val courseBatch = "CourseBatch"
  val l1 = "l1"
  val id = "id"
  val data = "data"
  val badCharList = if(config.hasPath("task.rc.badcharlist")) config.getString("task.rc.badcharlist") else "\\x00,\\\\aaa,\\aaa,Ø,Ý"

  // Tags
  val auditEventOutputTagName = "audit-events"
  val auditEventOutputTag: OutputTag[String] = OutputTag[String](auditEventOutputTagName)

  //UserFeed constants
  val cloudStoreBasePath = config.getString("cloud_storage_base_url")
  val cloudStoreBasePathPlaceholder = config.getString("cloud_store_base_path_placeholder")
  val contentCloudStorageContainer = config.getString("content_cloud_storage_container")
  val cnameUrl = config.getString("cloud_storage_cname_url")
  val baseUrl = if(cnameUrl.isEmpty) cloudStoreBasePath else cnameUrl
}
