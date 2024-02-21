package org.sunbird.job.userinfo.task

import com.typesafe.config.ConfigFactory
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.sunbird.job.connector.FlinkKafkaConnector
import org.sunbird.job.userinfo.domain.Event
import org.sunbird.job.userinfo.functions.ProgramUserInfoFunction
import org.sunbird.job.util.FlinkUtil

import java.io.File

class ProgramUserInfoStreamTask(config: ProgramUserInfoConfig, kafkaConnector: FlinkKafkaConnector) {

  private val serialVersionUID = -7729362727131516112L

  def process(): Unit = {

      implicit val env: StreamExecutionEnvironment = FlinkUtil.getExecutionContext(config)
      val source = kafkaConnector.kafkaEventSource[Event](config.kafkaInputTopic)

      env.addSource(source, config.programUserConsumer)
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
