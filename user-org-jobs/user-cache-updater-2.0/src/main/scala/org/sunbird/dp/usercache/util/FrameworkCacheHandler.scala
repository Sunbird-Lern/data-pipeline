package org.sunbird.dp.usercache.util

import com.google.common.cache.{Cache, CacheBuilder}
import com.google.gson.Gson
import com.google.gson.internal.LinkedTreeMap
import org.sunbird.dp.core.util.RestUtil
import org.sunbird.dp.usercache.task.UserCacheUpdaterConfigV2

import scala.collection.JavaConverters._

case class FwReadResult(result: java.util.HashMap[String, Any], responseCode: String, params: Params)

class FrameworkCacheHandler(config: UserCacheUpdaterConfigV2, restUtil: RestUtil) {
  private lazy val gson = new Gson()


  private val cache: Cache[String, List[String]] = CacheBuilder.newBuilder()
    .expireAfterWrite(1, java.util.concurrent.TimeUnit.DAYS)
    .build()

  def getFwCategories(fwID: String): List[String] = {
    if (cache.getIfPresent(fwID) != null && cache.getIfPresent(fwID).nonEmpty) {
      cache.getIfPresent(fwID)
    } else {
      val categoriesList = getFwCategoriesFromAPI(fwID)
      cache.put(fwID, categoriesList)
      categoriesList
    }
  }

  def getFwCategoriesFromAPI(fwID: String): List[String] = {
    val fwReadRes = gson.fromJson[FwReadResult](restUtil.get(String.format("%s%s",config.fwReadApiUrl, fwID)), classOf[FwReadResult])

    if(isValidResponse(fwReadRes)){
      var categoriesList = List[String]()
      val categoryRes = fwReadRes.result.asScala.get("framework").get.asInstanceOf[LinkedTreeMap[String, AnyRef]].get("categories").asInstanceOf[java.util.ArrayList[LinkedTreeMap[String, AnyRef]]]
      categoryRes.forEach(category => {
        val categoryCode = category.get("code").asInstanceOf[String]

        categoriesList = categoriesList :+ categoryCode
      })
      categoriesList
    } else List()
  }

  def isValidResponse(fwReadRes: FwReadResult): Boolean = {
    if (fwReadRes.responseCode.toUpperCase.equalsIgnoreCase("OK") && fwReadRes.result.asScala.nonEmpty && fwReadRes.result.asScala.contains("framework")) true else false
  }
}
