package org.sunbird.transferownership.fixture

import org.bson.types.ObjectId
import org.mongodb.scala.Document

object LoadMongoData {

  val license = Document("author" -> "DeletedUser", "creator" -> "DeletedUser")

  val platformRoles = List(Document("roleId" -> new ObjectId("60c74aa8b81797534e9a3f11"),"code" -> "PROGRAM_MANAGER"))

  val loadSolutionsData1 = Document(
    "_id" -> new ObjectId("5f34ade5585244939f89f8f5"),
    "author" -> "789",
    "creator" -> "DeletedUser",
    "license" -> license
  )

  val loadSolutionsData2 = Document(
    "_id" -> new ObjectId("5f34ade5585244939f89f8f6"),
    "author" -> "67890",
    "creator" -> "DeletedUser",
    "license" -> license
  )

  val loadSolutionsData3 = Document(
    "_id" -> new ObjectId("5f34ade5585244939f89f8f7"),
    "author" -> "67890",
    "creator" -> "DeletedUser",
    "license" -> license
  )

  val loadProgramData1 = Document(
    "_id" -> new ObjectId("5f34e44681871d939950bca6"),
    "owner" -> "789"
  )

  val loadProgramData2 = Document(
    "_id" -> new ObjectId("5f34e44681871d939950bca7"),
    "owner" -> "67890"
  )

  val loadProgramData3 = Document(
    "_id" -> new ObjectId("5f34e44681871d939950bca8"),
    "owner" -> "67890"
  )

  val loadProgramData4 = Document(
    "_id" -> new ObjectId("5f34e44681871d939950bca9"),
    "owner" -> "778899"
  )

  val loadUserExtensionData = Document(
    "_id" -> new ObjectId("5f34e44681871d939950abc6"),
    "userId" -> "112233",
    "platformRoles" -> platformRoles
  )

  val loadUserRolesData = Document(
    "_id" -> new ObjectId("5f32d8238e0dc831240405a0"),
    "code" -> "HM"
  )

}