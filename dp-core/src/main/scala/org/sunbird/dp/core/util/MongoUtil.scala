package org.sunbird.dp.core.util

import org.mongodb.scala.bson.conversions.Bson
import org.mongodb.scala.result.UpdateResult
import org.mongodb.scala.{Document, MongoClient, MongoCollection, MongoDatabase}

import java.util
import scala.collection.JavaConverters._
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class MongoUtil(host: String, port: Int, database: String) {

  val mongoClient: MongoClient = MongoClient(s"mongodb://$host:$port")
  val mongoDatabase: MongoDatabase = mongoClient.getDatabase(database)

  def updateMany(collection: String, filter: Bson, update: Bson) = {
    val mongoCollection: MongoCollection[Document] = mongoDatabase.getCollection(collection)
    val updateRes: Future[UpdateResult] = mongoCollection.updateMany(filter, update).toFuture()
    Await.result(updateRes, Duration.Inf)
    updateRes
  }

  def close(): Unit = {
    this.mongoClient.close()
  }

}
