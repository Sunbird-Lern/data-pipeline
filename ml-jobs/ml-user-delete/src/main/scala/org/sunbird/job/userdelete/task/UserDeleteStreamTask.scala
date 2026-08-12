package org.sunbird.job.userdelete.task

import java.io.File
import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.sunbird.job.connector.FlinkKafkaConnector
import org.sunbird.job.userdelete.domain.Event
import org.sunbird.job.userdelete.functions.UserDeleteFunction
import org.sunbird.job.util.FlinkUtil

class UserDeleteStreamTask(config: UserDeleteConfig, kafkaConnector: FlinkKafkaConnector) {

  implicit val mapTypeInfo: TypeInformation[Event] = TypeExtractor.getForClass(classOf[Event])

  def process(): Unit = {
    implicit val env: StreamExecutionEnvironment = FlinkUtil.getExecutionContext(config)
    val source = kafkaConnector.kafkaJobRequestSource[Event](config.inputTopic)
    val inputStream = env.fromSource(source, WatermarkStrategy.noWatermarks(), config.mlUserDeleteConsumer)
      .uid(config.mlUserDeleteConsumer)
      .setParallelism(config.mlUserDeleteParallelism)
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
      .process(new UserDeleteFunction(config))
      .name(config.userDeleteFunction).uid(config.userDeleteFunction)
  }
}

object UserDeleteStreamTask {
  def main(args: Array[String]): Unit = {
    val configFilePath = Option(ParameterTool.fromArgs(args).get("config.file.path"))
    val config = configFilePath.map {
      path => ConfigFactory.parseFile(new File(path)).resolve()
    }.getOrElse(ConfigFactory.load("user-delete.conf").withFallback(ConfigFactory.systemEnvironment()))
    val userDeleteConfig = new UserDeleteConfig(config)
    val kafkaUtil = new FlinkKafkaConnector(userDeleteConfig)
    val task = new UserDeleteStreamTask(userDeleteConfig, kafkaUtil)
    task.process()
  }
}
