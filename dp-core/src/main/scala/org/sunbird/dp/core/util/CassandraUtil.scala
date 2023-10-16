package org.sunbird.dp.core.util

import com.datastax.driver.core._
import com.datastax.driver.core.exceptions.DriverException
import com.datastax.driver.core.policies.DCAwareRoundRobinPolicy
import com.datastax.driver.core.querybuilder.{Delete, QueryBuilder}
import org.slf4j.{Logger, LoggerFactory}

import java.util

class CassandraUtil(host: String, port: Int, isMultiDCEnabled: Boolean) {

  val logger: Logger = LoggerFactory.getLogger("CassandraUtil")
  val options : QueryOptions = new QueryOptions()
  var cluster: Cluster = {
    val cb = Cluster.builder()
      .addContactPoint(host)
      .withPort(port)
      .withQueryOptions(options.setConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM))
      .withoutJMXReporting()

    if(isMultiDCEnabled) {
      cb.withLoadBalancingPolicy(DCAwareRoundRobinPolicy.builder().build())
    }
    cb.build()
  }

  var session: Session = cluster.connect()

  def findOne(query: String): Row = {
    val rs: ResultSet = session.execute(query)
    rs.one
  }

  def find(query: String): util.List[Row] = {
    try {
      val rs: ResultSet = session.execute(query)
      rs.all
    } catch {
      case ex: DriverException =>
        logger.info(s"Failed cassandra query is $query")
        ex.printStackTrace()
        throw ex
        // this.reconnect()
        // this.find(query)
    }
  }

  def upsert(query: String): Boolean = {
    val rs: ResultSet = session.execute(query)
    rs.wasApplied
  }

  def getUDTType(keyspace: String, typeName: String): UserType = session.getCluster.getMetadata.getKeyspace(keyspace).getUserType(typeName)

  def reconnect(): Unit = {
    this.session.close()
    val cluster: Cluster = Cluster.builder.addContactPoint(host).withPort(port).build
    this.session = cluster.connect
  }

  def close(): Unit = {
    this.session.close()
  }

  def deleteRecordByCompositeKey(keyspaceName: String, tableName: String, compositeKeyMap: Map[String, String]): Boolean = {
    logger.debug( "CassandraUtil: deleteRecord start:: " + compositeKeyMap)
    try {
      val delete: Delete = QueryBuilder.delete.from(keyspaceName, tableName)
      compositeKeyMap.foreach(x => {
        delete.where().and(QueryBuilder.eq(x._1, x._2))
      })
      logger.info("CassandraUtil: delete query:: " + delete.getQueryString)
      val rs: ResultSet = session.execute(delete)
      rs.wasApplied
    } catch {
      case e: Exception =>
        logger.error("CassandraUtil: deleteRecord by composite key. " +  tableName + " : " + e.getMessage, e)
        throw e
    }
  }

}
