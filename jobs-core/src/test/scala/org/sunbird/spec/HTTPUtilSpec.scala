package org.sunbird.spec

import org.apache.commons.io.FileUtils
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FlatSpec, Matchers}
import org.sunbird.job.util.{HTTPResponse, HttpUtil, JSONUtil, ScalaJsonUtil}

import java.io.File

class HTTPUtilSpec extends FlatSpec with Matchers {

  val config: Config = ConfigFactory.load("base-test.conf")
  val imagePath = config.getString("blob.input.contentImagePath")
  val videoPath = config.getString("blob.input.contentVideoPath")
  val httpUtil = new HttpUtil

  "get" should "return success response" in {
    val resp: HTTPResponse = httpUtil.get(imagePath)
    assert(resp.isSuccess)
  }

  "post" should "return success response" in {
    val reqMap = new java.util.HashMap[String, AnyRef]() {
      put("request", new java.util.HashMap[String, AnyRef]() {
        put("filters", new java.util.HashMap[String, AnyRef]() {
          put("objectType", "Content")
          put("status", "Live")
        })
        put("limit", 1.asInstanceOf[AnyRef])
        put("fields", Array[String]("identifier", "name"))
      })
    }

    val resp = httpUtil.post("https://dev.sunbirded.org/api/content/v1/search", JSONUtil.serialize(reqMap))
    assert(resp.isSuccess)
  }

  "getSize" should "return file size" in {
    val resp: Int = httpUtil.getSize(imagePath)
    println(resp)
    assert(resp>0)
  }

  "downloadFile" should "download file from provided Url" in {
    val fileUrl = imagePath
    val httpUtil = new HttpUtil
    val downloadPath = "/tmp/content" + File.separator + "_temp_" + System.currentTimeMillis
    val downloadedFile = httpUtil.downloadFile(fileUrl, downloadPath)
    assert(downloadedFile.exists())
    FileUtils.deleteDirectory(downloadedFile.getParentFile)
  }

  "downloadFile" should "return null when the url has no file name" in {
    val fileUrl = "https://dockstaging.sunbirded.org/"
    val httpUtil = new HttpUtil
    val downloadPath = "/tmp/content" + File.separator + "_temp_" + System.currentTimeMillis
    assertThrows[IllegalArgumentException] {
      httpUtil.downloadFile(fileUrl, downloadPath)
    }
  }

  "downloadFile" should "download file with lower case name" in {
    val fileUrl = videoPath
    val httpUtil = new HttpUtil
    val downloadPath = "/tmp/content" + File.separator + "_temp_" + System.currentTimeMillis
    val downloadedFile = httpUtil.downloadFile(fileUrl, downloadPath)
    assert(downloadedFile.exists())
    FileUtils.deleteDirectory(downloadedFile.getParentFile)
  }

}
