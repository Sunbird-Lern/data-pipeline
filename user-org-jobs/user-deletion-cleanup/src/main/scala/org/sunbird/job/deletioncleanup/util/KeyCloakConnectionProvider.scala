package org.sunbird.job.deletioncleanup.util

import org.apache.commons.lang3.StringUtils
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl
import org.keycloak.admin.client.Keycloak
import org.keycloak.admin.client.KeycloakBuilder
import org.slf4j.LoggerFactory
import org.sunbird.job.deletioncleanup.task.UserDeletionCleanupConfig

class KeyCloakConnectionProvider(config: UserDeletionCleanupConfig) {
  private[this] val logger = LoggerFactory.getLogger(classOf[KeyCloakConnectionProvider])
  private var keycloak: Keycloak = null


  /**
   * This method will provide the keycloak connection from environment variable. if environment
   * variable is not set then it will return null.
   *
   * @return Keycloak
   */
  @throws[Exception]
  private def initialiseConnection: Keycloak = {
    val SSO_URL = config.keycloakBaseUrl
    val username = config.keycloakUsername
    val password = config.keycloakPassword
    val CLIENT_ID = config.keycloakClientId
    val clientSecret = config.keycloakClientSecret
    val SSO_REALM = config.keycloakRealm
    val SSO_POOL_SIZE = config.keycloakPoolSize
    if (StringUtils.isBlank(SSO_URL) || StringUtils.isBlank(username) || StringUtils.isBlank(password) || StringUtils.isBlank(CLIENT_ID) || StringUtils.isBlank(SSO_REALM)) {
      logger.info("key cloak connection is not provided by Environment variable.")
      return null
    }

    val keycloakBuilder = KeycloakBuilder.builder.serverUrl(SSO_URL).realm(SSO_REALM).username(username).password(password).clientId(CLIENT_ID).resteasyClient(new ResteasyClientBuilderImpl().connectionPoolSize(SSO_POOL_SIZE).build)
    if (StringUtils.isNotBlank(clientSecret)) {
      keycloakBuilder.clientSecret(clientSecret)
      logger.info("KeyCloakConnectionProvider:initialiseEnvConnection client sceret is provided.")
    }
    keycloakBuilder.grantType("client_credentials")
    keycloak = keycloakBuilder.build()
    logger.info("key cloak instance is created from Environment variable settings .")
    registerShutDownHook()
    keycloak
  }

  private[util] object ResourceCleanUp extends Thread {
    override def run() = if (null != keycloak) keycloak.close
  }

  /** Register the hook for resource clean up. this will be called when jvm shut down. */
  def registerShutDownHook() = {
    val runtime = Runtime.getRuntime
    runtime.addShutdownHook(ResourceCleanUp)
  }

  def getConnection: Keycloak = {
    if (keycloak != null) return keycloak
    else try return initialiseConnection
    catch {
      case e: Exception =>
        logger.error("getConnection : " + e.getMessage, e)
    }
    null
  }


}