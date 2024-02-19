package org.sunbird.job.connector

import java.util
import org.apache.flink.streaming.api.functions.sink.SinkFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer.Semantic
import org.apache.flink.streaming.connectors.kafka.{FlinkKafkaConsumer, FlinkKafkaProducer}
import org.sunbird.job.BaseJobConfig
import org.sunbird.job.domain.reader.{Event, JobRequest}
import org.sunbird.job.serde.{ByteDeserializationSchema, ByteSerializationSchema, EventDeserializationSchema, EventSerializationSchema, JobRequestDeserializationSchema, JobRequestSerializationSchema, MapDeserializationSchema, MapSerializationSchema, StringDeserializationSchema, StringSerializationSchema}

class FlinkKafkaConnector(config: BaseJobConfig) extends Serializable {
  def kafkaMapSource(kafkaTopic: String): SourceFunction[util.Map[String, AnyRef]] = {
    new FlinkKafkaConsumer[util.Map[String, AnyRef]](kafkaTopic, new MapDeserializationSchema, config.kafkaConsumerProperties)
  }

  def kafkaMapSink(kafkaTopic: String): SinkFunction[util.Map[String, AnyRef]] = {
    new FlinkKafkaProducer[util.Map[String, AnyRef]](kafkaTopic, new MapSerializationSchema(kafkaTopic), config.kafkaProducerProperties, Semantic.AT_LEAST_ONCE)
  }

  def kafkaStringSource(kafkaTopic: String): SourceFunction[String] = {
    new FlinkKafkaConsumer[String](kafkaTopic, new StringDeserializationSchema, config.kafkaConsumerProperties)
  }

  def kafkaStringSink(kafkaTopic: String): SinkFunction[String] = {
    new FlinkKafkaProducer[String](kafkaTopic, new StringSerializationSchema(kafkaTopic), config.kafkaProducerProperties, Semantic.AT_LEAST_ONCE)
  }

  def kafkaJobRequestSource[T <: JobRequest](kafkaTopic: String)(implicit m: Manifest[T]): SourceFunction[T] = {
    new FlinkKafkaConsumer[T](kafkaTopic, new JobRequestDeserializationSchema[T], config.kafkaConsumerProperties)
  }

  def kafkaJobRequestSink[T <: JobRequest](kafkaTopic: String)(implicit m: Manifest[T]): SinkFunction[T] = {
    new FlinkKafkaProducer[T](kafkaTopic,
      new JobRequestSerializationSchema[T](kafkaTopic), config.kafkaProducerProperties, Semantic.AT_LEAST_ONCE)
  }

  def kafkaEventSource[T <: Event](kafkaTopic: String)(implicit m: Manifest[T]): SourceFunction[T] = {
    new FlinkKafkaConsumer[T](kafkaTopic, new EventDeserializationSchema[T], config.kafkaConsumerProperties)
  }

  def kafkaEventSink[T <: Event](kafkaTopic: String)(implicit m: Manifest[T]): SinkFunction[T] = {
    new FlinkKafkaProducer[T](kafkaTopic,
      new EventSerializationSchema[T](kafkaTopic), config.kafkaProducerProperties, Semantic.AT_LEAST_ONCE)
  }

  def kafkaBytesSource(kafkaTopic: String): SourceFunction[Array[Byte]] = {
    new FlinkKafkaConsumer[Array[Byte]](kafkaTopic, new ByteDeserializationSchema, config.kafkaConsumerProperties)
  }

  def kafkaBytesSink(kafkaTopic: String): SinkFunction[Array[Byte]] = {
    new FlinkKafkaProducer[Array[Byte]](kafkaTopic, new ByteSerializationSchema(kafkaTopic), config.kafkaProducerProperties, Semantic.AT_LEAST_ONCE)
  }

}
