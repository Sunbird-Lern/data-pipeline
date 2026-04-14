package org.sunbird.job.cache

import org.slf4j.LoggerFactory
import org.sunbird.job.BaseJobConfig
import redis.clients.jedis.Jedis

class RedisConnect(jobConfig: BaseJobConfig, host: Option[String] = None, port: Option[Int] = None) extends java.io.Serializable {

  private val serialVersionUID = -396824011996012513L

  val redisEnabled: Boolean = jobConfig.redisEnabled
  val redisHost: String = host.getOrElse(jobConfig.redisHost)
  val redisPort: Int = port.getOrElse(jobConfig.redisPort)
  private val logger = LoggerFactory.getLogger(classOf[RedisConnect])

  private def getConnection(backoffTimeInMillis: Long): Jedis = {
    val defaultTimeOut = 30000
    if (backoffTimeInMillis > 0) try Thread.sleep(backoffTimeInMillis)
    catch {
      case e: InterruptedException =>
        e.printStackTrace()
    }
    logger.info("Obtaining new Redis connection... : for host :" + redisHost + " and  port: " + redisPort)
    new Jedis(redisHost, redisPort, defaultTimeOut)
  }


  def getConnection(db: Int, backoffTimeInMillis: Long): Jedis = {
    val jedis: Jedis = getConnection(backoffTimeInMillis)
    jedis.select(db)
    jedis
  }

  def getConnection(db: Int): Jedis = {
    val jedis = getConnection(db, backoffTimeInMillis = 0)
    jedis.select(db)
    jedis
  }

  def getConnection: Jedis = getConnection(db = 0)
}
