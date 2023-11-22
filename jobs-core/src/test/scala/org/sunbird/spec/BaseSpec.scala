package org.sunbird.spec

import org.scalatest.{BeforeAndAfterAll, FlatSpec}
import redis.embedded.RedisServer

class BaseSpec extends FlatSpec with BeforeAndAfterAll {
  var redisServer: RedisServer = _

  override def beforeAll() {
    super.beforeAll()
    redisServer = RedisServer.newRedisServer().port(6340).build()
    redisServer.start()
    //EmbeddedPostgres.builder.setPort(5430).start() // Use the same port 5430 which is defined in the base-test.conf
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    redisServer.stop()
  }

}
