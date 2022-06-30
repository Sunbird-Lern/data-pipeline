package org.sunbird.job.merge.user.courses.spec

import com.typesafe.config.{Config, ConfigFactory}
import org.cassandraunit.CQLDataLoader
import org.cassandraunit.dataset.cql.FileCQLDataSet
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import org.mockito.Mockito
import org.sunbird.job.Metrics
import org.sunbird.job.merge.user.courses.domain.Event
import org.sunbird.job.merge.user.courses.fixture.EventFixture
import org.sunbird.job.merge.user.courses.task.MergeUserCoursesConfig
import org.sunbird.job.merge.user.courses.util.{Commons, MergeOperations}
import org.sunbird.job.util.{CassandraUtil, HttpUtil, JSONUtil, ScalaJsonUtil}
import org.sunbird.spec.{BaseTestSpec}

import java.util
import java.util.Map

class MergeOperationsTest extends BaseTestSpec{
  var cassandraUtil: CassandraUtil = _
  val config: Config = ConfigFactory.load("test.conf")
  lazy val jobConfig: MergeUserCoursesConfig = new MergeUserCoursesConfig(config)
  val httpUtil: HttpUtil = new HttpUtil
  val mockMetrics = mock[Metrics](Mockito.withSettings().serializable())


  override protected def beforeAll(): Unit = {
    super.beforeAll()
    EmbeddedCassandraServerHelper.startEmbeddedCassandra(80000L)
    cassandraUtil = new CassandraUtil(jobConfig.dbHost, jobConfig.dbPort)
    val session = cassandraUtil.session
    session.execute(s"DROP KEYSPACE IF EXISTS ${jobConfig.dbKeyspace}")
    val dataLoader = new CQLDataLoader(session)
    dataLoader.load(new FileCQLDataSet(getClass.getResource("/test.cql").getPath, true, true))

  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    try {
      EmbeddedCassandraServerHelper.cleanEmbeddedCassandra()
    } catch {
      case ex: Exception => ex.printStackTrace()
    }
  }

  "merge user enrolment" should " have merge records size greater than initial records size" in {
    val event = new Event(JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.EVENT_1), 0, 0)
    val commons: Commons = new Commons(jobConfig, cassandraUtil)
    val initialDocs = commons.readAsListOfMap(jobConfig.dbKeyspace, jobConfig.userEnrolmentsTable,new util.HashMap[String, AnyRef])
    new MergeOperations(jobConfig, cassandraUtil).mergeEnrolments(event.fromAccountId, event.toAccountId)
    val mergedDocs = commons.readAsListOfMap(jobConfig.dbKeyspace, jobConfig.userEnrolmentsTable,new util.HashMap[String, AnyRef])
    assert(initialDocs.size() < mergedDocs.size())
  }

  "merge user content consumption with matched content" should " have merged record progress & viewcount greater than intial value" in {
    val event = new Event(JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.EVENT_1), 0, 0)
    val commons: Commons = new Commons(jobConfig, cassandraUtil)
    val key = new util.HashMap[String, AnyRef]
    key.put(jobConfig.userId, event.toAccountId)
    val initialDocs = commons.readAsListOfMap(jobConfig.dbKeyspace, jobConfig.contentConsumptionTable,key)
    new MergeOperations(jobConfig, cassandraUtil).mergeContentConsumption(event.fromAccountId, event.toAccountId)
    key.put(jobConfig.userId, event.toAccountId)
    val mergedDocs = commons.readAsListOfMap(jobConfig.dbKeyspace, jobConfig.contentConsumptionTable,key)

    assert(mergedDocs.get(0).get("progress").asInstanceOf[Int] > initialDocs.get(0).get("progress").asInstanceOf[Int])
    assert(mergedDocs.get(0).get("viewcount").asInstanceOf[Int] > initialDocs.get(0).get("viewcount").asInstanceOf[Int])
  }

  "merge user content consumption without any matched content" should " have merged records than intial record" in {
    val event = new Event(JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.EVENT_2), 0, 0)
    val commons: Commons = new Commons(jobConfig, cassandraUtil)
    val key = new util.HashMap[String, AnyRef]
    key.put(jobConfig.userId, event.toAccountId)
    val initialDocs = commons.readAsListOfMap(jobConfig.dbKeyspace, jobConfig.contentConsumptionTable,key)
    new MergeOperations(jobConfig, cassandraUtil).mergeContentConsumption(event.fromAccountId, event.toAccountId)
    val mergedDocs = commons.readAsListOfMap(jobConfig.dbKeyspace, jobConfig.contentConsumptionTable,key)
    assert(initialDocs.size() < mergedDocs.size())

  }

  "merge user activity aggrs" should " have merged record with max value of completedCount in toAccount" in {
    val event = new Event(JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.EVENT_1), 0, 0)
    val commons: Commons = new Commons(jobConfig, cassandraUtil)
    val key = new util.HashMap[String, AnyRef]
    key.put(jobConfig.activity_type, "Course")
    key.put(jobConfig.user_id, event.toAccountId)
    key.put(jobConfig.activity_id, "do_11309999837886054415")

    val initialDocs = commons.readAsListOfMap(jobConfig.dbKeyspace, jobConfig.userActivityAggTable,key)
    new MergeOperations(jobConfig, cassandraUtil).mergeUserActivityAggregates(event.fromAccountId, event.toAccountId)

    val mergedDocs = commons.readAsListOfMap(jobConfig.dbKeyspace, jobConfig.userActivityAggTable,key)

    assert(initialDocs.get(0).get("agg_last_updated").asInstanceOf[Map[String, AnyRef]].get("completedCount")==null)
    assert(mergedDocs.get(0).get("agg_last_updated").asInstanceOf[Map[String, AnyRef]].get("completedCount")!=null)
    assert(mergedDocs.get(0).get("aggregates").asInstanceOf[Map[String, AnyRef]].get("completedCount").asInstanceOf[Double]>initialDocs.get(0).get("aggregates").asInstanceOf[Map[String, AnyRef]].get("completedCount").asInstanceOf[Double])

  }

  "Generate Batch Enrollment Sync Events" should " have no exception " in {
    val event = new Event(JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.EVENT_1), 0, 0)
    noException should be thrownBy  new MergeOperations(jobConfig, cassandraUtil).generateBatchEnrollmentSyncEvents(event.toAccountId, null)(mockMetrics)

  }

}
