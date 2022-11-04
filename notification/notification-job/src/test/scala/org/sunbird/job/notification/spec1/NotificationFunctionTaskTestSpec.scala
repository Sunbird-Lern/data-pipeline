package org.sunbird.job.collectioncert.function.spec

import java.util

import com.google.gson.Gson
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import org.apache.flink.test.util.MiniClusterWithClientResource
import org.mockito.Mockito.when
import org.mockito.{ Mockito}
import org.scalatest.DoNotDiscover
import org.sunbird.job.connector.FlinkKafkaConnector
import org.sunbird.job.notification.domain.Event
import org.sunbird.job.notification.fixture.EventFixture
import org.sunbird.job.notification.task.{NotificationConfig, NotificationStreamTask}
import org.sunbird.job.util.{CassandraUtil, JSONUtil}
import org.sunbird.spec.{BaseMetricsReporter, BaseTestSpec}

@DoNotDiscover
class NotificationFunctionTaskTestSpec extends BaseTestSpec {
    implicit val mapTypeInfo: TypeInformation[java.util.Map[String, AnyRef]] = TypeExtractor.getForClass(classOf[java.util.Map[String, AnyRef]])
    implicit val eventTypeInfo: TypeInformation[Event] = TypeExtractor.getForClass(classOf[Event])
    implicit val stringTypeInfo: TypeInformation[String] = TypeExtractor.getForClass(classOf[String])

    val flinkCluster = new MiniClusterWithClientResource(new MiniClusterResourceConfiguration.Builder()
        .setConfiguration(testConfiguration())
        .setNumberSlotsPerTaskManager(1)
        .setNumberTaskManagers(1)
        .build)

    val mockKafkaUtil: FlinkKafkaConnector = mock[FlinkKafkaConnector](Mockito.withSettings().serializable())
    val gson = new Gson()
    val config: Config = ConfigFactory.load("test.conf")
    val jobConfig: NotificationConfig = new NotificationConfig(config)


    var cassandraUtil: CassandraUtil = _

    override protected def beforeAll(): Unit = {
        super.beforeAll()

        // Clear the metrics
        BaseMetricsReporter.gaugeMetrics.clear()
        flinkCluster.before()
    }

    override protected def afterAll(): Unit = {
        super.afterAll()
        flinkCluster.after()
    }

    def initialize() {
        when(mockKafkaUtil.kafkaJobRequestSource[Event](jobConfig.kafkaInputTopic))
            .thenReturn(new NotificationEventSource)
        when(mockKafkaUtil.kafkaStringSink(jobConfig.kafkaInputTopic)).thenReturn(new GenerateNotificationSink)
    }
    "NotificationStreamTaskProcessor " should "validate metrics " in {
        initialize()
        new NotificationStreamTask(jobConfig, mockKafkaUtil).process()
        BaseMetricsReporter.gaugeMetrics(s"${jobConfig.jobName}.${jobConfig.totalEventsCount}").getValue() should be(1)
    }

}

class NotificationEventSource extends SourceFunction[Event] {
    override def run(ctx: SourceContext[Event]): Unit = {
        ctx.collect(new Event(JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.EVENT_1), 0, 0))
    }
    override def cancel(): Unit = {}
}

class GenerateNotificationSink extends SinkFunction[String] {
    override def invoke(value: String): Unit = {
        synchronized {
            println(value)
            GenerateNotificationSink.values.add(value)
        }
    }
}

object GenerateNotificationSink {
    val values: util.List[String] = new util.ArrayList()
}

