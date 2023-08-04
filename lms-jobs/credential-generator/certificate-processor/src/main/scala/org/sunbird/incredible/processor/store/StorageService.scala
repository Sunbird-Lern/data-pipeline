package org.sunbird.incredible.processor.store

import org.sunbird.cloud.storage.BaseStorageService
import org.sunbird.cloud.storage.factory.{StorageConfig, StorageServiceFactory}
import org.sunbird.incredible.pojos.exceptions.ServerException
import org.sunbird.incredible.{StorageParams, UrlManager}

import java.io.File


class StorageService(storageParams: StorageParams) extends Serializable {

  var storageService: BaseStorageService = _

  @throws[Exception]
  def getService: BaseStorageService = {
    if (null == storageService) {
        storageService = StorageServiceFactory.getStorageService(StorageConfig(storageParams.cloudStorageType, storageParams.storageKey, storageParams.storageSecret,storageParams.storageEndpoint))
      } else {
        throw new ServerException("ERR_INVALID_CLOUD_STORAGE", "Error while initialising cloud storage")
      }
    storageService
  }

  def uploadFile(path: String, file: File): String = {
    val objectKey = path + file.getName
    val containerName = storageParams.containerName
    val url = getService.upload(containerName, file.getAbsolutePath, objectKey, Option.apply(false), Option.apply(1), Option.apply(5), Option.empty)
    UrlManager.getSharableUrl(url, containerName)
  }


}
