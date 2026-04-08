package org.sunbird.job.serde

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema.KafkaSinkContext
import org.apache.flink.util.Collector
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.ProducerRecord
import org.slf4j.LoggerFactory
import org.sunbird.job.domain.reader.Event
import org.sunbird.job.util.JSONUtil

import java.nio.charset.StandardCharsets
import java.util
import scala.reflect.{ClassTag, classTag}

class EventDeserializationSchema[T <: Event](implicit ct: ClassTag[T]) extends KafkaRecordDeserializationSchema[T] {
  private val serialVersionUID = - 7339003654529835367L
  private[this] val logger = LoggerFactory.getLogger(classOf[EventDeserializationSchema[Event]])

  override def deserialize(record: ConsumerRecord[Array[Byte], Array[Byte]], out: Collector[T]): Unit = {
    try {
      val result = JSONUtil.deserialize[util.HashMap[String, AnyRef]](record.value())
      out.collect(ct.runtimeClass.getConstructor(classOf[util.Map[String, AnyRef]]).newInstance(result).asInstanceOf[T])
    }
    catch {
      case ex: Exception =>
        logger.error("Exception when parsing event from kafka: " + record, ex)
        out.collect(ct.runtimeClass.getConstructor(classOf[util.Map[String, AnyRef]]).newInstance(new util.HashMap[String, AnyRef]()).asInstanceOf[T])
    }
  }

  override def getProducedType: TypeInformation[T] = TypeExtractor.getForClass(classTag[T].runtimeClass).asInstanceOf[TypeInformation[T]]
}

class EventSerializationSchema[T <: Event : Manifest](topic: String) extends KafkaRecordSerializationSchema[T] {
  private val serialVersionUID = -4284080856874185929L

  override def serialize(element: T, context: KafkaSinkContext, timestamp: java.lang.Long): ProducerRecord[Array[Byte], Array[Byte]] = {
    new ProducerRecord[Array[Byte], Array[Byte]](topic, Option(element.kafkaKey()).map(_.getBytes(StandardCharsets.UTF_8)).orNull,
      element.getJson().getBytes(StandardCharsets.UTF_8))
  }
}
