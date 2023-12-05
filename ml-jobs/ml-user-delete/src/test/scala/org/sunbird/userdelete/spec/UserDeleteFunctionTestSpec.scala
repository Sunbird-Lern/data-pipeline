package org.sunbird.userdelete.spec

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
import org.mongodb.scala.model.Filters
import org.sunbird.job.connector.FlinkKafkaConnector
import org.sunbird.job.userdelete.domain.Event
import org.sunbird.job.userdelete.task.{UserDeleteConfig, UserDeleteStreamTask}
import org.sunbird.job.util.MongoUtil
import org.sunbird.spec.BaseTestSpec
import org.sunbird.userdelete.fixture.LoadMongoData

class UserDeleteFunctionTestSpec extends BaseTestSpec {

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
  val jobConfig: UserDeleteConfig = new UserDeleteConfig(config)
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
      .thenReturn(new UserDeleteEventSource)
    when(mockKafkaUtil.kafkaStringSink(jobConfig.inputTopic)).thenReturn(new GenerateUserDeleteSink)
  }

  val mongoCollection = new MongoUtil("localhost", port, "ml-service")

  def loadTestData() = {
    mongoCollection.insertOne("observations", LoadMongoData.Data1)
    mongoCollection.insertOne("observations", LoadMongoData.Data2)
    mongoCollection.insertOne("surveySubmissions", LoadMongoData.Data1)
    mongoCollection.insertOne("observationSubmissions", LoadMongoData.Data1)
    mongoCollection.insertOne("projects", LoadMongoData.Data3)
    mongoCollection.insertOne("programUsers", LoadMongoData.Data3)
    mongoCollection.insertOne("programUsers", LoadMongoData.Data4)
  }


  "UserDeleteStreamTask" should "execute successfully " in {
    initialize()

    new UserDeleteStreamTask(jobConfig, mockKafkaUtil).process()
    val filter = Filters.equal("createdBy", "5deed393-6e04-449a-b98d-7f0fbf88f22e")
    val obsData = mongoCollection.find("observations", filter)

    obsData.forEach { _ =>
      val updatedUserProfileOption = if (obsData.iterator().hasNext) obsData.iterator().next().get("userProfile") else None
      updatedUserProfileOption match {
        case Some(updatedUserProfile) =>
          val firstName = updatedUserProfile.asDocument().getString("firstName")
          firstName.getValue shouldBe "Deleted User"
        case None =>
          fail("userProfile field not found or is null")
      }
    }
  }

}
