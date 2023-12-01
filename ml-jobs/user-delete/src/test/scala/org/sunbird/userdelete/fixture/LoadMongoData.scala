package org.sunbird.userdelete.fixture

import org.mongodb.scala.Document

object LoadMongoData {

  val userProfileDocument = Document(
    "firstName" -> "John",
    "lastName" -> "Wick",
    "dob" -> "1991-12-31",
    "email" -> "Jo*****@shikshalokam.org",
    "maskedEmail" -> "Jo*****@shikshalokam.org",
    "recoveryEmail" -> "Jo*****@shikshalokam.org",
    "prevUsedEmail" -> "Jo*****@shikshalokam.org",
    "phone" -> "******9985",
    "maskedPhone" -> "******9985",
    "recoveryPhone" -> "******9985",
    "prevUsedPhone" -> "******9985",
  )

  val Data1 = Document(
    "_id" -> 123,
    "createdBy" -> "5deed393-6e04-449a-b98d-7f0fbf88f22e",
    "createdAt" -> "2021-05-25 07:38:10.949Z",
    "userProfile" -> userProfileDocument
  )

  val Data2 = Document(
    "_id" -> 456,
    "createdBy" -> "5deed393-6e04-449a-b98d-7f0fbf88f22e",
    "createdAt" -> "2021-05-25 07:38:10.949Z",
    "userProfile" -> userProfileDocument
  )

  val Data3 = Document(
    "_id" -> 123,
    "userId" -> "5deed393-6e04-449a-b98d-7f0fbf88f22e",
    "createdAt" -> "2021-05-25 07:38:10.949Z",
    "userProfile" -> userProfileDocument
  )

  val Data4 = Document(
    "_id" -> 456,
    "userId" -> "5deed393-6e04-449a-b98d-7f0fbf88f22e",
    "createdAt" -> "2021-05-25 07:38:10.949Z",
    "userProfile" -> userProfileDocument
  )

}
