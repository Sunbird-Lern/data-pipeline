package org.sunbird.job.certmigrator.spec

import java.io.File
import java.util

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.runtime.client.JobExecutionException
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import org.apache.flink.test.util.MiniClusterWithClientResource
import org.cassandraunit.CQLDataLoader
import org.cassandraunit.dataset.cql.FileCQLDataSet
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import org.mockito.ArgumentMatchers.{any, contains, endsWith}
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.DoNotDiscover
import org.sunbird.incredible.processor.store.StorageService
import org.sunbird.job.certmigrator.domain.Event
import org.sunbird.job.certmigrator.fixture.EventFixture
import org.sunbird.job.certmigrator.task.{CertificateGeneratorConfig, CertificateGeneratorStreamTask}
import org.sunbird.job.connector.FlinkKafkaConnector
import org.sunbird.job.util.{CassandraUtil, HTTPResponse, HttpUtil, JSONUtil}
import org.sunbird.spec.{BaseMetricsReporter, BaseTestSpec}

@DoNotDiscover
class CertificateGeneratorFunctionTaskTestSpec extends BaseTestSpec {

  implicit val mapTypeInfo: TypeInformation[java.util.Map[String, AnyRef]] = TypeExtractor.getForClass(classOf[java.util.Map[String, AnyRef]])

  var cassandraUtil: CassandraUtil = _

  val flinkCluster = new MiniClusterWithClientResource(new MiniClusterResourceConfiguration.Builder()
    .setConfiguration(testConfiguration())
    .setNumberSlotsPerTaskManager(1)
    .setNumberTaskManagers(1)
    .build)

  val mockKafkaUtil: FlinkKafkaConnector = mock[FlinkKafkaConnector](Mockito.withSettings().serializable())
  val config: Config = ConfigFactory.load("test.conf")
  val jobConfig: CertificateGeneratorConfig = new CertificateGeneratorConfig(config)
  val mockHttpUtil: HttpUtil = mock[HttpUtil](Mockito.withSettings().serializable())
  val storageService: StorageService = mock[StorageService](Mockito.withSettings().serializable())


  override protected def beforeAll(): Unit = {
    super.beforeAll()
    EmbeddedCassandraServerHelper.startEmbeddedCassandra(80000L)
    cassandraUtil = new CassandraUtil(jobConfig.dbHost, jobConfig.dbPort, jobConfig.isMultiDCEnabled)
    val session = cassandraUtil.session

    val dataLoader = new CQLDataLoader(session)
    dataLoader.load(new FileCQLDataSet(getClass.getResource("/test.cql").getPath, true, true))
    flinkCluster.before()
    // Clear the metrics

    BaseMetricsReporter.gaugeMetrics.clear()
    when(mockHttpUtil.post(endsWith("/certs/v2/registry/add"), any[String], any[Map[String, String]]())).thenReturn(HTTPResponse(200, """{"id":"api.certs.registry.add","ver":"v2","ts":"1602590393507","params":null,"responseCode":"OK","result":{"id":"c96d60f8-9c76-4a73-9ef0-9e01d0f726c6"}}"""))
    when(mockHttpUtil.get(contains("/private/user/v1/read"), any[Map[String, String]]())).thenReturn(HTTPResponse(200, """{"id":"","ver":"private","ts":"2020-10-21 14:10:49:964+0000","params":{"resmsgid":null,"msgid":"a6f3e248-c504-4c2f-9bfa-90f54abd2e30","err":null,"status":"success","errmsg":null},"responseCode":"OK","result":{"response":{"firstName":"test12","lastName":"A","maskedPhone":"******0183","rootOrgName":"ORG_002","userName":"teast123","rootOrgId":"01246944855007232011"}}}"""))
    when(mockHttpUtil.post(endsWith("/v2/notification"), any[String], any[Map[String, String]]())).thenReturn(HTTPResponse(200, """{"id":"api.notification","ver":"v2","ts":"2020-10-21 14:12:09:065+0000","params":{"resmsgid":null,"msgid":"0df38787-1168-4ae0-aa4b-dcea23ea81e4","err":null,"status":"success","errmsg":null},"responseCode":"OK","result":{"response":"SUCCESS"}}"""))
    when(mockHttpUtil.post(endsWith("/private/user/feed/v1/create"), any[String], any[Map[String, String]]())).thenReturn(HTTPResponse(200, """{"id":"api.user.feed.create","ver":"v1","ts":"2020-10-30 13:20:54:940+0000","params":{"resmsgid":null,"msgid":"518d3404-cf1f-4001-81a5-0c58647b32fe","err":null,"status":"success","errmsg":null},"responseCode":"OK","result":{"response":"SUCCESS"}}"""))
    when(storageService.uploadFile(any[String], any[File])).thenReturn("jsonUrl")
  }


  override protected def afterAll(): Unit = {
    super.afterAll()
    try {
      EmbeddedCassandraServerHelper.cleanEmbeddedCassandra()
    } catch {
      case ex: Exception => {
      }
    } finally 
      flinkCluster.after()
  }


  /**
    * this test works on intellij , but using mvn scoverge:report is not working
    */
  it should "generate certificate and add to the registry" in {
    when(mockKafkaUtil.kafkaStringSink(jobConfig.kafkaAuditEventTopic)).thenReturn(new auditEventSink)
    when(mockKafkaUtil.kafkaJobRequestSource[Event](jobConfig.kafkaInputTopic)).thenReturn(new CertificateGeneratorEventSource)
    intercept[JobExecutionException](new CertificateGeneratorStreamTask(jobConfig, mockKafkaUtil, mockHttpUtil).process())
    BaseMetricsReporter.gaugeMetrics(s"${jobConfig.jobName}.${jobConfig.totalEventsCount}").getValue() should be(3)
    BaseMetricsReporter.gaugeMetrics(s"${jobConfig.jobName}.${jobConfig.successEventCount}").getValue() should be(1)
    BaseMetricsReporter.gaugeMetrics(s"${jobConfig.jobName}.${jobConfig.failedEventCount}").getValue() should be(1)
    BaseMetricsReporter.gaugeMetrics(s"${jobConfig.jobName}.${jobConfig.skippedEventCount}").getValue() should be(1)
    BaseMetricsReporter.gaugeMetrics(s"${jobConfig.jobName}.${jobConfig.enrollmentDbReadCount}").getValue() should be(3)
    BaseMetricsReporter.gaugeMetrics(s"${jobConfig.jobName}.${jobConfig.courseBatchdbReadCount}").getValue() should be(1)
    BaseMetricsReporter.gaugeMetrics(s"${jobConfig.jobName}.${jobConfig.dbUpdateCount}").getValue() should be(1)
    BaseMetricsReporter.gaugeMetrics(s"${jobConfig.jobName}.${jobConfig.notifiedUserCount}").getValue() should be(1)
    auditEventSink.values.size() should be(1)
  }


}

class CertificateGeneratorEventSource extends SourceFunction[Event] {
  override def run(ctx: SourceContext[Event]): Unit = {
    val eventMap1: util.Map[String, Any] = JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.EVENT_1)
    val eventMap2: util.Map[String, Any] = JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.EVENT_2)
    val eventMap3: util.Map[String, Any] = JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.EVENT_3)
    ctx.collect(new Event(eventMap1.asInstanceOf[util.Map[String, Any]],0,0))
    ctx.collect(new Event(eventMap3.asInstanceOf[util.Map[String, Any]],0, 1))
    ctx.collect(new Event(eventMap2.asInstanceOf[util.Map[String, Any]],0, 2))
  }

  override def cancel() = {}
}

class auditEventSink extends SinkFunction[String] {

  override def invoke(value: String): Unit = {
    synchronized {
      auditEventSink.values.add(value)
    }
  }
}

object auditEventSink {
  val values: util.List[String] = new util.ArrayList()
}
