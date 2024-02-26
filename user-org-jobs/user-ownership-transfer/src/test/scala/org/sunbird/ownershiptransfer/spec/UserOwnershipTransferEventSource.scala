package org.sunbird.ownershiptransfer.spec

import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import org.sunbird.job.ownershiptransfer.domain.Event
import org.sunbird.job.util.JSONUtil
import org.sunbird.ownershiptransfer.fixture.EventFixture

class UserOwnershipTransferEventSource extends SourceFunction[Event] {
  override def run(ctx: SourceContext[Event]): Unit = {
    ctx.collect(new Event(JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.EVENT_1)))
  }

  override def cancel(): Unit = {}
}
