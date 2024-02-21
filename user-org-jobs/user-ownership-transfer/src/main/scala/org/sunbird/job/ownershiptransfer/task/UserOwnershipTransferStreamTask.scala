package org.sunbird.job.ownershiptransfer.task

import java.io.File
import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.sunbird.job.connector.FlinkKafkaConnector
import org.sunbird.job.ownershiptransfer.domain.Event
import org.sunbird.job.ownershiptransfer.functions.UserOwnershipTransferFunction
import org.sunbird.job.util.{ElasticSearchUtil, FlinkUtil, HttpUtil}

class UserOwnershipTransferStreamTask(config: UserOwnershipTransferConfig, httpUtil: HttpUtil, esUtil: ElasticSearchUtil, kafkaConnector: FlinkKafkaConnector) {

  def process(): Unit = {

    implicit val env: StreamExecutionEnvironment = FlinkUtil.getExecutionContext(config)
    implicit val mapTypeInfo: TypeInformation[Event] = TypeExtractor.getForClass(classOf[Event])
    val source = kafkaConnector.kafkaEventSource[Event](config.inputTopic)
    env.addSource(source).name(config.userOwnershipTransferConsumer).uid(config.userOwnershipTransferConsumer).
      setParallelism(config.userOwnershipTransferParallelism).rebalance
      .process(new UserOwnershipTransferFunction(config, httpUtil, esUtil))
      .name(config.userOwnershipTransferFunction).uid(config.userOwnershipTransferFunction)
    env.execute(config.jobName)
  }

}

// $COVERAGE-OFF$ Disabling scoverage as the below code can only be invoked within flink cluster
object UserOwnershipTransferStreamTask {

  def main(args: Array[String]): Unit = {
    val configFilePath = Option(ParameterTool.fromArgs(args).get("config.file.path"))
    val config = configFilePath.map {
      path => ConfigFactory.parseFile(new File(path)).resolve()
    }.getOrElse(ConfigFactory.load("user-ownership-transfer.conf").withFallback(ConfigFactory.systemEnvironment()))
    val userOwnershipTransferConfig = new UserOwnershipTransferConfig(config)
    val httpUtil = new HttpUtil
    val esUtil: ElasticSearchUtil = new ElasticSearchUtil(userOwnershipTransferConfig.esConnection, userOwnershipTransferConfig.searchIndex, userOwnershipTransferConfig.courseBatchIndexType)
    val kafkaUtil = new FlinkKafkaConnector(userOwnershipTransferConfig)
    val task = new UserOwnershipTransferStreamTask(userOwnershipTransferConfig, httpUtil, esUtil, kafkaUtil)
    task.process()
  }
}

// $COVERAGE-ON$
