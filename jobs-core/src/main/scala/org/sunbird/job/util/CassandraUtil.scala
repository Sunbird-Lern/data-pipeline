package org.sunbird.job.util

import com.datastax.driver.core._
import com.datastax.driver.core.exceptions.DriverException
import com.datastax.driver.core.policies.DCAwareRoundRobinPolicy
import com.datastax.driver.core.querybuilder.{Delete, QueryBuilder}
import org.slf4j.LoggerFactory
import org.sunbird.job.BaseJobConfig

import java.util

class CassandraUtil(host: String, port: Int, isMultiDCEnabled: Boolean, config: BaseJobConfig = null) {

  private[this] val logger = LoggerFactory.getLogger("CassandraUtil")

  // Single place that builds a Cluster, so the constructor and reconnect() both get credentials and TLS.
  // config is optional: without it no credentials or TLS are added (the previous behaviour).
  private def newCluster(): Cluster = {
    val cb = Cluster.builder()
      .addContactPoint(host)
      .withPort(port)
      .withQueryOptions(new QueryOptions().setConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM))
      .withoutJMXReporting()

    if(isMultiDCEnabled) {
      cb.withLoadBalancingPolicy(DCAwareRoundRobinPolicy.builder().build())
    }
    if (config != null) CassandraSecurity.configure(cb, config)
    cb.build()
  }

  var cluster = newCluster()
  var session = cluster.connect()

  def findOne(query: String): Row = {
    try {
      val rs: ResultSet = session.execute(query)
      rs.one
    } catch {
      case ex: DriverException =>
        logger.error(s"findOne - Error while executing query $query :: ", ex)
        this.reconnect()
        this.findOne(query)
    }
  }

  def find(query: String): util.List[Row] = {
    try {
      val rs: ResultSet = session.execute(query)
      rs.all
    } catch {
      case ex: DriverException =>
        this.reconnect()
        this.find(query)
    }
  }

  def upsert(query: String): Boolean = {
    val rs: ResultSet = session.execute(query)
    rs.wasApplied
  }

  def getUDTType(keyspace: String, typeName: String): UserType = session.getCluster.getMetadata.getKeyspace(keyspace).getUserType(typeName)

  def reconnect(): Unit = {
    this.session.close()
    this.cluster.close()
    this.cluster = newCluster()
    this.session = this.cluster.connect
  }

  def close(): Unit = {
    this.session.close()
  }

  def update(query: Statement): Boolean = {
    val rs: ResultSet = session.execute(query)
    rs.wasApplied
  }

  def executePreparedStatement(query: String, params: Object*): util.List[Row] = {
    val rs: ResultSet = session.execute(session.prepare(query).bind(params: _*))
    rs.all()
  }

  def deleteRecordByCompositeKey(keyspaceName: String, tableName: String, compositeKeyMap: Map[String, String]): Boolean = {
    logger.debug("CassandraUtil: deleteRecord start:: " + compositeKeyMap)
    try {
      val delete: Delete = QueryBuilder.delete.from(keyspaceName, tableName)
      compositeKeyMap.foreach(x => {
        delete.where().and(QueryBuilder.eq(x._1, x._2))
      })

      val rs: ResultSet = session.execute(delete)
      rs.wasApplied
    } catch {
      case e: Exception =>
        logger.error("CassandraUtil: deleteRecord by composite key. " + tableName + " : " + e.getMessage, e)
        throw e
    }
  }

}
