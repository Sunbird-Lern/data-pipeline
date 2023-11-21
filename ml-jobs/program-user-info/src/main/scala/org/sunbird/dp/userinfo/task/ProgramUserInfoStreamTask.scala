package org.sunbird.dp.userinfo.task

import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.sunbird.dp.core.job.FlinkKafkaConnector
import org.sunbird.dp.core.util.FlinkUtil
import org.sunbird.dp.userinfo.domain.Event
import org.sunbird.dp.userinfo.functions.ProgramUserInfoFunction

import java.io.File

class ProgramUserInfoStreamTask(config: ProgramUserInfoConfig, kafkaConnector: FlinkKafkaConnector) {

  private val serialVersionUID = -7729362727131516112L

  def process(): Unit = {

      implicit val env: StreamExecutionEnvironment = FlinkUtil.getExecutionContext(config)
      val source = kafkaConnector.kafkaEventSource[Event](config.kafkaInputTopic)

      env.fromSource(source, WatermarkStrategy.noWatermarks[Event](), config.programUserConsumer)
        .uid(config.programUserConsumer).setParallelism(config.kafkaConsumerParallelism).rebalance()
        .process(new ProgramUserInfoFunction(config))
        .name(config.programUserInfoFunction).uid(config.programUserInfoFunction)
        .setParallelism(config.programUserParallelism)

      env.execute(config.jobName)
    }
}

object ProgramUserInfoStreamTask {

  def main(args: Array[String]): Unit = {
    // $COVERAGE-OFF$
    val configFilePath = Option(ParameterTool.fromArgs(args).get("config.file.path"))
    val config = configFilePath.map {
      path => ConfigFactory.parseFile(new File(path)).resolve()
    }.getOrElse(ConfigFactory.load("program-user-info").withFallback(ConfigFactory.systemEnvironment()))
    val programUserInfoConfig = new ProgramUserInfoConfig(config)
    val kafkaUtil = new FlinkKafkaConnector(programUserInfoConfig)
    val task = new ProgramUserInfoStreamTask(programUserInfoConfig, kafkaUtil)
    task.process()
    // $COVERAGE-ON$
  }

}
