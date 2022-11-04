package org.sunbird.job.merge.user.courses.spec

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.cassandraunit.CQLDataLoader
import org.cassandraunit.dataset.cql.FileCQLDataSet
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import org.mockito.Mockito
import org.sunbird.job.Metrics
import org.sunbird.job.exception.InvalidEventException
import org.sunbird.job.merge.user.courses.domain.Event
import org.sunbird.job.merge.user.courses.fixture.EventFixture
import org.sunbird.job.merge.user.courses.functions.MergeUserCourseFunction
import org.sunbird.job.merge.user.courses.task.MergeUserCoursesConfig
import org.sunbird.job.util.{CassandraUtil, JSONUtil}
import org.sunbird.spec.BaseTestSpec

import java.util.HashMap

class MergeUserCourseFuncTest extends BaseTestSpec{
  @transient var cassandraUtil: CassandraUtil = _
  val config: Config = ConfigFactory.load("test.conf")
  lazy val jobConfig: MergeUserCoursesConfig = new MergeUserCoursesConfig(config)
  val mockMetrics = mock[Metrics](Mockito.withSettings().serializable())
  implicit val stringTypeInfo: TypeInformation[String] = TypeExtractor.getForClass(classOf[String])
  val context: ProcessFunction[Event, String]#Context = mock[ProcessFunction[Event, String]#Context]


  override protected def beforeAll(): Unit = {
    super.beforeAll()
    EmbeddedCassandraServerHelper.startEmbeddedCassandra(80000L)
    cassandraUtil = new CassandraUtil(jobConfig.dbHost, jobConfig.dbPort, jobConfig.isMultiDCEnabled)
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


  "Merge user course process with valid event " should " have no exception " in {
    val event = new Event(JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.EVENT_1), 0, 0)
    val mergeUserCourseFunc = new MergeUserCourseFunction(jobConfig)(stringTypeInfo, cassandraUtil)
    noException should be thrownBy mergeUserCourseFunc.processElement(event,context, mockMetrics)
  }

  "Merge user course process with invalid event" should " have no exception " in {
    val event = new Event(JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.EVENT_3), 0, 0)
    val mergeUserCourseFunc = new MergeUserCourseFunction(jobConfig)(stringTypeInfo, cassandraUtil)
    noException should be thrownBy mergeUserCourseFunc.processElement(event,context, mockMetrics)
  }

  "Merge user course process with invalid connection" should " have exception " in {
    val event = new Event(JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.EVENT_1), 0, 0)
    val mergeUserCourseFunc = new MergeUserCourseFunction(jobConfig)(stringTypeInfo, null)
    an [InvalidEventException] should be thrownBy mergeUserCourseFunc.processElement(event,context, mockMetrics)
  }

  "Merge user course generate Failed Events" should " error message found " in {
    val event = new Event(JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.EVENT_3), 0, 0)
    val mergeUserCourseFunc = new MergeUserCourseFunction(jobConfig)(stringTypeInfo, cassandraUtil)
    val objects =  mergeUserCourseFunc.generateFailedEvents("dummyJobName", "event id not present", context)

    assert(objects.get("failInfo").asInstanceOf[HashMap[String, AnyRef]].get("error").eq("event id not present"))
  }
}

