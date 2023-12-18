package org.sunbird.userdelete.spec

import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import org.sunbird.job.userdelete.domain.Event
import org.sunbird.job.util.JSONUtil
import org.sunbird.userdelete.fixture.EventFixture

class UserDeleteEventSource extends SourceFunction[Event] {
  override def run(ctx: SourceContext[Event]): Unit = {
    ctx.collect(new Event(JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.VALID_EVENT),0, 0))
    ctx.collect(new Event(JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.EVENT_WITH_DIFFERENT_USERID), 0, 1))
    ctx.collect(new Event(JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.EVENT_WITH_USERID_NULL), 0, 2))
  }

  override def cancel(): Unit = {}
}