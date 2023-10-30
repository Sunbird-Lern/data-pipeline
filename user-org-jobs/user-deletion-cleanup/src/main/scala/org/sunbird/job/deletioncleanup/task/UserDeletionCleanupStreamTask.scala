package org.sunbird.job.deletioncleanup.task

import java.io.File
import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.sunbird.dp.core.job.FlinkKafkaConnector
import org.sunbird.dp.core.util.{ElasticSearchUtil, FlinkUtil, HttpUtil}
import org.sunbird.job.deletioncleanup.domain.Event
import org.sunbird.job.deletioncleanup.functions.UserDeletionCleanupFunction

class UserDeletionCleanupStreamTask(config: UserDeletionCleanupConfig, httpUtil: HttpUtil, esUtil: ElasticSearchUtil, kafkaConnector: FlinkKafkaConnector) {

  def process(): Unit = {

    implicit val env: StreamExecutionEnvironment = FlinkUtil.getExecutionContext(config)
    implicit val mapTypeInfo: TypeInformation[Event] = TypeExtractor.getForClass(classOf[Event])
    val source = kafkaConnector.kafkaEventSource[Event](config.inputTopic)
    env.addSource(source, config.userDeletionCleanupConsumer).uid(config.userDeletionCleanupConsumer).
      setParallelism(config.userDeletionCleanupParallelism).rebalance()
      .process(new UserDeletionCleanupFunction(config, httpUtil, esUtil))
      .name(config.userDeletionCleanupFunction).uid(config.userDeletionCleanupFunction)
    env.execute(config.jobName)
  }

}

// $COVERAGE-OFF$ Disabling scoverage as the below code can only be invoked within flink cluster
object UserDeletionCleanupStreamTask {

  def main(args: Array[String]): Unit = {
    val configFilePath = Option(ParameterTool.fromArgs(args).get("config.file.path"))
    val config = configFilePath.map {
      path => ConfigFactory.parseFile(new File(path)).resolve()
    }.getOrElse(ConfigFactory.load("user-deletion-cleanup.conf").withFallback(ConfigFactory.systemEnvironment()))
    val userDeletionCleanupConfig = new UserDeletionCleanupConfig(config)
    val httpUtil = new HttpUtil
    val esUtil: ElasticSearchUtil = new ElasticSearchUtil(userDeletionCleanupConfig.esConnection, userDeletionCleanupConfig.searchIndex, userDeletionCleanupConfig.courseBatchIndexType)
    val kafkaUtil = new FlinkKafkaConnector(userDeletionCleanupConfig)
    val task = new UserDeletionCleanupStreamTask(userDeletionCleanupConfig, httpUtil, esUtil, kafkaUtil)
    task.process()
  }
}

// $COVERAGE-ON$
