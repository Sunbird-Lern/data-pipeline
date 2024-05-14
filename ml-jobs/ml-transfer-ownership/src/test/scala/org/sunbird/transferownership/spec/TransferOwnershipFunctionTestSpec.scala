package org.sunbird.transferownership.spec

import com.typesafe.config.{Config, ConfigFactory}
import de.flapdoodle.embed.mongo.config.{MongodConfigBuilder, Net}
import de.flapdoodle.embed.mongo.distribution.Version
import de.flapdoodle.embed.mongo.{MongodExecutable, MongodProcess, MongodStarter}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration
import org.apache.flink.test.util.MiniClusterWithClientResource
import org.mockito.Mockito
import org.mockito.Mockito.when
import org.sunbird.job.connector.FlinkKafkaConnector
import org.sunbird.job.transferownership.domain.Event
import org.sunbird.job.transferownership.task.{TransferOwnershipConfig, TransferOwnershipStreamTask}
import org.sunbird.job.util.MongoUtil
import org.sunbird.spec.BaseTestSpec
import org.sunbird.transferownership.fixture.LoadMongoData

class TransferOwnershipFunctionTestSpec extends BaseTestSpec {

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
  val jobConfig: TransferOwnershipConfig = new TransferOwnershipConfig(config)
  var mongoUtil: MongoUtil = _

  private val starter: MongodStarter = MongodStarter.getDefaultInstance
  private val port: Int = 27017
  private var mongodExecutable: MongodExecutable = _
  private var mongodProcess: MongodProcess = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    //Embedded MongoDB connection
    mongodExecutable = starter.prepare(new MongodConfigBuilder()
      .version(Version.Main.V4_0)
      .net(new Net(port, false))
      .build())
    mongodProcess = mongodExecutable.start()
    loadTestData()
    flinkCluster.before()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    flinkCluster.after()
  }

  def initialize() {
    when(mockKafkaUtil.kafkaJobRequestSource[Event](jobConfig.inputTopic))
      .thenReturn(new TransferOwnershipEventSource)
    when(mockKafkaUtil.kafkaStringSink(jobConfig.inputTopic)).thenReturn(new GenerateTransferOwnershipSink)
  }

  val mongoCollection = new MongoUtil("localhost", port, "ml-service")

  def loadTestData() = {
    mongoCollection.insertOne("solutions", LoadMongoData.loadSolutionsData1)
    mongoCollection.insertOne("solutions", LoadMongoData.loadSolutionsData2)
    mongoCollection.insertOne("solutions", LoadMongoData.loadSolutionsData3)
    mongoCollection.insertOne("programs", LoadMongoData.loadProgramData1)
    mongoCollection.insertOne("programs", LoadMongoData.loadProgramData2)
    mongoCollection.insertOne("programs", LoadMongoData.loadProgramData3)
    mongoCollection.insertOne("programs", LoadMongoData.loadProgramData4)
    mongoCollection.insertOne("userExtension", LoadMongoData.loadUserExtensionData)
    mongoCollection.insertOne("userRoles", LoadMongoData.loadUserRolesData)
  }

  "TransferOwnership for one-to-one solution asset " should "execute successfully " in {
    initialize()
    new TransferOwnershipStreamTask(jobConfig, mockKafkaUtil).process()
  }

}