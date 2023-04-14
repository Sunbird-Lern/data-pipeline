package org.sunbird.job.certmigrator.functions

import com.datastax.driver.core.querybuilder.{QueryBuilder, Update}
import com.datastax.driver.core.{Row, TypeTokens}
import com.google.gson.reflect.TypeToken
import kong.unirest.UnirestException
import org.apache.commons.lang.StringUtils
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.KeyedProcessFunction
import org.slf4j.LoggerFactory
import org.sunbird.incredible.processor.CertModel
import org.sunbird.incredible.processor.store.StorageService
import org.sunbird.incredible.{CertificateConfig, ScalaModuleJsonUtils}
import org.sunbird.job.certmigrator.domain.Issuer
import org.sunbird.job.certmigrator.domain._
import org.sunbird.job.certmigrator.exceptions.ServerException
import org.sunbird.job.certmigrator.task.CertificateGeneratorConfig
import org.sunbird.job.exception.InvalidEventException
import org.sunbird.job.util.{CassandraUtil, ElasticSearchUtil, HttpUtil, ScalaJsonUtil}
import org.sunbird.job.{BaseProcessKeyedFunction, Metrics}

import java.io.{File, IOException}
import java.lang.reflect.Type
import java.text.SimpleDateFormat
import java.util
import java.util.stream.Collectors
import java.util.{Base64, Date}
import scala.collection.JavaConverters._

class CertificateGeneratorFunction(config: CertificateGeneratorConfig, httpUtil: HttpUtil, @transient var cassandraUtil: CassandraUtil = null)
  extends BaseProcessKeyedFunction[String, Event, String](config) {


  private[this] val logger = LoggerFactory.getLogger(classOf[CertificateGeneratorFunction])
  val mapType: Type = new TypeToken[java.util.Map[String, AnyRef]]() {}.getType
  val directory: String = "certificates/"
  val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  implicit val certificateConfig: CertificateConfig = CertificateConfig(basePath = config.basePath, encryptionServiceUrl = config.encServiceUrl, contextUrl = config.CONTEXT, issuerUrl = config.ISSUER_URL,
    evidenceUrl = config.EVIDENCE_URL, signatoryExtension = config.SIGNATORY_EXTENSION)
  implicit var esUtil: ElasticSearchUtil = null

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    cassandraUtil = new CassandraUtil(config.dbHost, config.dbPort, config.isMultiDCEnabled)
    if(esUtil==null)
      esUtil = new ElasticSearchUtil(config.esConnection, config.certIndex, "config.auditHistoryIndexType")
  }

  override def close(): Unit = {
    cassandraUtil.close()
    if(esUtil!=null) esUtil.close()
    super.close()
  }

  override def metricsList(): List[String] = {
    List(config.successEventCount, config.failedEventCount, config.skippedEventCount, config.totalEventsCount, config.dbUpdateCount, config.enrollmentDbReadCount, config.totalEventsCount)
  }


  override def processElement(event: Event,
                              context: KeyedProcessFunction[String, Event, String]#Context,
                              metrics: Metrics): Unit = {
    println("Certificate data: " + event)
    metrics.incCounter(config.totalEventsCount)
    try {
      val certValidator = new CertValidator()
      logger.info("Certificate generator | is rc integration enabled: " + config.enableRcCertificate)
      certValidator.validateGenerateCertRequest(event, config.enableSuppressException)
      if(isNotMigrated(event)) {
        generateCertificateUsingRC(event, context)(metrics)
      } else {
        metrics.incCounter(config.skippedEventCount)
        logger.info(s"Certificate already migrated for: ${event.eData.getOrElse("userId", "")} ${event.related}")
      }
    } catch {
      case e: Exception =>
        metrics.incCounter(config.failedEventCount)
        throw new InvalidEventException(e.getMessage, Map("partition" -> event.partition, "offset" -> event.offset), e)
    }
  }

  @throws[Exception]
  def generateCertificateUsingRC(event: Event, context: KeyedProcessFunction[String, Event, String]#Context)(implicit metrics: Metrics): Unit = {
    val certModelList: List[CertModel] = new CertMapper(certificateConfig).mapReqToCertModel(event)
    certModelList.foreach(certModel => {
      var uuid: String = null
      //if reissue then read rc for oldId and call rc delete api
      val related = event.related
      val certReq = generateRequest(event, certModel)
      //make api call to registry
      uuid = callCertificateRc(config.rcCreateApi, null, certReq)
      val userEnrollmentData = UserEnrollmentData(related.getOrElse(config.BATCH_ID, "").asInstanceOf[String], certModel.identifier,
        related.getOrElse(config.COURSE_ID, "").asInstanceOf[String], event.courseName, event.templateId,
        Certificate(uuid, event.name, "", formatter.format(new Date()), event.svgTemplate, config.rcEntity))
      updateUserEnrollmentTable(event, userEnrollmentData, context)
      metrics.incCounter(config.successEventCount)
    })

    deleteOldRegistry(event.oldId)
  }

  def deleteOldRegistry(id: String) = {
    try {
      revokeCassandraRecord(id)
      deleteEsRecord(id)
    } catch {
      case ex: Exception =>
        logger.error("Old registry deletion failed | old id is not present :: identifier " + id+ " :: " + ex.getMessage)

    }
  }

  def revokeCassandraRecord(id: String): Unit = {
    val query = QueryBuilder.update(config.sbKeyspace, config.certRegTable).where()
      .`with`(QueryBuilder.set("isrevoked", true))
      .where(QueryBuilder.eq("id", id))
      .ifExists
    cassandraUtil.update(query)
  }

  def deleteEsRecord(id: String): Unit = {
    esUtil.deleteDocument(id)
  }

  def generateRequest(event: Event, certModel: CertModel):  Map[String, AnyRef] = {
    val req = Map("filters" -> Map())
    val publicKeyId: String = callCertificateRc(config.rcSearchApi, null, req)
    val replacedUrl = if(event.svgTemplate.contains(config.cloudStoreBasePathPlaceholder)) event.svgTemplate.replace(config.cloudStoreBasePathPlaceholder, config.baseUrl+"/"+config.contentCloudStorageContainer) else event.svgTemplate
    logger.info("generateRequest: template url from event {}", event.svgTemplate)
    logger.info("generateRequest: template url after replacing placeholder {}", replacedUrl)
    val createCertReq = Map[String, AnyRef](
      "certificateLabel" -> certModel.certificateName,
      "status" -> "ACTIVE",
      "templateUrl" -> replacedUrl,
      "training" -> Training(event.related.getOrElse(config.COURSE_ID, "").asInstanceOf[String], event.courseName, "Course", event.related.getOrElse(config.BATCH_ID, "").asInstanceOf[String]),
      "recipient" -> Recipient(certModel.identifier, certModel.recipientName, null),
      "issuer" -> Issuer(certModel.issuer.url, certModel.issuer.name, publicKeyId),
      "signatory" -> event.signatoryList,
      config.OLD_ID -> event.oldId
    )
    createCertReq
  }

  @throws[ServerException]
  @throws[UnirestException]
  def callCertificateRc(api: String, identifier: String, request: Map[String, AnyRef]): String = {
    logger.info("Certificate rc called | Api:: " + api)
    var id: String = null
    val uri: String = config.rcBaseUrl + "/" + config.rcEntity
    val status = api match {
      case config.rcDeleteApi => httpUtil.delete(uri + "/" +identifier).status
      case config.rcCreateApi =>
        val plainReq: String = ScalaModuleJsonUtils.serialize(request)
        val req = removeBadChars(plainReq)
        logger.info("RC Create API request: " + req)
        val httpResponse = httpUtil.post(uri, req)
        if(httpResponse.status == 200) {
          val response = ScalaJsonUtil.deserialize[Map[String, AnyRef]](httpResponse.body)
          println(httpResponse.body)
          id = response.getOrElse("result", Map[String, AnyRef]()).asInstanceOf[Map[String, AnyRef]].getOrElse(config.rcEntity, Map[String, AnyRef]()).asInstanceOf[Map[String, AnyRef]].getOrElse("osid","").asInstanceOf[String]
        } else {
          logger.error("RC Create Error Response: " + httpResponse.status +  " :: Response: " + httpResponse.body)
        }
        httpResponse.status
      case config.rcSearchApi =>
        val req: String = ScalaModuleJsonUtils.serialize(request)
        val searchUri = config.rcBaseUrl + "/" + "PublicKey" + "/search"
        val httpResponse = httpUtil.post(searchUri, req)
        if(httpResponse.status == 200) {
          val resp = ScalaJsonUtil.deserialize[List[Map[String, AnyRef]]](httpResponse.body)
          id = resp.head.getOrElse("osid", null).asInstanceOf[String]
        }
        httpResponse.status
    }
    if (status == 200) {
      logger.info("certificate rc successfully executed for api: " + api)
    } else {
      logger.error("certificate rc failed for api: " + api +  " | Status is: " + status)
      throw ServerException("ERR_API_CALL", "Something Went Wrong While Making API Call:  " + api +  " | Status is: " + status)
    }
    id
  }

  private def removeBadChars(request: String): String = {
    config.badCharList.split(",").foldLeft(request)((curReq, removeChar) => StringUtils.remove(curReq, removeChar))
  }

  private def cleanUp(fileName: String, path: String): Unit = {
    try {
      val directory = new File(path)
      val files: Array[File] = directory.listFiles
      if (files != null && files.length > 0)
        files.foreach(file => {
          if (file.getName.startsWith(fileName)) file.delete
        })
      logger.info("cleanUp completed")
    } catch {
      case ex: Exception =>
        logger.error(ex.getMessage, ex)
    }
  }

  def updateUserEnrollmentTable(event: Event, certMetaData: UserEnrollmentData, context: KeyedProcessFunction[String, Event, String]#Context)(implicit metrics: Metrics): Unit = {
    logger.info("updating user enrollment table {}", certMetaData)
    val primaryFields = Map(config.userId.toLowerCase() -> certMetaData.userId, config.batchId.toLowerCase -> certMetaData.batchId, config.courseId.toLowerCase -> certMetaData.courseId)
    val records = getIssuedCertificatesFromUserEnrollmentTable(primaryFields)
    if (records.nonEmpty) {
      records.foreach((row: Row) => {
        val issuedOn = row.getTimestamp("completedOn")
        var certificatesList = row.getList(config.issued_certificates, TypeTokens.mapOf(classOf[String], classOf[String]))
        if (null == certificatesList && certificatesList.isEmpty) {
          certificatesList = new util.ArrayList[util.Map[String, String]]()
        }
        val updatedCerts: util.List[util.Map[String, String]] = certificatesList.stream().filter(cert => !StringUtils.equalsIgnoreCase(certMetaData.certificate.name, cert.get("name"))).collect(Collectors.toList())
        updatedCerts.add(mapAsJavaMap(Map[String, String](
          config.name -> certMetaData.certificate.name,
          config.identifier -> certMetaData.certificate.id,
          config.token -> certMetaData.certificate.token,
        ) ++ {if(!certMetaData.certificate.lastIssuedOn.isEmpty) Map[String, String](config.lastIssuedOn -> certMetaData.certificate.lastIssuedOn)
        else Map[String, String]()}
        ++ {if(config.enableRcCertificate) Map[String, String](config.templateUrl -> certMetaData.certificate.templateUrl, config.`type`->certMetaData.certificate.`type`)
        else Map[String, String]()}
        ))
        
        val query = getUpdateIssuedCertQuery(updatedCerts, certMetaData.userId, certMetaData.courseId, certMetaData.batchId, config)
        logger.info("update query {}", query.toString)
        val result = cassandraUtil.update(query)
        logger.info("update result {}", result)
        if (result) {
          logger.info("issued certificates in user-enrollment table  updated successfully")
          metrics.incCounter(config.dbUpdateCount)
          val certificateAuditEvent = generateAuditEvent(certMetaData)
          logger.info("pushAuditEvent: audit event generated for certificate : " + certificateAuditEvent)
          val audit = ScalaJsonUtil.serialize(certificateAuditEvent)
          context.output(config.auditEventOutputTag, audit)
          logger.info("pushAuditEvent: certificate audit event success {}", audit)
        } else {
          metrics.incCounter(config.failedEventCount)
          throw new Exception(s"Update certificates to enrolments failed: ${event}")
        }

      })
    }

  }


  /**
    * returns query for updating issued_certificates in user_enrollment table
    */
  def getUpdateIssuedCertQuery(updatedCerts: util.List[util.Map[String, String]], userId: String, courseId: String, batchId: String, config: CertificateGeneratorConfig):
  Update.Where = QueryBuilder.update(config.dbKeyspace, config.dbEnrollmentTable).where()
    .`with`(QueryBuilder.set(config.issued_certificates, updatedCerts))
    .where(QueryBuilder.eq(config.userId.toLowerCase, userId))
    .and(QueryBuilder.eq(config.courseId.toLowerCase, courseId))
    .and(QueryBuilder.eq(config.batchId.toLowerCase, batchId))


  private def getIssuedCertificatesFromUserEnrollmentTable(columns: Map[String, AnyRef])(implicit metrics: Metrics) = {
    logger.info("primary columns {}", columns)
    val selectWhere = QueryBuilder.select().all()
      .from(config.dbKeyspace, config.dbEnrollmentTable).
      where()
    columns.map(col => {
      col._2 match {
        case value: List[Any] =>
          selectWhere.and(QueryBuilder.in(col._1, value.asJava))
        case _ =>
          selectWhere.and(QueryBuilder.eq(col._1, col._2))
      }
    })
    logger.info("select query {}", selectWhere.toString)
    metrics.incCounter(config.enrollmentDbReadCount)
    cassandraUtil.find(selectWhere.toString).asScala.toList
  }


  private def generateAuditEvent(data: UserEnrollmentData): CertificateAuditEvent = {
    CertificateAuditEvent(
      actor = Actor(id = data.userId),
      context = EventContext(cdata = Array(Map("type" -> config.courseBatch, config.id -> data.batchId).asJava)),
      `object` = EventObject(id = data.certificate.id, `type` = "Certificate", rollup = Map(config.l1 -> data.courseId).asJava))
  }

  def isNotMigrated(event: Event):Boolean = {
    val query = QueryBuilder.select("isrevoked").from(config.sbKeyspace, config.certRegTable)
      .where(QueryBuilder.eq(config.id, event.eData.getOrElse("identifier", "")))
    val row = cassandraUtil.findOne(query.toString)
    if (null != row) true else false
  }

}
