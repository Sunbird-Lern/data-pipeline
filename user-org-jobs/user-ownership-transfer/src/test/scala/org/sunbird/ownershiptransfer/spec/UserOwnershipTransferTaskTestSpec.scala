package org.sunbird.ownershiptransfer.spec

import org.apache.flink.streaming.api.functions.sink.SinkFunction
import java.util

class GenerateUserOwnershipTransferSink extends SinkFunction[String] {
    override def invoke(value: String): Unit = {
        synchronized {
            println(value)
            GenerateUserOwnershipTransferSink.values.add(value)
        }
    }
}

object GenerateUserOwnershipTransferSink {
    val values: util.List[String] = new util.ArrayList()
}

