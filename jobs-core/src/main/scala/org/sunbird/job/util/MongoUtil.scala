package org.sunbird.job.util

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

  def find(collection: String, query: Bson): util.List[Document] = {
    val mongoCollection: MongoCollection[Document] = mongoDatabase.getCollection(collection)
    val result = mongoCollection.find(query).toFuture()
    Await.result(result, Duration.Inf).asJava
  }

  def insertOne(collection: String, document: Document): Boolean = {
    val mongoCollection: MongoCollection[Document] = mongoDatabase.getCollection(collection)
    val result = mongoCollection.insertOne(document).toFuture()
    Await.result(result, Duration.Inf).wasAcknowledged()
  }

  def updateMany(collection: String, filter: Bson, update: Bson) = {
    val mongoCollection: MongoCollection[Document] = mongoDatabase.getCollection(collection)
    val updateRes: Future[UpdateResult] = mongoCollection.updateMany(filter, update).toFuture()
    Await.result(updateRes, Duration.Inf)
    updateRes
  }

  def updateOne(collection: String, filter: Bson, update: Bson): Future[UpdateResult] = {
    val mongoCollection: MongoCollection[Document] = mongoDatabase.getCollection(collection)
    val updateRes: Future[UpdateResult] = mongoCollection.updateOne(filter, update).toFuture()
    updateRes
  }

  def close(): Unit = {
    this.mongoClient.close()
  }

}
