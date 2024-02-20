package org.sunbird.job.usercache.util

import com.google.gson.Gson
import org.slf4j.LoggerFactory
import org.sunbird.job.Metrics
import org.sunbird.job.cache.DataCache
import org.sunbird.job.util.RestUtil
import org.sunbird.job.usercache.domain.Event
import org.sunbird.job.usercache.task.UserCacheUpdaterConfigV2

import scala.collection.JavaConverters._
import scala.collection.mutable

case class UserReadResult(result: java.util.HashMap[String, Any], responseCode: String, params: Params)
case class Response(firstName: String, lastName: String, encEmail: String, encPhone: String, language: java.util.List[String], rootOrgId: String, profileUserType: java.util.HashMap[String, String],
                    userLocations: java.util.ArrayList[java.util.Map[String, AnyRef]], rootOrg: RootOrgInfo, userId: String, framework: java.util.LinkedHashMap[String, java.util.List[String]], profileUserTypes: java.util.List[java.util.HashMap[String, String]])
case class RootOrgInfo(orgName: String)
case class Params(msgid: String, err: String, status: String, errmsg: String)

object UserMetadataUpdater {

  private lazy val gson = new Gson()

  val logger = LoggerFactory.getLogger("UserMetadataUpdater")

  def execute(userId: String, event: Event, metrics: Metrics, config: UserCacheUpdaterConfigV2, dataCache: DataCache, restUtil: RestUtil, fwCache: FrameworkCacheHandler): mutable.Map[String, AnyRef] = {

    val generalInfo = getGeneralInfo(userId, event, metrics, config, dataCache);
    val regdInfo = if (config.regdUserProducerPid.equals(event.producerPid())) {
      getRegisteredUserInfo(userId, event, metrics, config, dataCache, restUtil, fwCache)
    } else mutable.Map[String, String]()
    generalInfo.++:(regdInfo);
  }

  def getGeneralInfo(userId: String, event: Event, metrics: Metrics, config: UserCacheUpdaterConfigV2, dataCache: DataCache): mutable.Map[String, String] = {
    val userCacheData: mutable.Map[String, String] = mutable.Map[String, String]()
    Option(event.getContextDataId(cDataType = "SignupType")).map(signInType => {
      if (config.userSelfSignedInTypeList.contains(signInType)) {
        userCacheData.put(config.userSignInTypeKey, config.userSelfSignedKey)
      }
      if (config.userValidatedTypeList.contains(signInType)) {
        userCacheData.put(config.userSignInTypeKey, config.userValidatedKey)
      }
    }).orNull
    Option(event.getContextDataId(cDataType = "UserRole")).map(loginType => {
      userCacheData.put(config.userLoginTypeKey, loginType)
    })
    userCacheData;
  }

  @throws(classOf[Exception])
  def getRegisteredUserInfo(userId: String, event: Event, metrics: Metrics, config: UserCacheUpdaterConfigV2, dataCache: DataCache,
                            restUtil: RestUtil, fwCache: FrameworkCacheHandler): mutable.Map[String, AnyRef] = {
    var userCacheData: mutable.Map[String, AnyRef] = mutable.Map[String, AnyRef]()

    //?fields=locations is appended in url to get userLocation in API response
    val userReadRes = gson.fromJson[UserReadResult](restUtil.get(String.format("%s%s",config.userReadApiUrl, userId + "?fields=" + config.userReadApiFields)), classOf[UserReadResult])
    if(event.isValid(userReadRes)) {
      // Inc API Read metrics
      metrics.incCounter(config.apiReadSuccessCount)

      val response = gson.fromJson[Response](gson.toJson(userReadRes.result.get("response")), classOf[Response])
      val framework = response.framework

      //flatten Framework Categories value
      /**
        * Assumption: Framework-id is single valued
        */
      if (!framework.isEmpty) {
        val fwIDList = framework.getOrDefault("id", List().asJava)

        if (!fwIDList.isEmpty){
          val fwID = fwIDList.get(0)
          userCacheData.+=("framework_id" -> fwID)

          val userFrameworkFields = fwCache.getFwCategories(fwID)
          userFrameworkFields.map(key =>{
            val frValue = framework.getOrDefault(key, List().asJava)
            if (!frValue.isEmpty) {
              userCacheData.+=("framework_" + key -> frValue)
            }
          })
        }
      }

      //Location and School Information
      val locationInfo = response.userLocations
      if(null != locationInfo && !locationInfo.isEmpty) {
        locationInfo.forEach(location => {
          location.getOrDefault("type", "").asInstanceOf[String].toLowerCase match {
          case config.schoolKey => userCacheData.put(config.schoolNameKey, location.getOrDefault("name", "").asInstanceOf[String])
              userCacheData.put(config.schoolUdiseCodeKey, location.getOrDefault("code", "").asInstanceOf[String])
          case _ => userCacheData.put(location.getOrDefault("type", "").asInstanceOf[String], location.getOrDefault("name", "").asInstanceOf[String])
          }
        })
      }

      //Flatten User Type and subType
      val profileUserTypes = response.profileUserTypes
      if (null != profileUserTypes && !profileUserTypes.isEmpty) {
        val List(userTypeString, userSubtypeString) = makeUsertypeStrings(profileUserTypes, config)

        userCacheData.+=(config.userTypeKey -> userTypeString, config.userSubtypeKey -> userSubtypeString)
        userCacheData.+=(config.profileUserTypesKey -> new Gson().toJson(profileUserTypes))
      }

      //Personal information
      userCacheData.+=(config.firstName -> response.firstName, config.lastName -> response.lastName,
        config.language -> response.language,
        config.orgnameKey -> response.rootOrg.orgName,
        config.rootOrgId -> response.rootOrgId,
        config.phone -> response.encPhone,
        config.email -> response.encEmail,
        config.userId -> response.userId)

    } else if (config.userReadApiErrors.contains(userReadRes.responseCode.toUpperCase) && userReadRes.params.err.equalsIgnoreCase(config.userAccBlockedErrCode)) { //Skip the events for which response is 400 Bad request
      logger.info(s"User Read API has response as ${userReadRes.responseCode.toUpperCase} for user: ${userId}")
      metrics.incCounter(config.apiReadMissCount)
    } else {
      logger.info(s"User Read API does not have details for user: ${userId}")
      metrics.incCounter(config.apiReadMissCount)
      throw new Exception(s"User Read API does not have details for user: ${userId}")
    }
    userCacheData
  }

  def makeUsertypeStrings(profileUserTypes: java.util.List[java.util.HashMap[String, String]], config: UserCacheUpdaterConfigV2): List[String] = {
    val userTypeValue = mutable.ListBuffer[String]()
    val userSubtypeValue = mutable.ListBuffer[String]()
    profileUserTypes.forEach(userType => {
      val typeVal:String = userType.get(config.`type`)
      val subTypeVal:String = userType.get(config.subtype)

      if (typeVal != null && typeVal.nonEmpty && !userTypeValue.contains(typeVal)) userTypeValue.append(typeVal)
      if (subTypeVal != null && subTypeVal.nonEmpty && !userSubtypeValue.contains(subTypeVal)) userSubtypeValue.append(subTypeVal)
    })

    List(userTypeValue.mkString(","), userSubtypeValue.mkString(","))
  }

  def removeEmptyFields(key: String, dataCache: DataCache, userMetaData: mutable.Map[String, AnyRef]):Unit = {
    val redisRec = dataCache.hgetAllWithRetry(key)
    val removableKeys = redisRec.keySet.diff(userMetaData.keySet)
    logger.info(s"removeEmptyFields: ${key}: removableKeys: ${removableKeys}")
    if(removableKeys.nonEmpty) dataCache.hdelWithRetry(key, removableKeys.toSeq)
  }

  def removeFrameworkFields(key: String, dataCache: DataCache): Unit = {
    val redisRec = dataCache.hgetAllWithRetry(key, false)
    val frameworkKeys = redisRec.keySet.filter(k => k.contains("framework_"))
    logger.info(s"removeFrameworkFields: ${key}: frameworkKeys: ${frameworkKeys}")
    if (frameworkKeys.nonEmpty) dataCache.hdelWithRetry(key, frameworkKeys.toSeq)
  }

  def stringify(userData: mutable.Map[String, AnyRef]): mutable.Map[String, String] = {
    userData.map { f =>
      (f._1, if (!f._2.isInstanceOf[String]) {
        if (null != f._2) {
          new Gson().toJson(f._2)
        } else {
          ""
        }
      } else {
        f._2.asInstanceOf[String].replaceAll("\\[", "\\\\[").replaceAll("\\]", "\\\\]")
      })
    }
  }
}
