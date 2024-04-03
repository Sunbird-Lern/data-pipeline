package org.sunbird.spec

import org.scalatest.{BeforeAndAfterAll, FlatSpec}
import redis.embedded.{RedisInstance, RedisServer}

class BaseSpec extends FlatSpec with BeforeAndAfterAll {
  var redisServer: RedisServer = _
//  var redisInstance: RedisInstance = _

  override def beforeAll() {
    super.beforeAll()
    redisServer = new RedisServer(6340)
    redisServer.start()
//    redisInstance = RedisServer.newRedisServer.port(6340).build
//    redisInstance.start()
    //EmbeddedPostgres.builder.setPort(5430).start() // Use the same port 5430 which is defined in the base-test.conf
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
//    redisInstance.stop()
    redisServer.stop()
  }

}
