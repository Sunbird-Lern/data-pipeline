package org.sunbird.incredible.processor.store

import java.io.File

import org.apache.commons.lang3.StringUtils
import org.sunbird.cloud.storage.BaseStorageService
import org.sunbird.cloud.storage.factory.StorageConfig
import org.sunbird.cloud.storage.factory.StorageServiceFactory
import org.sunbird.incredible.pojos.exceptions.ServerException
import org.sunbird.incredible.{JsonKeys, StorageParams, UrlManager}


class StorageService(storageParams: StorageParams) extends Serializable {

  var storageService: BaseStorageService = _
  val storageType: String = storageParams.cloudStorageType
  val supportedCloudStorageType: List[String] = List(JsonKeys.AZURE, JsonKeys.AWS, JsonKeys.GCLOUD)

  @throws[Exception]
  def getService: BaseStorageService = {
    if (null == storageService) {
      if (supportedCloudStorageType.contains(storageType)) {
        storageService = StorageServiceFactory.getStorageService(StorageConfig(storageType, storageParams.storageKey, storageParams.storageSecret))
      } else {
        throw new ServerException("ERR_INVALID_CLOUD_STORAGE", "Error while initialising cloud storage")
      }
    }
    storageService
  }

  def getContainerName: String = {
    if (supportedCloudStorageType.contains(storageType))
      storageParams.containerName
    else
      throw new ServerException("ERR_INVALID_CLOUD_STORAGE", "Container name not configured.")
  }

  def uploadFile(path: String, file: File): String = {
    val objectKey = path + file.getName
    val containerName = getContainerName
    val url = getService.upload(containerName, file.getAbsolutePath, objectKey, Option.apply(false), Option.apply(1), Option.apply(5), Option.empty)
    UrlManager.getSharableUrl(url, containerName)
  }


}
