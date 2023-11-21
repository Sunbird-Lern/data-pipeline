package org.sunbird.spec

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.sunbird.dp.core.job.{BaseProcessFunction, Metrics}

import scala.collection.mutable


class TestMapStreamFunc(config: BaseProcessTestConfig)(implicit val stringTypeInfo: TypeInformation[String])
  extends BaseProcessFunction[mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]](config) {

  override def metricsList(): List[String] = {
    List(config.mapEventCount)
  }

  override def processElement(event: mutable.Map[String, AnyRef],
                              context: ProcessFunction[mutable.Map[String, AnyRef], mutable.Map[String, AnyRef]]#Context,
                              metrics: Metrics): Unit = {
    metrics.get(config.mapEventCount)
    metrics.reset(config.mapEventCount)
    metrics.incCounter(config.mapEventCount)
    context.output(config.mapOutputTag, event)
  }
}
