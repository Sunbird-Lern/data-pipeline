package org.sunbird.incredible

case class CertificateConfig(basePath: String, encryptionServiceUrl: String, contextUrl: String, evidenceUrl: String,
                             issuerUrl: String, signatoryExtension: String, accessCodeLength: Double = 6)


case class StorageParams(cloudStorageType: String, storageKey: String, storageSecret: String, containerName: String,storageEndpoint: Option[String])