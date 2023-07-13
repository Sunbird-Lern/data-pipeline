package org.sunbird.dp.spec


import com.google.gson.{Gson, JsonParser}
import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import org.apache.flink.test.util.MiniClusterWithClientResource
import org.cassandraunit.CQLDataLoader
import org.cassandraunit.dataset.cql.FileCQLDataSet
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.sunbird.dp.userinfo.domain.Event
import org.sunbird.dp.core.job.FlinkKafkaConnector
import org.sunbird.dp.core.util.{CassandraUtil, JSONUtil}
import org.sunbird.dp.fixture.EventFixture
import org.sunbird.dp.userinfo.task.{ProgramUserInfoConfig, ProgramUserInfoStreamTask}
import org.sunbird.dp.{BaseMetricsReporter, BaseTestSpec}


import java.util

class ProgramUserInfoTaskTestSpec extends BaseTestSpec {

  /**
   * The TypeInformation interface is part of the Flink API for working with data streams and datasets, It is capable of describing the data type
   */
  implicit val mapTypeInfo: TypeInformation[util.Map[String, AnyRef]] = TypeExtractor.getForClass(classOf[util.Map[String, AnyRef]])

  /**
   * This creates a Apache Flink cluster with one task manager and one slot per task manager, using the MiniClusterWithClientResource class.
   */
  val flinkCluster = new MiniClusterWithClientResource(new MiniClusterResourceConfiguration.Builder()
    .setConfiguration(testConfiguration())
    .setNumberSlotsPerTaskManager(1)
    .setNumberTaskManagers(1)
    .build)

  /**
   * mock[FlinkKafkaConnector] creates a mock instance of FlinkKafkaConnector.
   * new Gson() creates a new instance of the Gson library, which is used for converting between Java objects and JSON.
   */
  val config: Config = ConfigFactory.load("test.conf")
  val programUserConfig: ProgramUserInfoConfig = new ProgramUserInfoConfig(config)
  val mockKafkaUtil: FlinkKafkaConnector = mock[FlinkKafkaConnector](Mockito.withSettings().serializable())
  val gson = new Gson()

  var cassandraUtil: CassandraUtil = _

  /**
   * The startEmbeddedCassandra method of EmbeddedCassandraServerHelper is called to start an embedded Cassandra server.
   * An instance of CassandraUtil is created using the configuration properties from programUserConfig.
   * The test data is loaded into the embedded Cassandra server using the CQLDataLoader from the cassandra-unit library.
   * The testCassandraUtil method is called to verify the CassandraUtil instance and clear any metrics.
   * The before method of the flinkCluster is called to start the Flink cluster.
   */
  override protected def beforeAll(): Unit = {
    super.beforeAll()
    EmbeddedCassandraServerHelper.startEmbeddedCassandra(80000L)
    cassandraUtil = new CassandraUtil(programUserConfig.dbHost, programUserConfig.dbPort, programUserConfig.isMultiDCEnabled)
    val session = cassandraUtil.session
    val dataLoader = new CQLDataLoader(session)
    dataLoader.load(new FileCQLDataSet(getClass.getResource("/test.cql").getPath, true, true));
    // Clear the metrics
    testCassandraUtil(cassandraUtil)
    BaseMetricsReporter.gaugeMetrics.clear()

    flinkCluster.before()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    try {
      EmbeddedCassandraServerHelper.cleanEmbeddedCassandra()
    } catch {
      case ex: Exception => {

      }
    }
    flinkCluster.after()
  }


  it should "get data from Kafka" in {
    when(mockKafkaUtil.kafkaEventSource[Event](programUserConfig.kafkaInputTopic)).thenReturn(new ProgramUserInfoEventSource)
    val task = new ProgramUserInfoStreamTask(programUserConfig,mockKafkaUtil)
    task.process()
  }

  def testCassandraUtil(cassandraUtil: CassandraUtil): Unit = {
    cassandraUtil.reconnect()
    val response = cassandraUtil.find("SELECT * FROM sunbird_programs.program_enrollment;")
    response should not be (null)
  }
}


class ProgramUserInfoEventSource extends SourceFunction[Event] {
  override def run(ctx: SourceContext[Event]) {
    val eventMap1 = JSONUtil.deserialize[util.HashMap[String, Any]](EventFixture.MODIFIED_EVENT)
    val eventMap2 = JSONUtil.deserialize[util.HashMap[String, Any]](EventFixture.WHEN_VALUES_ARE_EMPTY_STRING)
    val eventMap3 = JSONUtil.deserialize[util.HashMap[String, Any]](EventFixture.WHEN_VALUES_ARE_NULL)
    val eventMap4 = JSONUtil.deserialize[util.HashMap[String, Any]](EventFixture.NO_DATA)
    val eventMap5 = JSONUtil.deserialize[util.HashMap[String, Any]](EventFixture.USERLOCATION_MISSING)
    val eventMap6 = JSONUtil.deserialize[util.HashMap[String, Any]](EventFixture.ROOTORG_MISSING)
    val eventMap7 = JSONUtil.deserialize[util.HashMap[String, Any]](EventFixture.WHEN_SUBTYPE_HAVING_DUPLICATE_ENTRIES)
    ctx.collect(new Event(eventMap1))
    ctx.collect(new Event(eventMap2))
    ctx.collect(new Event(eventMap3))
    ctx.collect(new Event(eventMap4))
    ctx.collect(new Event(eventMap5))
    ctx.collect(new Event(eventMap6))
    ctx.collect(new Event(eventMap7))
  }

  override def cancel() = {}
}

