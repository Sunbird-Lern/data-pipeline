package org.sunbird.job.userinfo.util

import com.datastax.driver.core.querybuilder.QueryBuilder
import org.apache.commons.collections.MapUtils
import org.sunbird.job.util.CassandraUtil

import java.util
import java.util.Map

class Commons (@transient var cassandraUtil: CassandraUtil){

  def insertDbRecord(keyspace: String, table: String, keys: Map[String, AnyRef]) = {
    val insertQuery = QueryBuilder.insertInto(keyspace, table)
    convertKeyCase(keys).entrySet.forEach((entry: util.Map.Entry[String, AnyRef]) => insertQuery.value(entry.getKey, entry.getValue))
    cassandraUtil.upsert(insertQuery.toString)
  }

  private def convertKeyCase(properties: util.Map[String, AnyRef]) = {
    val keyLowerCaseMap = new util.HashMap[String, AnyRef]
    if (MapUtils.isNotEmpty(properties)) {
      properties.entrySet.stream()
        .filter(e => e != null && e.getKey != null)
        .forEach((entry: util.Map.Entry[String, AnyRef]) => {
          keyLowerCaseMap.put(entry.getKey.toLowerCase, entry.getValue)
        })
    }
    keyLowerCaseMap
  }

}
