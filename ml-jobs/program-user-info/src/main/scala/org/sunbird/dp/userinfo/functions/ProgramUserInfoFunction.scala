package org.sunbird.dp.userinfo.functions

import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.slf4j.LoggerFactory
import org.sunbird.dp.core.job.{BaseProcessFunction, Metrics}
import org.sunbird.dp.core.util.CassandraUtil
import org.sunbird.dp.userinfo.domain.Event
import org.sunbird.dp.userinfo.task.ProgramUserInfoConfig
import org.sunbird.dp.userinfo.util.Commons

import java.text.SimpleDateFormat
import java.util
import java.util.Date

case class Data(
                 program_id: String,
                 program_externalid: String,
                 program_name: String,
                 pii_consent_required: Boolean,
                 user_id: String,
                 organisation_id: String,
                 organisation_name: String,
                 user_sub_type: String,
                 user_type: String,
                 created_at: String,
                 updated_at: String
               )

class ProgramUserInfoFunction(config: ProgramUserInfoConfig,
                              @transient var cassandraUtil: CassandraUtil = null
                             ) extends BaseProcessFunction[Event, Event](config) {

  private[this] val logger = LoggerFactory.getLogger(classOf[ProgramUserInfoFunction])

  override def metricsList() = List()

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    cassandraUtil = new CassandraUtil(config.dbHost, config.dbPort, config.isMultiDCEnabled)
  }

  override def close(): Unit = {
    super.close()
  }

  /**
   * This method will convert the incoming date-time string to required Date format "yyyy-MM-dd"
   *
   * @param dateStr date will be passed as a string in this format "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
   * @return
   */
  def getDateOnly(dateStr: String): java.sql.Date = {
    val inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    val date = inputFormat.parse(dateStr)
    val outputFormat = new SimpleDateFormat("yyyy-MM-dd")
    val formattedDate = outputFormat.format(date)
    java.sql.Date.valueOf(formattedDate)
  }

  override def processElement(event: Event,
                              context: ProcessFunction[Event, Event]#Context,
                              metrics: Metrics): Unit = {

    val created_at: Date = getDateOnly(event.created_at_string)
    val updated_at: Date = getDateOnly(event.updated_at_string)

    /**
     * To get the key-values related to user location
     */
    val userLocationData = new util.HashMap[String, String]
    if(event.user_Location!= null && event.user_Location.isEmpty==false) {
      event.user_Location.forEach(f => {
        if (f != null && f.get("type") != null && !f.get("type").toString().isBlank()) {
          if (f != null && f.get("name") != null && !f.get("name").toString().isBlank()) {
            userLocationData.put(f.get("type") + "_name", f.get("name").toString)
          }
          if (f != null && f.get("id") != null && !f.get("id").toString().isBlank()) {
            userLocationData.put(f.get("type") + "_id", f.get("id").toString)
          }
          if (f != null && f.get("code") != null && !f.get("code").toString().isBlank()) {
            userLocationData.put(f.get("type") + "_code", f.get("code").toString)
          }
        }
      })
    }

    /**
     * To get the key-values related to user type
     */
    val userTypeData = new util.HashMap[String, String]
    if (event.user_Types != null && event.user_Types.isEmpty == false) {
      val subTypeBuilder = new StringBuilder
      val subTypeSet = new util.HashSet[String]
      event.user_Types.forEach(f => {
        userTypeData.put("type", Option(f.get("type")).map(_.toString).filter(_.nonEmpty).map(_.toLowerCase).orNull)
        val subType = Option(f.get("subType")).map(_.toString).filter(_.nonEmpty).map(_.toUpperCase).orNull
        if (subType != null && !subTypeSet.contains(subType)) {
          if (subTypeBuilder.length() > 0) {
            subTypeBuilder.append(",")
          }
          subTypeBuilder.append(subType)
          subTypeSet.add(subType)
        }
      })
      userTypeData.put("sub_type", if (subTypeBuilder.toString().isBlank) null else subTypeBuilder.toString())
    }

    /**
     * To get the key-values related to user organisation
     */
    val organisationsData = new util.HashMap[String, String]
    if(event.organisations!= null) {
      organisationsData.put("organisation_id", Option(event.organisations.get("id")) match {
        case Some(s: String) if s.trim.nonEmpty => s
        case _ => null
      })
      organisationsData.put("organisation_name", Option(event.organisations.get("orgName")) match {
        case Some(s: String) if s.trim.nonEmpty => s
        case _ => null
      })
    }

    /**
     * Storing the parsed JSON event in to a single variable with the help of case class
     */
    val UserData = Data(
      program_id = event.program_id,
      program_externalid = event.program_externalId,
      program_name = event.program_name,
      pii_consent_required = event.pii_consent_required,
      user_id = event.user_id,
      organisation_id = organisationsData.getOrDefault("organisation_id", null),
      organisation_name = organisationsData.getOrDefault("organisation_name", null),
      user_sub_type = userTypeData.getOrDefault("sub_type", null),
      user_type = userTypeData.getOrDefault("type", null),
      created_at = created_at.toString,
      updated_at = updated_at.toString
    )
    logger.info("Successfully parsed JSON and stored back the required fields to single case class")

    val insertData = new util.HashMap[String, AnyRef]
    insertData.put("program_id", UserData.program_id)
    insertData.put("program_externalid", UserData.program_externalid)
    insertData.put("program_name", UserData.program_name)
    insertData.put("pii_consent_required", UserData.pii_consent_required.asInstanceOf[AnyRef])
    insertData.put("user_id", UserData.user_id)
    if(event.user_Location!= null) {
      insertData.put("user_locations", userLocationData)
    }
    if(event.organisations!=null) {
      insertData.put("organisation_id", UserData.organisation_id)
      insertData.put("organisation_name", UserData.organisation_name)
    }
    if(event.user_Types!=null) {
      insertData.put("user_sub_type", UserData.user_sub_type)
      insertData.put("user_type", UserData.user_type)
    }
    insertData.put("created_at", UserData.created_at)
    insertData.put("updated_at", UserData.updated_at)


    /**
     * Insert flattened data into cassandra database
     */

    if (UserData.program_id != null && UserData.user_id != null) {
      val commons = new Commons(cassandraUtil)
      commons.insertDbRecord(config.dbKeyspace, config.dbTable, insertData)
      logger.info("Successfully inserted the parsed events to Database with program_Id = " + UserData.program_id)

    }
    else {
      logger.debug("This Event is skipped due to ProgramId or userId missing DocumentId:" + event._id)
    }
  }

}
