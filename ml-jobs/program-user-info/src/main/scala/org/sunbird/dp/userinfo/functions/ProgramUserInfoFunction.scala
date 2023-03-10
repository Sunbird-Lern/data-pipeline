package org.sunbird.dp.userinfo.functions

import com.datastax.driver.core.querybuilder.QueryBuilder
import com.google.gson.reflect.TypeToken
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.ProcessFunction
import org.slf4j.LoggerFactory
import org.sunbird.dp.core.job.{BaseProcessFunction, Metrics}
import org.sunbird.dp.core.util.CassandraUtil
import org.sunbird.dp.userinfo.domain.Event
import org.sunbird.dp.userinfo.task.ProgramUserInfoConfig
import java.lang.reflect.Type
import java.text.SimpleDateFormat
import java.util
import java.util.Date

case class Data(
                 program_id: String,
                 program_externalId: String,
                 program_name: String,
                 pii_consent_required: Boolean,
                 user_id: String,
                 state_code: String,
                 state_id: String,
                 state_name: String,
                 district_code: String,
                 district_id: String,
                 district_name: String,
                 block_code: String,
                 block_id: String,
                 block_name: String,
                 cluster_code: String,
                 cluster_id: String,
                 cluster_name: String,
                 school_code: String,
                 school_id: String,
                 school_name: String,
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

  val mapType: Type = new TypeToken[util.Map[String, AnyRef]]() {}.getType
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

    val userProfileData = scala.collection.mutable.Map[String, String]()

    /**
     * To get the key-values related to user location
     */
    event.user_Location.forEach(f => {
      userProfileData.put(f.get("type") + "_name", Option(f.get("name")).map(_.toString).filter(_.nonEmpty).orNull)
      userProfileData.put(f.get("type") + "_id", Option(f.get("id")).map(_.toString).filter(_.nonEmpty).orNull)
      userProfileData.put(f.get("type") + "_code", Option(f.get("code")).map(_.toString).filter(_.nonEmpty).orNull)
    })

    /**
     * To get the key-values related to user type
     */
    event.user_Types.forEach(f => {
      userProfileData.put("type", Option(f.get("type")).map(_.toString).filter(_.nonEmpty).orNull)
      userProfileData.put("sub_type", Option(f.get("subType")).map(_.toString).filter(_.nonEmpty).orNull)
    })

    /**
     * To get the key-values related to organisation when is_school = false
     */
    event.organisations.forEach(f => {
      if (f.get("isSchool") == false) {
        userProfileData.put("organisation_name", Option(f.get("orgName")).map(_.toString).filter(_.nonEmpty).orNull)
        userProfileData.put("organisation_id", Option(f.get("organisationId")).map(_.toString).filter(_.nonEmpty).orNull)
      }
    })

    /**
     * Storing the parsed JSON event in to a single variable with the help of case class
     */
    val UserData = Data(
      program_id = event.program_id,
      program_externalId = event.program_externalId,
      program_name = event.program_name,
      pii_consent_required = event.pii_consent_required,
      user_id = event.user_id,
      state_code = userProfileData.getOrElse("state_code", null),
      state_id = userProfileData.getOrElse("state_id", null),
      state_name = userProfileData.getOrElse("state_name", null),
      district_code = userProfileData.getOrElse("district_code", null),
      district_id = userProfileData.getOrElse("district_id", null),
      district_name = userProfileData.getOrElse("district_name", null),
      block_code = userProfileData.getOrElse("block_code", null),
      block_id = userProfileData.getOrElse("block_id", null),
      block_name = userProfileData.getOrElse("block_name", null),
      cluster_code = userProfileData.getOrElse("cluster_code", null),
      cluster_id = userProfileData.getOrElse("cluster_id", null),
      cluster_name = userProfileData.getOrElse("cluster_name", null),
      school_code = userProfileData.getOrElse("school_code", null),
      school_id = userProfileData.getOrElse("school_id", null),
      school_name = userProfileData.getOrElse("school_name", null),
      organisation_id = userProfileData.getOrElse("organisation_id", null),
      organisation_name = userProfileData.getOrElse("organisation_name", null),
      user_sub_type = userProfileData.getOrElse("sub_type", null),
      user_type = userProfileData.getOrElse("type", null),
      created_at = created_at.toString,
      updated_at = updated_at.toString
    )
    logger.info("Successfully parsed JSON and stored back the required fields to single case class")

    if (UserData.program_id != null && UserData.user_id != null) {
      val query = QueryBuilder.insertInto(config.dbKeyspace, config.dbTable)
        .value("program_id", UserData.program_id)
        .value("program_externalId", UserData.program_externalId)
        .value("program_name", UserData.program_name)
        .value("pii_consent_required", UserData.pii_consent_required)
        .value("user_id", UserData.user_id)
        .value("state_code", UserData.state_code)
        .value("state_id", UserData.state_id)
        .value("state_name", UserData.state_name)
        .value("district_code", UserData.district_code)
        .value("district_id", UserData.district_id)
        .value("district_name", UserData.district_name)
        .value("block_code", UserData.block_code)
        .value("block_id", UserData.block_id)
        .value("block_name", UserData.block_name)
        .value("cluster_code", UserData.cluster_code)
        .value("cluster_id", UserData.cluster_id)
        .value("cluster_name", UserData.cluster_name)
        .value("school_code", UserData.school_code)
        .value("school_id", UserData.school_id)
        .value("school_name", UserData.school_name)
        .value("organisation_id", UserData.organisation_id)
        .value("organisation_name", UserData.organisation_name)
        .value("user_sub_type", UserData.user_sub_type)
        .value("user_type", UserData.user_type)
        .value("created_at", UserData.created_at)
        .value("updated_at", UserData.updated_at).toString

      cassandraUtil.upsert(query)

      logger.info("Successfully inserted the parsed events to Database with program_Id = " + UserData.program_id)
    }
    else {
      logger.debug("This Event is skipped due to ProgramId or userId missing DocumentId:" + event._id)
    }
  }



}

