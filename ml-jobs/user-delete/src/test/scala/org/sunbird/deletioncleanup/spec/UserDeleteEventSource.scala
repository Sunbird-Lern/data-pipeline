package org.sunbird.deletioncleanup.spec

import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import org.sunbird.deletioncleanup.fixture.EventFixture
import org.sunbird.dp.core.util.JSONUtil
import org.sunbird.job.userdelete.domain.Event

class UserDeleteEventSource extends SourceFunction[Event] {
  override def run(ctx: SourceContext[Event]): Unit = {
    ctx.collect(new Event(JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.EVENT_1)))
  }

  override def cancel(): Unit = {}
}