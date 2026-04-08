package org.sunbird.job.connector

import java.util
import org.apache.flink.connector.kafka.source.KafkaSource
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer
import org.apache.flink.connector.kafka.sink.KafkaSink
import org.apache.flink.connector.base.DeliveryGuarantee
import org.sunbird.job.BaseJobConfig
import org.sunbird.job.domain.reader.{Event, JobRequest}
import org.sunbird.job.serde.{ByteDeserializationSchema, ByteSerializationSchema, EventDeserializationSchema, EventSerializationSchema, JobRequestDeserializationSchema, JobRequestSerializationSchema, MapDeserializationSchema, MapSerializationSchema, StringDeserializationSchema, StringSerializationSchema}

class FlinkKafkaConnector(config: BaseJobConfig) extends Serializable {
  def kafkaMapSource(kafkaTopic: String): KafkaSource[util.Map[String, AnyRef]] = {
    KafkaSource.builder[util.Map[String, AnyRef]]()
      .setTopics(kafkaTopic)
      .setDeserializer(new MapDeserializationSchema)
      .setProperties(config.kafkaConsumerProperties)
      .setStartingOffsets(OffsetsInitializer.committedOffsets())
      .build()
  }

  def kafkaMapSink(kafkaTopic: String): KafkaSink[util.Map[String, AnyRef]] = {
    KafkaSink.builder[util.Map[String, AnyRef]]()
      .setRecordSerializer(new MapSerializationSchema(kafkaTopic))
      .setKafkaProducerConfig(config.kafkaProducerProperties)
      .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
      .build()
  }

  def kafkaStringSource(kafkaTopic: String): KafkaSource[String] = {
    KafkaSource.builder[String]()
      .setTopics(kafkaTopic)
      .setDeserializer(new StringDeserializationSchema)
      .setProperties(config.kafkaConsumerProperties)
      .setStartingOffsets(OffsetsInitializer.committedOffsets())
      .build()
  }

  def kafkaStringSink(kafkaTopic: String): KafkaSink[String] = {
    KafkaSink.builder[String]()
      .setRecordSerializer(new StringSerializationSchema(kafkaTopic))
      .setKafkaProducerConfig(config.kafkaProducerProperties)
      .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
      .build()
  }

  def kafkaJobRequestSource[T <: JobRequest](kafkaTopic: String)(implicit m: Manifest[T]): KafkaSource[T] = {
    KafkaSource.builder[T]()
      .setTopics(kafkaTopic)
      .setDeserializer(new JobRequestDeserializationSchema[T])
      .setProperties(config.kafkaConsumerProperties)
      .setStartingOffsets(OffsetsInitializer.committedOffsets())
      .build()
  }

  def kafkaJobRequestSink[T <: JobRequest](kafkaTopic: String)(implicit m: Manifest[T]): KafkaSink[T] = {
    KafkaSink.builder[T]()
      .setRecordSerializer(new JobRequestSerializationSchema[T](kafkaTopic))
      .setKafkaProducerConfig(config.kafkaProducerProperties)
      .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
      .build()
  }

  def kafkaEventSource[T <: Event](kafkaTopic: String)(implicit m: Manifest[T]): KafkaSource[T] = {
    KafkaSource.builder[T]()
      .setTopics(kafkaTopic)
      .setDeserializer(new EventDeserializationSchema[T])
      .setProperties(config.kafkaConsumerProperties)
      .setStartingOffsets(OffsetsInitializer.committedOffsets())
      .build()
  }

  def kafkaEventSink[T <: Event](kafkaTopic: String)(implicit m: Manifest[T]): KafkaSink[T] = {
    KafkaSink.builder[T]()
      .setRecordSerializer(new EventSerializationSchema[T](kafkaTopic))
      .setKafkaProducerConfig(config.kafkaProducerProperties)
      .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
      .build()
  }

  def kafkaBytesSource(kafkaTopic: String): KafkaSource[Array[Byte]] = {
    KafkaSource.builder[Array[Byte]]()
      .setTopics(kafkaTopic)
      .setDeserializer(new ByteDeserializationSchema)
      .setProperties(config.kafkaConsumerProperties)
      .setStartingOffsets(OffsetsInitializer.committedOffsets())
      .build()
  }

  def kafkaBytesSink(kafkaTopic: String): KafkaSink[Array[Byte]] = {
    KafkaSink.builder[Array[Byte]]()
      .setRecordSerializer(new ByteSerializationSchema(kafkaTopic))
      .setKafkaProducerConfig(config.kafkaProducerProperties)
      .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
      .build()
  }

}
