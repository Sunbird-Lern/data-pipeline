package org.sunbird.deletioncleanup.spec

import org.apache.flink.streaming.api.functions.sink.SinkFunction
import java.util

class GenerateUserDeletionCleanupSink extends SinkFunction[String] {
    override def invoke(value: String): Unit = {
        synchronized {
            println(value)
            GenerateUserDeletionCleanupSink.values.add(value)
        }
    }
}

object GenerateUserDeletionCleanupSink {
    val values: util.List[String] = new util.ArrayList()
}

