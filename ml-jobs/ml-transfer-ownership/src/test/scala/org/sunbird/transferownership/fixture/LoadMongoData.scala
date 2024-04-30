package org.sunbird.transferownership.fixture

import org.mongodb.scala.Document
import org.mongodb.scala.bson.{BsonArray, ObjectId}

object LoadMongoData {

  val license = Document("author" -> "DeletedUser", "creator" -> "DeletedUser")

  val loadSolutionsData1 = Document(
    "_id" -> new ObjectId("648c54255c81330008b69fc4"),
    "author" -> "789",
    "creator" -> "DeletedUser",
    "license" -> license
  )

}