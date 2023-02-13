package org.sunbird.job.spec

import java.util

import com.datastax.driver.core.Row
import com.google.gson.Gson
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
import org.mockito.Mockito._
import org.sunbird.job.cache.RedisConnect
import org.sunbird.job.connector.FlinkKafkaConnector
import org.sunbird.job.fixture.EventFixture
import org.sunbird.job.aggregate.task.{ActivityAggregateUpdaterConfig, ActivityAggregateUpdaterStreamTask}
import org.sunbird.job.util.{CassandraUtil, HTTPResponse, HttpUtil}
import org.sunbird.spec.{BaseMetricsReporter, BaseTestSpec}
import redis.clients.jedis.Jedis
import redis.embedded.RedisServer

import scala.collection.mutable
import scala.collection.JavaConverters._

class ActivityAggregateUpdaterTaskTestSpec extends BaseTestSpec {

  implicit val mapTypeInfo: TypeInformation[util.Map[String, AnyRef]] = TypeExtractor.getForClass(classOf[util.Map[String, AnyRef]])

  val flinkCluster = new MiniClusterWithClientResource(new MiniClusterResourceConfiguration.Builder()
    .setConfiguration(testConfiguration())
    .setNumberSlotsPerTaskManager(1)
    .setNumberTaskManagers(1)
    .build)

  var redisServer: RedisServer = _
  redisServer = new RedisServer(6340)
  redisServer.start()
  var jedis: Jedis = _
  val mockKafkaUtil: FlinkKafkaConnector = mock[FlinkKafkaConnector](Mockito.withSettings().serializable())
  val gson = new Gson()
  val config: Config = ConfigFactory.load("test.conf")
  val courseAggregatorConfig: ActivityAggregateUpdaterConfig = new ActivityAggregateUpdaterConfig(config)
  val mockHttpUtil: HttpUtil = mock[HttpUtil](Mockito.withSettings().serializable())

  var cassandraUtil: CassandraUtil = _

  val requestBody = s"""{
                       |    "request": {
                       |        "filters": {
                       |            "objectType": "Collection",
                       |            "identifier": "course001",
                       |            "status": ["Live", "Unlisted", "Retired"]
                       |        },
                       |        "fields": ["status"]
                       |    }
                       |}""".stripMargin

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    val redisConnect = new RedisConnect(courseAggregatorConfig)
    jedis = redisConnect.getConnection(courseAggregatorConfig.nodeStore)
    EmbeddedCassandraServerHelper.startEmbeddedCassandra(80000L)
    cassandraUtil = new CassandraUtil(courseAggregatorConfig.dbHost, courseAggregatorConfig.dbPort, courseAggregatorConfig.isMultiDCEnabled)
    val session = cassandraUtil.session

    val dataLoader = new CQLDataLoader(session)
    dataLoader.load(new FileCQLDataSet(getClass.getResource("/test.cql").getPath, true, true))
    // Clear the metrics
    testCassandraUtil(cassandraUtil)
    BaseMetricsReporter.gaugeMetrics.clear()
    jedis.flushDB()
    flinkCluster.before()
    updateRedis(jedis, EventFixture.CASE_1.asInstanceOf[Map[String, AnyRef]])
    updateRedis(jedis, EventFixture.CASE_2.asInstanceOf[Map[String, AnyRef]])
    updateRedis(jedis, EventFixture.CASE_3.asInstanceOf[Map[String, AnyRef]])
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    try {
      EmbeddedCassandraServerHelper.cleanEmbeddedCassandra()
      redisServer.stop()
    } catch {
      case ex: Exception => {
      }
    }
    flinkCluster.after()
  }

  def initialize() {
    when(mockKafkaUtil.kafkaMapSource(courseAggregatorConfig.kafkaInputTopic)).thenReturn(new CompleteContentConsumptionMapSource)
    when(mockKafkaUtil.kafkaStringSink(courseAggregatorConfig.kafkaAuditEventTopic)).thenReturn(new AuditEventSink)
    when(mockKafkaUtil.kafkaStringSink(courseAggregatorConfig.kafkaFailedEventTopic)).thenReturn(new FailedEventSink)
    when(mockKafkaUtil.kafkaStringSink(courseAggregatorConfig.kafkaCertIssueTopic)).thenReturn(new CertificateIssuedEventsSink)
  }

  "Activity Aggregator " should " compute and update enrolment as completed when all the content consumption data processed" in {
    initialize()
    new ActivityAggregateUpdaterStreamTask(courseAggregatorConfig, mockKafkaUtil, new HttpUtil).process()
    BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.totalEventCount}").getValue() should be(3)
    BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.batchEnrolmentUpdateEventCount}").getValue() should be(3)
    BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.dbReadCount}").getValue() should be(3)
    BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.dbUpdateCount}").getValue() should be(6)
    //BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.cacheHitCount}").getValue() should be(18)
    //BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.cacheHitCount}").getValue() should be(27)
    BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.cacheHitCount}").getValue() should be(30)
    BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.processedEnrolmentCount}").getValue() should be(3)
    //BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.processedEnrolmentCount}").getValue() should be(3)
    BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.enrolmentCompleteCount}").getValue() should be(0)
    //BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.enrolmentCompleteCount}").getValue() should be(1)
    BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.failedEventCount}").getValue() should be(0)
    BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.skipEventsCount}").getValue() should be(0)
    //BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.cacheMissCount}").getValue() should be(9)
    BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.cacheMissCount}").getValue() should be(6)
    //BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.cacheMissCount}").getValue() should be(0)

    //AuditEventSink.values.size() should be(4)
    AuditEventSink.values.size() should be(3)
    AuditEventSink.values.forEach(event => {
      println("AUDIT_TELEMETRY_EVENT: " + event)
    })
    jedis.select(courseAggregatorConfig.deDupStore)
    val deDupKeys = jedis.keys("*")
    println("DeDup Keys:" + deDupKeys)
    deDupKeys.size() should be (3)
    jedis.select(courseAggregatorConfig.nodeStore)
  }

  "Activity Aggregator " should " throw exception when the cache not available for root collection" in {
    jedis.select(courseAggregatorConfig.nodeStore)
    jedis.flushAll()
    when(mockHttpUtil.post(courseAggregatorConfig.searchAPIURL, requestBody)).thenReturn(HTTPResponse(200, """{"id":"api.v1.search","ver":"1.0","ts":"2020-12-16T12:37:40.283Z","params":{"resmsgid":"7c4cf0b0-3f9b-11eb-9b0c-abcfbdf41bc3","msgid":"7c4b1bf0-3f9b-11eb-9b0c-abcfbdf41bc3","status":"successful","err":null,"errmsg":null},"responseCode":"OK","result":{"count":1,"content":[{"identifier":"course001","objectType":"Content","status":"Live"}]}}"""))
    initialize()

    val activityAggTask = new ActivityAggregateUpdaterStreamTask(courseAggregatorConfig, mockKafkaUtil, mockHttpUtil)
    the [Exception] thrownBy {
      activityAggTask.process()
    } should have message "Job execution failed."

    // De-dup should not save the keys for which the processing failed.
    // This will help in processing the same data after restart.
    jedis.select(courseAggregatorConfig.deDupStore)
    jedis.keys("*").size() should be (0)
    jedis.select(courseAggregatorConfig.nodeStore)

    FailedEventSink.values.forEach(event => {
      println("FAILED_EVENT_DATA: " + event)
    })
    //    failedEventSink.values.size() should be (2)
  }

  ignore should " skip the retired collection consumption events" in {
    jedis.select(courseAggregatorConfig.nodeStore)
    jedis.flushAll()
    reset(mockHttpUtil)
    when(mockHttpUtil.post(courseAggregatorConfig.searchAPIURL, requestBody)).thenReturn(HTTPResponse(200, """{"id":"api.v1.search","ver":"1.0","ts":"2020-12-16T12:37:40.283Z","params":{"resmsgid":"7c4cf0b0-3f9b-11eb-9b0c-abcfbdf41bc3","msgid":"7c4b1bf0-3f9b-11eb-9b0c-abcfbdf41bc3","status":"successful","err":null,"errmsg":null},"responseCode":"OK","result":{"count":1,"content":[{"identifier":"course001","objectType":"Content","status":"Retired"}]}}"""))
    initialize()

    val activityAggTask = new ActivityAggregateUpdaterStreamTask(courseAggregatorConfig, mockKafkaUtil, mockHttpUtil)
    activityAggTask.process()

    jedis.select(courseAggregatorConfig.deDupStore)
    jedis.keys("*").size() should be (0)
    jedis.select(courseAggregatorConfig.nodeStore)

    BaseMetricsReporter.gaugeMetrics(s"${courseAggregatorConfig.jobName}.${courseAggregatorConfig.retiredCCEventsCount}").getValue() should be(3)

  }

  def testCassandraUtil(cassandraUtil: CassandraUtil): Unit = {
    cassandraUtil.reconnect()
  }

  def updateRedis(jedis: Jedis, testData: Map[String, AnyRef]) {
    testData.get("cacheData").map(data => {
      data.asInstanceOf[List[Map[String, AnyRef]]].map(cacheData => {
        cacheData.map(x => {
          x._2.asInstanceOf[List[String]].foreach(d => {
            jedis.sadd(x._1, d)
          })
        })
      })
    })
  }

  def readFromCassandra(event: String): util.List[Row] = {
    val event1_primaryCols = getPrimaryCols(gson.fromJson(event, new util.LinkedHashMap[String, AnyRef]().getClass).asInstanceOf[util.Map[String, AnyRef]].asScala.asJava)
    val query = s"select * from sunbird_courses.user_activity_agg where context_id='cb:${event1_primaryCols.get("batchid").get}' and user_id='${event1_primaryCols.get("userid").get}' ALLOW FILTERING;"
    cassandraUtil.find(query)
  }

  def readFromContentConsumptionTable(event: String): util.List[Row] = {
    val event1_primaryCols = getPrimaryCols(gson.fromJson(event, new util.LinkedHashMap[String, AnyRef]().getClass).asInstanceOf[util.Map[String, AnyRef]].asScala.asJava)
    val query = s"select * from sunbird_courses.user_content_consumption where userid='${event1_primaryCols.get("userid").get}' and batchid='${event1_primaryCols.get("batchid").get}' and courseid='${event1_primaryCols.get("courseid").get}' ALLOW FILTERING;"
    cassandraUtil.find(query)
  }


  def getPrimaryCols(event: util.Map[String, AnyRef]): mutable.Map[String, String] = {
    val eventData = event.get("edata").asInstanceOf[util.Map[String, AnyRef]]
    val primaryFields = List("userid", "courseid", "batchid")
    eventData.asScala.map(v => (v._1.toLowerCase, v._2)).filter(x => primaryFields.contains(x._1)).asInstanceOf[mutable.Map[String, String]]
  }
}

private class CompleteContentConsumptionMapSource extends SourceFunction[util.Map[String, AnyRef]] {

  override def run(ctx: SourceContext[util.Map[String, AnyRef]]) {
    ctx.collect(jsonToMap(EventFixture.CC_EVENT1))
    ctx.collect(jsonToMap(EventFixture.CC_EVENT2))
    ctx.collect(jsonToMap(EventFixture.CC_EVENT3))
  }

  override def cancel() = {}

  def jsonToMap(json: String): util.Map[String, AnyRef] = {
    val gson = new Gson()
    gson.fromJson(json, new util.LinkedHashMap[String, AnyRef]().getClass).asInstanceOf[util.Map[String, AnyRef]]
  }

}
