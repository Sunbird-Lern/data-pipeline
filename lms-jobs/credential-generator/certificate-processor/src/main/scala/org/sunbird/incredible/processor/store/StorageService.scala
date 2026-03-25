package org.sunbird.incredible.processor.store

import org.sunbird.cloud.storage.{IStorageService, StorageConfig, StorageServiceFactory}
import org.sunbird.cloud.storage.StorageConfig.{AuthType, StorageType}
import org.sunbird.incredible.pojos.exceptions.ServerException
import org.sunbird.incredible.{StorageParams, UrlManager}

import java.io.File


class StorageService(storageParams: StorageParams) extends Serializable {

  var storageService: IStorageService = _

  @throws[Exception]
  def getService: IStorageService = {
    if (storageService == null) {
      val authType = resolveAuthType(storageParams.cloudStorageAuthType)
      val builder = StorageConfig.builder(resolveStorageType(storageParams.cloudStorageType))
        .storageKey(storageParams.storageKey)
        .authType(authType)
      if (storageParams.storageEndpoint != null && storageParams.storageEndpoint.nonEmpty)
        builder.endPoint(storageParams.storageEndpoint)
      if (authType == AuthType.ACCESS_KEY)
        builder.storageSecret(storageParams.storageSecret)
      storageService = StorageServiceFactory.getStorageService(builder.build())
    }
    storageService
  }

  def uploadFile(path: String, file: File): String = {
    val objectKey = path + file.getName
    val containerName = storageParams.containerName
    val url = getService.upload(containerName, file.getAbsolutePath, objectKey, false, 1, 5, null)
    UrlManager.getSharableUrl(url, containerName)
  }

  private def resolveStorageType(storageType: String): StorageType = {
    storageType.toLowerCase match {
      case "azure"  => StorageType.AZURE
      case "aws"    => StorageType.AWS
      case "gcloud" => StorageType.GCLOUD
      case "oci"    => StorageType.OCI
      case "cephs3" => StorageType.CEPHS3
      case other    => throw new ServerException("ERR_INVALID_CLOUD_STORAGE_TYPE", s"Unknown cloud storage type: $other")
    }
  }

  private def resolveAuthType(authTypeStr: String): AuthType = {
    try {
      AuthType.valueOf(authTypeStr.replace("-", "_").toUpperCase)
    } catch {
      case _: IllegalArgumentException => AuthType.ACCESS_KEY
    }
  }

}
