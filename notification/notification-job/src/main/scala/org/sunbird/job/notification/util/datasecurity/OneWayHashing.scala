/** */
package org.sunbird.job.notification.util.datasecurity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.slf4j.LoggerFactory;

/**
 * This class will do one way data hashing.
 *
 * @author Manzarul
 */
/**
  * This class will do one way data hashing.
  *
  * @author Manzarul
  */
object OneWayHashing {
    /**
      * This method will encrypt value using SHA-256 . it is one way encryption.
      *
      * @param val String
      * @return String encrypted value or empty in case of exception
      */
    def encryptVal(`val`: String): String = {
        try {
            val md = MessageDigest.getInstance("SHA-256")
            md.update(`val`.getBytes(StandardCharsets.UTF_8))
            val byteData = md.digest
            // convert the byte to hex format method 1
            val sb = new StringBuilder
            var i = 0
            while ( {
                i < byteData.length
            }) {
                sb.append(Integer.toString((byteData(i) & 0xff) + 0x100, 16).substring(1))
                
                {
                    i += 1; i - 1
                }
            }
            //logger.log("encrypted value is==: " + sb.toString());
            return sb.toString
        } catch {
            case e: Exception =>
            
            // logger.log("Error while encrypting", e);
        }
        ""
    }
}

class OneWayHashing private() {
}

