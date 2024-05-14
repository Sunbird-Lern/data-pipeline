package org.sunbird.transferownership.spec

import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import org.sunbird.job.transferownership.domain.Event
import org.sunbird.job.util.JSONUtil
import org.sunbird.transferownership.fixture.EventFixture

class TransferOwnershipEventSource extends SourceFunction[Event] {

  override def run(ctx: SourceContext[Event]): Unit = {
    ctx.collect(new Event(JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.ASSET_SOLUTIONS_EVENT), 0, 0))
    ctx.collect(new Event(JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.ASSET_PROGRAM_EVENT_WITH_USER_EXTENSION_DATA), 1, 0))
    ctx.collect(new Event(JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.ASSET_PROGRAM_EVENT_WITHOUT_USER_EXTENSION_DATA), 2, 0))
    ctx.collect(new Event(JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.WITHOUT_ASSET_INFORMATION_EVENT), 3, 0))
    ctx.collect(new Event(JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.WITHOUT_ASSET_INFORMATION_EVENT_1), 4, 0))
    ctx.collect(new Event(JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.ASSET_PROGRAM_EVENT_WITH_USER_EXTENSION_DATA_1), 5, 0))
  }

  override def cancel(): Unit = {}

}