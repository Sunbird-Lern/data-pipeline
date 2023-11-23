package org.sunbird.job.userdelete.task

import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.sunbird.dp.core.job.FlinkKafkaConnector
import org.sunbird.dp.core.util.{ElasticSearchUtil, FlinkUtil, HttpUtil}
import org.sunbird.job.userdelete.domain.Event
import org.sunbird.job.userdelete.functions.UserDeleteFunction

import java.io.File


class UserDeleteStreamTask (config: UserDeleteConfig, kafkaConnector: FlinkKafkaConnector) {

  def process(): Unit = {

    implicit val env: StreamExecutionEnvironment = FlinkUtil.getExecutionContext(config)
    implicit val mapTypeInfo: TypeInformation[Event] = TypeExtractor.getForClass(classOf[Event])
    val source = kafkaConnector.kafkaEventSource[Event](config.inputTopic)
    env.addSource(source, config.userDeletionCleanupConsumer).uid(config.userDeletionCleanupConsumer).
      setParallelism(config.userDeletionCleanupParallelism).rebalance()
      .process(new UserDeleteFunction(config))
      .name(config.userDeleteFunction).uid(config.userDeleteFunction)
    env.execute(config.jobName)
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


