package org.sunbird.ownershiptransfer.spec

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration
import org.apache.flink.test.util.MiniClusterWithClientResource
import org.cassandraunit.CQLDataLoader
import org.cassandraunit.dataset.cql.FileCQLDataSet
import org.cassandraunit.utils.EmbeddedCassandraServerHelper
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito
import org.mockito.Mockito.{doNothing, when}
import org.sunbird.dp.core.job.FlinkKafkaConnector
import org.sunbird.dp.core.util.{CassandraUtil, ElasticSearchUtil, HTTPResponse, HttpUtil}
import org.sunbird.dp.{BaseMetricsReporter, BaseTestSpec}
import org.sunbird.job.ownershiptransfer.domain.Event
import org.sunbird.job.ownershiptransfer.task.{UserOwnershipTransferConfig, UserOwnershipTransferStreamTask}


class UserOwnershipTransferFunctionTestSpec extends BaseTestSpec {
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
  val jobConfig: UserOwnershipTransferConfig = new UserOwnershipTransferConfig(config)
  var mockHttpUtil: HttpUtil = mock[HttpUtil](Mockito.withSettings().serializable())
  var mockEsUtil: ElasticSearchUtil = mock[ElasticSearchUtil](Mockito.withSettings().serializable())
  var cassandraUtil: CassandraUtil = _

  val createdByRequestBody: String = s"""{
                       |    "request": {
                       |        "filters": {
                       |            "createdBy": "02c4e0dc-3e25-4f7d-b811-242c73e24a01",
                       |            "status": [0,1]
                       |        },
                       |        "fields": ["identifier", "createdFor","batchId","courseId","startDate","enrollmentType"]
                       |    }
                       |}""".stripMargin

  val createdBySearchResponse: String = s"""{
                                   |    "id": "api.course.batch.search",
                                   |    "ver": "v1",
                                   |    "ts": "2023-09-20 12:22:58:754+0000",
                                   |    "params": {
                                   |        "resmsgid": null,
                                   |        "msgid": "85acae51-ca75-456f-9d4a-662a24fe75f9",
                                   |        "err": null,
                                   |        "status": "success",
                                   |        "errmsg": null
                                   |    },
                                   |    "responseCode": "OK",
                                   |    "result": {
                                   |        "response": {
                                   |            "count": 1,
                                   |            "content": [
                                   |                {
                                   |                    "identifier": "01295417094601113627",
                                   |                    "createdFor": [
                                   |                        "01269878797503692810"
                                   |                    ],
                                   |                    "mentors": null,
                                   |                    "batchId": "01295417094601113627",
                                   |                    "enrollmentType": "invite-only",
                                   |                    "courseId": "do_2129195698820055041246",
                                   |                    "startDate": "2020-03-08"
                                   |                }
                                   |            ]
                                   |        }
                                   |    }
                                   |}""".stripMargin

  val mentorRequestBody: String = s"""{
                             |    "request": {
                             |        "filters": {
                             |            "mentors": ["02c4e0dc-3e25-4f7d-b811-242c73e24a01"],
                             |            "status": [0,1]
                             |        },
                             |        "fields": ["identifier", "createdFor","batchId","courseId","startDate","enrollmentType","mentors]
                             |    }
                             |}""".stripMargin

  val mentorSearchResponse: String = s"""{
                                |    "id": "api.course.batch.search",
                                |    "ver": "v1",
                                |    "ts": "2023-09-20 12:24:51:291+0000",
                                |    "params": {
                                |        "resmsgid": null,
                                |        "msgid": "abfc6a1e-a372-4c1c-bbbf-a53d3792b28d",
                                |        "err": null,
                                |        "status": "success",
                                |        "errmsg": null
                                |    },
                                |    "responseCode": "OK",
                                |    "result": {
                                |        "response": {
                                |            "count": 42,
                                |            "content": [
                                |                {
                                |                    "identifier": "01289599909937152015",
                                |                    "createdFor": [
                                |                        "01269878797503692810"
                                |                    ],
                                |                    "mentors": [
                                |                        "02c4e0dc-3e25-4f7d-b811-242c73e24a01"
                                |                    ],
                                |                    "batchId": "01289599909937152015",
                                |                    "enrollmentType": "open",
                                |                    "courseId": "do_21289599347869286411276",
                                |                    "startDate": "2019-11-20"
                                |                },
                                |                {
                                |                    "identifier": "0129555223509401604",
                                |                    "createdFor": [
                                |                        "01269878797503692810"
                                |                    ],
                                |                    "mentors": [
                                |                        "02c4e0dc-3e25-4f7d-b811-242c73e24a01",
                                |                        "02c4e0dc-3e25-4f7d-b811-242c73e24a01"
                                |                    ],
                                |                    "batchId": "0129555223509401604",
                                |                    "enrollmentType": "open",
                                |                    "courseId": "do_2129555111659520001388",
                                |                    "startDate": "2020-02-12"
                                |                }
                                |            ]
                                |        }
                                |    }
                                |}""".stripMargin

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
    // TODO:
//    when(mockKafkaUtil.kafkaEventSource[Event](jobConfig.inputTopic)).thenReturn(new UserOwnershipTransferEventSource)
//    when(mockKafkaUtil.kafkaStringSink(jobConfig.inputTopic)).thenReturn(new GenerateUserOwnershipTransferSink)
  }

  ignore should "validate metrics " in {
    doNothing().when(mockEsUtil).updateDocument("01295417094601113627","doc")
    doNothing().when(mockEsUtil).updateDocument("01289599909937152015","doc")
    doNothing().when(mockEsUtil).updateDocument("0129555223509401604","doc")
    when(mockHttpUtil.get(jobConfig.userOrgServiceBasePath + jobConfig.userReadApi +"/02c4e0dc-3e25-4f7d-b811-242c73e24a01?identifier,rootOrgId")).thenReturn(HTTPResponse(200, """{"id": "api.user.read.4cd4c690-eab6-4938-855a-447c7b1b8ea9","ver": "v5","ts": "2023-09-05 14:07:47:872+0000","params": {"resmsgid": "1281c745-830c-421c-8245-dd5b2b795842","msgid": "1281c745-830c-421c-8245-dd5b2b795842","err": null,"status": "SUCCESS","errmsg": null},"responseCode": "OK","result": {"response": {"identifier": "02c4e0dc-3e25-4f7d-b811-242c73e24a01","rootOrgId": "01309282781705830427"}}}"""))
    when(mockHttpUtil.get(jobConfig.userOrgServiceBasePath + jobConfig.userReadApi +"/fca2925f-1eee-4654-9177-fece3fd6afc9?identifier,rootOrgId")).thenReturn(HTTPResponse(200, """{"id": "api.user.read.4cd4c690-eab6-4938-855a-447c7b1b8ea9","ver": "v5","ts": "2023-09-05 14:07:47:872+0000","params": {"resmsgid": "1281c745-830c-421c-8245-dd5b2b795842","msgid": "1281c745-830c-421c-8245-dd5b2b795842","err": null,"status": "SUCCESS","errmsg": null},"responseCode": "OK","result": {"response": {"identifier": "fca2925f-1eee-4654-9177-fece3fd6afc9","rootOrgId": "01309282781705830427"}}}"""))
    when(mockHttpUtil.post(jobConfig.lmsServiceBasePath + jobConfig.batchSearchApi, createdByRequestBody)).thenReturn(HTTPResponse(200, createdBySearchResponse))
    when(mockHttpUtil.post(jobConfig.lmsServiceBasePath + jobConfig.batchSearchApi, mentorRequestBody)).thenReturn(HTTPResponse(200, mentorSearchResponse))
    when(mockEsUtil.getDocumentAsString(anyString())).thenReturn("01295417094601113627")
    when(mockEsUtil.getDocumentAsString(anyString())).thenReturn("01289599909937152015")
    when(mockEsUtil.getDocumentAsString(anyString())).thenReturn("0129555223509401604")
    initialize()
    new UserOwnershipTransferStreamTask(jobConfig, mockHttpUtil, mockEsUtil, mockKafkaUtil).process()
    BaseMetricsReporter.gaugeMetrics(s"${jobConfig.jobName}.${jobConfig.totalEventsCount}").getValue() should be(1)
  }

}
