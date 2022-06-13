package org.sunbird.job.merge.user.courses.spec
import com.datastax.driver.core.Row
import com.typesafe.config.{Config, ConfigFactory}
import org.cassandraunit.CQLDataLoader
import org.cassandraunit.dataset.cql.FileCQLDataSet
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import org.sunbird.job.merge.user.courses.domain.Event
import org.sunbird.job.merge.user.courses.fixture.EventFixture
import org.sunbird.job.merge.user.courses.task.MergeUserCoursesConfig
import org.sunbird.job.merge.user.courses.util.{Commons, MergeOperations}
import org.sunbird.job.util.{CassandraUtil, HttpUtil, JSONUtil, ScalaJsonUtil}
import org.sunbird.spec.BaseTestSpec

import java.util
import java.util.List

class MergeOperationsTest extends BaseTestSpec{
  var cassandraUtil: CassandraUtil = _
  val config: Config = ConfigFactory.load("test.conf")
  lazy val jobConfig: MergeUserCoursesConfig = new MergeUserCoursesConfig(config)
  val httpUtil: HttpUtil = new HttpUtil
  //val commons: Commons = new Commons(jobConfig, cassandraUtil)
  //val mergeOperations: MergeOperations = new MergeOperations(jobConfig, cassandraUtil)

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    System.out.print("====starting cs====")
    EmbeddedCassandraServerHelper.startEmbeddedCassandra(80000L)
    System.out.print("====end cs====")
    System.out.print("====jobConfig.dbHost===="+jobConfig.dbHost)
    System.out.print("====jobConfig.dbHost===="+jobConfig.dbPort)

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
    System.out.println("initialDocs doc =>"+ScalaJsonUtil.serialize(initialDocs))
    new MergeOperations(jobConfig, cassandraUtil).mergeEnrolments(event.fromAccountId, event.toAccountId)
    val mergedDocs = commons.readAsListOfMap(jobConfig.dbKeyspace, jobConfig.userEnrolmentsTable,new util.HashMap[String, AnyRef])
    System.out.println("final doc =>"+ScalaJsonUtil.serialize(mergedDocs))
    assert(initialDocs.size() < mergedDocs.size())

  }

  "merge user content consumption with matched content" should " have merged record progress & viewcount greater than intial value" in {
    val event = new Event(JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.EVENT_1), 0, 0)
    val commons: Commons = new Commons(jobConfig, cassandraUtil)
    val key = new util.HashMap[String, AnyRef]
    key.put(jobConfig.userId, event.toAccountId)
    val initialDocs = commons.readAsListOfMap(jobConfig.dbKeyspace, jobConfig.contentConsumptionTable,key)
    System.out.println("initialDocs doc =>"+ScalaJsonUtil.serialize(initialDocs))
    new MergeOperations(jobConfig, cassandraUtil).mergeContentConsumption(event.fromAccountId, event.toAccountId)
    key.put(jobConfig.userId, event.toAccountId)
    val mergedDocs = commons.readAsListOfMap(jobConfig.dbKeyspace, jobConfig.contentConsumptionTable,key)
    System.out.println("final doc =>"+ScalaJsonUtil.serialize(mergedDocs))

    assert(mergedDocs.get(0).get("progress").asInstanceOf[Int] > initialDocs.get(0).get("progress").asInstanceOf[Int])
    assert(mergedDocs.get(0).get("viewcount").asInstanceOf[Int] > initialDocs.get(0).get("viewcount").asInstanceOf[Int])
  }
}
