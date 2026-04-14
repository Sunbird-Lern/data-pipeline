package org.sunbird.job.certmigrator.task

import java.io.File
import java.util
import com.typesafe.config.ConfigFactory
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.functions.KeySelector
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.api.java.utils.ParameterTool
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.streaming.api.scala.{DataStream, StreamExecutionEnvironment}
import org.sunbird.job.certmigrator.domain.Event
import org.sunbird.job.certmigrator.functions.CertificateGeneratorFunction
import org.sunbird.job.connector.FlinkKafkaConnector
import org.sunbird.job.util.{FlinkUtil, HttpUtil}

class CertificateGeneratorStreamTask(config: CertificateGeneratorConfig, kafkaConnector: FlinkKafkaConnector, httpUtil: HttpUtil) {

  implicit val eventTypeInfo: TypeInformation[Event] = TypeExtractor.getForClass(classOf[Event])
  implicit val mapTypeInfo: TypeInformation[util.Map[String, AnyRef]] = TypeExtractor.getForClass(classOf[util.Map[String, AnyRef]])
  implicit val stringTypeInfo: TypeInformation[String] = TypeExtractor.getForClass(classOf[String])

  def process(): Unit = {
    implicit val env: StreamExecutionEnvironment = FlinkUtil.getExecutionContext(config)
    val source = kafkaConnector.kafkaJobRequestSource[Event](config.kafkaInputTopic)
    val inputStream = env.fromSource(source, WatermarkStrategy.noWatermarks(), config.certificateGeneratorConsumer)
      .uid(config.certificateGeneratorConsumer).setParallelism(config.kafkaConsumerParallelism)
      .rebalance
    buildGraph(inputStream)
    env.execute(config.jobName)
  }

  def processForTest(env: StreamExecutionEnvironment, inputStream: DataStream[Event]): Unit = {
    buildGraph(inputStream)
    env.execute(config.jobName)
  }

  private def buildGraph(inputStream: DataStream[Event]): Unit = {
    val processStreamTask = inputStream
      .keyBy(new CertificateGeneratorKeySelector)
      .process(new CertificateGeneratorFunction(config, httpUtil))
      .name("legacy-certificate-migrator")
      .uid("legacy-certificate-migrator")
      .setParallelism(config.parallelism)

    processStreamTask.getSideOutput(config.auditEventOutputTag)
      .sinkTo(kafkaConnector.kafkaStringSink(config.kafkaAuditEventTopic))
      .name(config.certificateGeneratorAuditProducer)
      .uid(config.certificateGeneratorAuditProducer)
  }

}

// $COVERAGE-OFF$ Disabling scoverage as the below code can only be invoked within flink cluster
object CertificateGeneratorStreamTask {

  def main(args: Array[String]): Unit = {
    val configFilePath = Option(ParameterTool.fromArgs(args).get("config.file.path"))
    val config = configFilePath.map {
      path => ConfigFactory.parseFile(new File(path)).resolve()
    }.getOrElse(ConfigFactory.load("legacy-certificate-migrator.conf").withFallback(ConfigFactory.systemEnvironment()))
    val ccgConfig = new CertificateGeneratorConfig(config)
    val kafkaUtil = new FlinkKafkaConnector(ccgConfig)
    val httpUtil = new HttpUtil
    val task = new CertificateGeneratorStreamTask(ccgConfig, kafkaUtil, httpUtil)
    task.process()
  }
}

// $COVERAGE-ON$

class CertificateGeneratorKeySelector extends KeySelector[Event, String] {
  override def getKey(event: Event): String = Set(event.userId, event.courseId, event.batchId).mkString("_")
}