package org.sunbird.deletioncleanup.spec

import org.apache.flink.streaming.api.functions.sink.SinkFunction

import java.util

class GenerateUserDeleteSink extends SinkFunction[String] {
  override def invoke(value: String): Unit = {
    synchronized {
      println(value)
      GenerateUserDeleteSink.values.add(value)
    }
  }
}

object GenerateUserDeleteSink {
  val values: util.List[String] = new util.ArrayList()
}

