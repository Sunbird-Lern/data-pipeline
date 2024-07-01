package org.sunbird.dp.core.exception

class ServerException(code: String, msg: String, cause: Throwable = null) extends Exception(msg, cause)