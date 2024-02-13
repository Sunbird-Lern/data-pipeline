package org.sunbird.transferownership.spec

import org.apache.flink.streaming.api.functions.sink.SinkFunction

import java.util

class GenerateTransferOwnershipSink extends SinkFunction[String] {

  override def invoke(value: String): Unit = {
    synchronized{
      println(value)
      GenerateTransferOwnershipSink.values.add(value)
    }
  }
}

object GenerateTransferOwnershipSink {
  val values: util.List[String] = new util.ArrayList()
}