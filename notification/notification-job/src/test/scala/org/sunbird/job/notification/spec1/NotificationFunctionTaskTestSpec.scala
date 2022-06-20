/*
package org.sunbird.job.notification.spec1

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
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.DoNotDiscover
import org.sunbird.job.connector.FlinkKafkaConnector
import org.sunbird.job.notification.domain.Event
import org.sunbird.job.notification.fixture.EventFixture
import org.sunbird.job.notification.task.{NotificationConfig, NotificationStreamTask}
import org.sunbird.job.util.JSONUtil
import org.sunbird.spec.{BaseMetricsReporter, BaseTestSpec}

@DoNotDiscover
class NotificationFunctionTaskTestSpec extends BaseTestSpec {

  implicit val mapTypeInfo: TypeInformation[java.util.Map[String, AnyRef]] = TypeExtractor.getForClass(classOf[java.util.Map[String, AnyRef]])

  //var cassandraUtil: CassandraUtil = _

  val flinkCluster = new MiniClusterWithClientResource(new MiniClusterResourceConfiguration.Builder()
    .setConfiguration(testConfiguration())
    .setNumberSlotsPerTaskManager(1)
    .setNumberTaskManagers(1)
    .build)

  val mockKafkaUtil: FlinkKafkaConnector = mock[FlinkKafkaConnector](Mockito.withSettings().serializable())
  val config: Config = ConfigFactory.load("test.conf")
  val jobConfig: NotificationConfig = new NotificationConfig(config)
  //val storageService: StorageService = mock[StorageService](Mockito.withSettings().serializable())


  override protected def beforeAll(): Unit = {
    super.beforeAll()
    //EmbeddedCassandraServerHelper.startEmbeddedCassandra(80000L)
    //cassandraUtil = new CassandraUtil(jobConfig.dbHost, jobConfig.dbPort)
    //val session = cassandraUtil.session

    //val dataLoader = new CQLDataLoader(session)
    //dataLoader.load(new FileCQLDataSet(getClass.getResource("/test.cql").getPath, true, true))
    flinkCluster.before()
    // Clear the metrics

    BaseMetricsReporter.gaugeMetrics.clear()
    //when(storageService.uploadFile(any[String], any[File])).thenReturn("jsonUrl")
  }


  override protected def afterAll(): Unit = {
    super.afterAll()
    try {
      //EmbeddedCassandraServerHelper.cleanEmbeddedCassandra()
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
    when(mockKafkaUtil.kafkaStringSink(jobConfig.kafkaInputTopic)).thenReturn(new auditEventSink)
    when(mockKafkaUtil.kafkaJobRequestSource[Event](jobConfig.kafkaInputTopic)).thenReturn(new NotificationEventSource)
    intercept[JobExecutionException](new NotificationStreamTask(jobConfig, mockKafkaUtil).process())
    BaseMetricsReporter.gaugeMetrics(s"${jobConfig.jobName}.${jobConfig.totalEventsCount}").getValue() should be(3)
    BaseMetricsReporter.gaugeMetrics(s"${jobConfig.jobName}.${jobConfig.successEventCount}").getValue() should be(1)
    BaseMetricsReporter.gaugeMetrics(s"${jobConfig.jobName}.${jobConfig.failedEventCount}").getValue() should be(1)
    BaseMetricsReporter.gaugeMetrics(s"${jobConfig.jobName}.${jobConfig.skippedEventCount}").getValue() should be(1)
    auditEventSink.values.size() should be(1)
  }


}

class NotificationEventSource extends SourceFunction[Event] {
  override def run(ctx: SourceContext[Event]): Unit = {
    val eventMap1: util.Map[String, Any] = JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.EVENT_1)
    ctx.collect(new Event(eventMap1.asInstanceOf[util.Map[String, Any]],0,0))
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
*/
