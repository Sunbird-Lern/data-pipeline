package org.sunbird.job.merge.user.courses.task

import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.sunbird.job.connector.FlinkKafkaConnector
import org.sunbird.job.merge.user.courses.domain.Event
import org.sunbird.job.merge.user.courses.functions.MergeUserCourseFunction
import org.sunbird.job.util.FlinkUtil

import java.io.File
import java.util


class MergeUserCoursesStreamTask(config: MergeUserCoursesConfig, kafkaConnector: FlinkKafkaConnector) {
  def process(): Unit = {
    implicit val env: StreamExecutionEnvironment = FlinkUtil.getExecutionContext(config)
    implicit val eventTypeInfo: TypeInformation[Event] = TypeExtractor.getForClass(classOf[Event])
    implicit val mapTypeInfo: TypeInformation[util.Map[String, AnyRef]] = TypeExtractor.getForClass(classOf[util.Map[String, AnyRef]])
    implicit val stringTypeInfo: TypeInformation[String] = TypeExtractor.getForClass(classOf[String])
    val source = kafkaConnector.kafkaJobRequestSource[Event](config.kafkaInputTopic)

    val progressStream = env.addSource(source).name(config.mergeUserCoursesConsumer)
      .uid(config.mergeUserCoursesConsumer).setParallelism(config.kafkaConsumerParallelism)
      .rebalance
      .process( new MergeUserCourseFunction(config))
      .name("merge-user-courses").uid("merge-user-courses")
      .setParallelism(config.parallelism)

    progressStream.getSideOutput(config.eventOutputTag).addSink(kafkaConnector.kafkaStringSink(config.courseBatchUpdaterTopic))
      .name(config.courseBatchUpdaterProducer).uid(config.courseBatchUpdaterProducer).setParallelism(config.courseBatchUpdaterParallelism)

    env.execute(config.jobName)
  }
}
object MergeUserCoursesStreamTask {

  def main(args: Array[String]): Unit = {
    val configFilePath = Option(ParameterTool.fromArgs(args).get("config.file.path"))
    val config = configFilePath.map {
      path => ConfigFactory.parseFile(new File(path)).resolve()
    }.getOrElse(ConfigFactory.load("merge-user-courses.conf").withFallback(ConfigFactory.systemEnvironment()))
    val mergeUserCoursesConfig = new MergeUserCoursesConfig(config)
    val kafkaUtil = new FlinkKafkaConnector(mergeUserCoursesConfig)
    val task = new MergeUserCoursesStreamTask(mergeUserCoursesConfig, kafkaUtil)
    task.process()
  }
}


// $COVERAGE-ON$
