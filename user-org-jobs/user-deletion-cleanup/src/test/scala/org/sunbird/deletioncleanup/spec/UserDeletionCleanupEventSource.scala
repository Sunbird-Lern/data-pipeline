package org.sunbird.deletioncleanup.spec

import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import org.sunbird.deletioncleanup.fixture.EventFixture
import org.sunbird.job.deletioncleanup.domain.Event
import org.sunbird.job.util.JSONUtil

class UserDeletionCleanupEventSource extends SourceFunction[Event] {
  override def run(ctx: SourceContext[Event]): Unit = {
    ctx.collect(new Event(JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.EVENT_1)))
  }

  override def cancel(): Unit = {}
}
