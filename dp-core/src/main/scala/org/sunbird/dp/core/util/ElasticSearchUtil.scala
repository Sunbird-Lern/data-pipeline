package org.sunbird.dp.core.util

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.http.HttpHost
import org.apache.http.client.config.RequestConfig
import org.elasticsearch.action.get.GetRequest
import org.elasticsearch.action.index.IndexRequest
import org.elasticsearch.action.update.UpdateRequest
import org.elasticsearch.client.{RequestOptions, RestClient, RestHighLevelClient}
import org.slf4j.LoggerFactory

import java.io.IOException
import java.util

class ElasticSearchUtil(connectionInfo: String, indexName: String, indexType: String, batchSize: Int = 1000) extends Serializable {

  private val resultLimit = 100
  private val esClient: RestHighLevelClient = createClient(connectionInfo)
  private val mapper = new ObjectMapper
  private val maxFieldLimit = 32000

  private[this] val logger = LoggerFactory.getLogger(classOf[ElasticSearchUtil])

  System.setProperty("es.set.netty.runtime.available.processors", "false")

  private def createClient(connectionInfo: String): RestHighLevelClient = {
    val httpHosts: Array[HttpHost] = connectionInfo.split(",").map(info => {
      val host = info.split(":")(0)
      val port = info.split(":")(1).toInt
      new HttpHost(host, port)
    })

    val restClientBuilder = RestClient.builder(httpHosts: _*)
      .setRequestConfigCallback((requestConfigBuilder: RequestConfig.Builder) => {
        requestConfigBuilder.setConnectionRequestTimeout(-1)
      })

    new RestHighLevelClient(restClientBuilder)
  }

  def updateDocument(identifier: String, document: String): Unit = {
    try {
      val doc = mapper.readValue(document, new TypeReference[util.Map[String, AnyRef]]() {})
      val updatedDoc = checkDocStringLength(doc)
      val indexRequest = new IndexRequest(indexName, indexType, identifier).source(updatedDoc)
      val request = new UpdateRequest(indexName, indexType, identifier).doc(updatedDoc).upsert(indexRequest)
      val response = esClient.update(request, RequestOptions.DEFAULT)
      logger.info(s"Updated ${response.getId} to index ${response.getIndex}")
    } catch {
      case e: IOException =>
        logger.error(s"ElasticSearchUtil:: Error while updating document to index: $indexName", e)
    }
  }

  def getDocumentAsString(identifier: String): String = {
    val getRequest = new GetRequest(indexName, indexType, identifier)
    val response = esClient.get(getRequest, RequestOptions.DEFAULT)
    response.getSourceAsString
  }

  def close(): Unit = {
    if (esClient != null) {
      try {
        esClient.close()
      } catch {
        case e: IOException => e.printStackTrace()
      }
    }
  }

  private def checkDocStringLength(doc: util.Map[String, AnyRef]): util.Map[String, AnyRef] = {
    doc.entrySet().forEach(entry => {
      if (entry.getValue.isInstanceOf[String] && entry.getValue.toString.length > maxFieldLimit) {
        doc.put(entry.getKey, entry.getValue.toString.substring(0, maxFieldLimit))
      }
    })
    doc
  }

}
