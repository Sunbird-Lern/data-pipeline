package org.sunbird.job.transferownership.task

import java.io.File
import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.sunbird.job.connector.FlinkKafkaConnector
import org.sunbird.job.transferownership.domain.Event
import org.sunbird.job.transferownership.functions.TransferOwnershipFunction
import org.sunbird.job.util.FlinkUtil

class TransferOwnershipStreamTask(config: TransferOwnershipConfig, kafkaConnector: FlinkKafkaConnector){

  implicit val mapTypeInfo: TypeInformation[Event] = TypeExtractor.getForClass(classOf[Event])

  def process(): Unit = {
    implicit val env: StreamExecutionEnvironment = FlinkUtil.getExecutionContext(config)
    val source = kafkaConnector.kafkaJobRequestSource[Event](config.inputTopic)
    val inputStream = env.fromSource(source, WatermarkStrategy.noWatermarks(), config.mlTransferOwnershipConsumer)
      .uid(config.mlTransferOwnershipConsumer)
      .setParallelism(config.mlTransferOwnershipParallelism)
      .rebalance
    buildGraph(inputStream)
    env.execute(config.jobName)
  }

  def processForTest(env: StreamExecutionEnvironment, inputStream: DataStream[Event]): Unit = {
    buildGraph(inputStream)
    env.execute(config.jobName)
  }

  private def buildGraph(inputStream: DataStream[Event]): Unit = {
    inputStream
      .process(new TransferOwnershipFunction(config))
      .name(config.transferOwnershipFunction).uid(config.transferOwnershipFunction)
  }
}

object TransferOwnershipStreamTask {

  def main(args: Array[String]): Unit = {
    val configFilePath = Option(ParameterTool.fromArgs(args).get("config.file.path"))
    val config = configFilePath.map {
      path => ConfigFactory.parseFile(new File(path)).resolve()
    }.getOrElse(ConfigFactory.load("transfer-ownership.conf").withFallback(ConfigFactory.systemEnvironment()))
    val transferOwnershipConfig = new TransferOwnershipConfig(config)
    val kafkaUtil = new FlinkKafkaConnector(transferOwnershipConfig)
    val task = new TransferOwnershipStreamTask(transferOwnershipConfig, kafkaUtil)
    task.process()
  }
}