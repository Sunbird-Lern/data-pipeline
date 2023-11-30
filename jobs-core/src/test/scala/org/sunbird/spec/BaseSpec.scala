package org.sunbird.spec

import org.scalatest.{BeforeAndAfterAll, FlatSpec}
import redis.embedded.RedisServer

class BaseSpec extends FlatSpec with BeforeAndAfterAll {
  var redisServer: RedisServer = _

  override def beforeAll() {
    super.beforeAll()
    redisServer = RedisServer.newRedisServer().port(6340).build()
    redisServer.start()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    redisServer.stop()
  }

}
