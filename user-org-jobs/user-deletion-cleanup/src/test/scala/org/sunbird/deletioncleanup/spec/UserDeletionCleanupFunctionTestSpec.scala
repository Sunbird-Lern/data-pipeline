package org.sunbird.deletioncleanup.spec

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration
import org.apache.flink.test.util.MiniClusterWithClientResource
import org.cassandraunit.CQLDataLoader
import org.cassandraunit.dataset.cql.FileCQLDataSet
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.sunbird.dp.core.job.FlinkKafkaConnector
import org.sunbird.dp.core.util.{CassandraUtil, ElasticSearchUtil, HttpUtil}
import org.sunbird.dp.{BaseMetricsReporter, BaseTestSpec}
import org.sunbird.job.deletioncleanup.domain.Event
import org.sunbird.job.deletioncleanup.task.{UserDeletionCleanupConfig, UserDeletionCleanupStreamTask}


class UserDeletionCleanupFunctionTestSpec extends BaseTestSpec {
  implicit val mapTypeInfo: TypeInformation[java.util.Map[String, AnyRef]] = TypeExtractor.getForClass(classOf[java.util.Map[String, AnyRef]])
  implicit val eventTypeInfo: TypeInformation[Event] = TypeExtractor.getForClass(classOf[Event])
  implicit val stringTypeInfo: TypeInformation[String] = TypeExtractor.getForClass(classOf[String])

  val flinkCluster = new MiniClusterWithClientResource(new MiniClusterResourceConfiguration.Builder()
    .setConfiguration(testConfiguration())
    .setNumberSlotsPerTaskManager(1)
    .setNumberTaskManagers(1)
    .build)

  val mockKafkaUtil: FlinkKafkaConnector = mock[FlinkKafkaConnector](Mockito.withSettings().serializable())

  val config: Config = ConfigFactory.load("test.conf")
  val jobConfig: UserDeletionCleanupConfig = new UserDeletionCleanupConfig(config)
  var mockHttpUtil: HttpUtil = mock[HttpUtil](Mockito.withSettings().serializable())
  var mockEsUtil: ElasticSearchUtil = mock[ElasticSearchUtil](Mockito.withSettings().serializable())
  var cassandraUtil: CassandraUtil = _


  override protected def beforeAll(): Unit = {
    super.beforeAll()

    EmbeddedCassandraServerHelper.startEmbeddedCassandra(80000L)
    cassandraUtil = new CassandraUtil(jobConfig.dbHost, jobConfig.dbPort, jobConfig.isMultiDCEnabled)
    val session = cassandraUtil.session

    val dataLoader = new CQLDataLoader(session)
    dataLoader.load(new FileCQLDataSet(getClass.getResource("/test.cql").getPath, true, true))

    testCassandraUtil(cassandraUtil)
    // Clear the metrics
    BaseMetricsReporter.gaugeMetrics.clear()
    flinkCluster.before()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    flinkCluster.after()
  }

  def testCassandraUtil(cassandraUtil: CassandraUtil): Unit = {
    cassandraUtil.reconnect()
  }
  def initialize() {
    when(mockKafkaUtil.kafkaEventSource[Event](jobConfig.inputTopic))
      .thenReturn(new UserDeletionCleanupEventSource)
    when(mockKafkaUtil.kafkaStringSink(jobConfig.inputTopic)).thenReturn(new GenerateUserDeletionCleanupSink)
  }

  "UserDeletionCleanupStreamTaskProcessor " should "validate metrics " in {
    initialize()
    new UserDeletionCleanupStreamTask(jobConfig, mockHttpUtil, mockEsUtil, mockKafkaUtil).process()
    BaseMetricsReporter.gaugeMetrics(s"${jobConfig.jobName}.${jobConfig.totalEventsCount}").getValue() should be(1)
  }

}
