package org.sunbird.job.ownershiptransfer.task

import java.io.File
import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.sunbird.job.connector.FlinkKafkaConnector
import org.sunbird.job.ownershiptransfer.domain.Event
import org.sunbird.job.ownershiptransfer.functions.UserOwnershipTransferFunction
import org.sunbird.job.util.{FlinkUtil, HttpUtil}

class UserOwnershipTransferStreamTask(config: UserOwnershipTransferConfig, httpUtil: HttpUtil, kafkaConnector: FlinkKafkaConnector) {

  implicit val mapTypeInfo: TypeInformation[Event] = TypeExtractor.getForClass(classOf[Event])

  def process(): Unit = {
    implicit val env: StreamExecutionEnvironment = FlinkUtil.getExecutionContext(config)
    val source = kafkaConnector.kafkaEventSource[Event](config.inputTopic)
    val inputStream = env.fromSource(source, WatermarkStrategy.noWatermarks(), config.userOwnershipTransferConsumer).uid(config.userOwnershipTransferConsumer).
      setParallelism(config.userOwnershipTransferParallelism).rebalance
    buildGraph(inputStream)
    env.execute(config.jobName)
  }

  def processForTest(env: StreamExecutionEnvironment, inputStream: DataStream[Event]): Unit = {
    buildGraph(inputStream)
    env.execute(config.jobName)
  }

  private def buildGraph(inputStream: DataStream[Event]): Unit = {
    inputStream
      .process(new UserOwnershipTransferFunction(config, httpUtil))
      .name(config.userOwnershipTransferFunction).uid(config.userOwnershipTransferFunction)
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
    val kafkaUtil = new FlinkKafkaConnector(userOwnershipTransferConfig)
    val task = new UserOwnershipTransferStreamTask(userOwnershipTransferConfig, httpUtil, kafkaUtil)
    task.process()
  }
}

// $COVERAGE-ON$
