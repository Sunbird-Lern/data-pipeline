package org.sunbird.dp.userinfo.task

import com.typesafe.config.Config
import org.sunbird.dp.core.job.BaseJobConfig

class ProgramUserInfoConfig(override val config: Config) extends BaseJobConfig(config, jobName = "ProgramUserInfo") {

  //Kafka
  override val kafkaConsumerParallelism: Int = config.getInt("task.consumer.parallelism")

  val programUserParallelism: Int = config.getInt("task.programUser.parallelism")

  val kafkaInputTopic: String = config.getString("kafka.input.topic")

  // Consumer
  val programUserConsumer = "program-user-consumer"

  //Cassandra
  val dbTable: String = config.getString("ml-cassandra.table")
  val dbKeyspace: String = config.getString("ml-cassandra.keyspace")
  val dbHost: String = config.getString("ml-cassandra.host")
  val dbPort: Int = config.getInt("ml-cassandra.port")


  // Functions
  val programUserInfoFunction = "ProgramUserInfoFunction"

}
