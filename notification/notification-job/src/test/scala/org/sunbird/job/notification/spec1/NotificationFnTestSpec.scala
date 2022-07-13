package org.sunbird.job.notification.spec1

import com.typesafe.config.{Config, ConfigFactory}
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.java.typeutils.TypeExtractor
import org.apache.http.impl.client.{CloseableHttpClient, HttpClients}
import org.junit.runner.RunWith
import org.mockito.{ArgumentMatchers, Mockito}
import org.mockito.Mockito.{doNothing, doReturn, when}
import org.powermock.api.mockito.PowerMockito
import org.powermock.api.mockito.PowerMockito.{doReturn, when}
import org.powermock.core.classloader.annotations.{PowerMockIgnore, PrepareForTest}
import org.powermock.modules.junit4.PowerMockRunner
import org.sunbird.job.Metrics
import org.sunbird.job.cache.{DataCache, RedisConnect}
import org.sunbird.job.notification.task.NotificationConfig
import org.sunbird.job.util._
import org.sunbird.spec.BaseTestSpec
import redis.clients.jedis.Jedis
import org.sunbird.job.notification.domain.{Event, NotificationUtil}
import org.sunbird.job.notification.fixture.EventFixture
import org.sunbird.job.notification.function.NotificationFunction
import org.sunbird.notification.beans.{EmailConfig, EmailRequest, SMSConfig}
import org.sunbird.notification.email.service.{IEmailFactory, IEmailService}
import org.sunbird.notification.fcm.provider.{IFCMNotificationService, NotificationFactory}
import org.sunbird.notification.fcm.providerImpl.FCMHttpNotificationServiceImpl
import org.sunbird.notification.sms.provider.ISmsProvider
import org.sunbird.notification.sms.providerimpl.{Msg91SmsProviderFactory, Msg91SmsProviderImpl}
import org.sunbird.notification.utils.{PropertiesCache, SMSFactory}

import scala.collection.JavaConverters._

@RunWith(classOf[PowerMockRunner])
@PowerMockIgnore(Array("javax.management.*", "javax.net.ssl.*", "javax.security.*"))
@PrepareForTest(Array(classOf[IFCMNotificationService], classOf[NotificationFactory]))
class NotificationFnTestSpec extends BaseTestSpec {
    implicit val stringTypeInfo: TypeInformation[String] = TypeExtractor.getForClass(classOf[String])
    //var cassandraUtil: CassandraUtil = _
    var emailFactory : IEmailFactory = _
    var emailService : IEmailService = _
    private var smsProvider: ISmsProvider = _
    val config: Config = ConfigFactory.load("test.conf")
    lazy val jobConfig = new NotificationConfig(config)
    val emailConfig = new EmailConfig(jobConfig.mail_server_from_email, jobConfig.mail_server_username, jobConfig.mail_server_password, jobConfig.mail_server_host, jobConfig.mail_server_port)
    
    val httpUtil = new HttpUtil
    val mockHttpUtil:HttpUtil = mock[HttpUtil](Mockito.withSettings().serializable())
    val notificationUtil:NotificationUtil = mock[NotificationUtil](Mockito.withSettings().serializable())
    val notificationFactory : NotificationFactory = mock[NotificationFactory](Mockito.withSettings())
    val iFCMNotificationService : IFCMNotificationService = mock[IFCMNotificationService](Mockito.withSettings())
    /*emailFactory = mock[IEmailFactory](Mockito.withSettings())
    emailService = mock[IEmailService](Mockito.withSettings())
    org.mockito.Mockito.when(emailFactory.create(emailConfig)).thenReturn(emailService)
    PowerMockito.spy(classOf[NotificationFactory])
    //PowerMockito.mockStatic(classOf[NotificationFactory])
    PowerMockito.doAnswer(_ => iFCMNotificationService).when(classOf[NotificationFactory], "getHttpInstance")*/

    //PowerMockito.doReturn(iFCMNotificationService).when(classOf[NotificationFactory], "getHttpInstance")
    //PowerMockito.doAnswer(_ => iFCMNotificationService).when(classOf[NotificationFactory])
    //when(NotificationFactory.getHttpInstance(c, Mockito.anyMap)).thenReturn("anyUserId")
    //when(notificationFactory. (ArgumentMatchers.contains(jobConfig.userReadApi), ArgumentMatchers.any[Map[String, String]]())).thenReturn(HTTPResponse(200, EventFixture.USER_1))
    //val factory = NotificationFactory.getInstance(NotificationFactory.instanceType.httpClinet.name)
    
    val metricJson = s"""{"${jobConfig.totalEventsCount}": 0, "${jobConfig.skippedEventCount}": 0}"""
    val mockMetrics = mock[Metrics](Mockito.withSettings().serializable())

    override protected def beforeAll(): Unit = {
        super.beforeAll()
        /*PowerMockito.spy(classOf[SMSFactory])
        smsProvider = mock[ISmsProvider](Mockito.withSettings())
        PowerMockito.doAnswer(_ => smsProvider).when(classOf[SMSFactory], "getInstance", ArgumentMatchers.any[String](), ArgumentMatchers.any[SMSConfig]())*/
        emailFactory = mock[IEmailFactory](Mockito.withSettings())
        emailService = mock[IEmailService](Mockito.withSettings())
        org.mockito.Mockito.when(emailFactory.create(emailConfig)).thenReturn(emailService)
        smsProvider = mock[ISmsProvider](Mockito.withSettings())
        //org.mockito.Mockito.when(NotificationFactory.getHttpInstance(emailConfig)).thenReturn(iFCMNotificationService)
        //PowerMockito.spy(classOf[NotificationFactory])
        //PowerMockito.mockStatic(classOf[NotificationFactory])
        //PowerMockito.doAnswer(_ => iFCMNotificationService).when(classOf[NotificationFactory], "getHttpInstance")
        //doReturn()
        //PowerMockito.doReturn(iFCMNotificationService).when(classOf[NotificationFactory], "getHttpInstance")
        //doReturn(iFCMNotificationService).when(classOf[NotificationFactory], "getHttpInstance")
        //PowerMockito.doAnswer(_ => iFCMNotificationService).when(classOf[NotificationFactory], "getHttpInstance")
    }

    override protected def afterAll(): Unit = {
        super.afterAll()
    }


    "Notification-Email " should " Should send email " in {
        val event = new Event(JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.EVENT_1), 0, 0)
        val requestMap = event.edataMap.get("request").get.asInstanceOf[scala.collection.immutable.Map[String, Object]]
        val notificationMap = requestMap.get("notification").get.asInstanceOf[scala.collection.immutable.HashMap[String, AnyRef]]
        org.mockito.Mockito.when(notificationUtil.sendEmail(ArgumentMatchers.any[EmailRequest]())).thenReturn(true)
        org.mockito.Mockito.when(emailService.sendEmail(ArgumentMatchers.any[EmailRequest]())).thenReturn(true)
        val notificationFn = new NotificationFunction(jobConfig, notificationUtil)
        val sentEmail = notificationFn.sendEmailNotification(notificationMap)
        sentEmail shouldNot be(false)
    }
    
    "Notification-Mobile " should " Should send message " in {
        val event = new Event(JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.EVENT_2), 0, 0)
        val msgId = event.msgId;
        val requestMap = event.edataMap.get("request").get.asInstanceOf[scala.collection.immutable.Map[String, Object]]
        val notificationMap = requestMap.get("notification").get.asInstanceOf[scala.collection.immutable.HashMap[String, AnyRef]]
        org.mockito.Mockito.when(notificationUtil.sendSmsNotification(ArgumentMatchers.any[java.util.List[String]], ArgumentMatchers.any[String]())).thenReturn(true)
        org.mockito.Mockito.when(smsProvider.bulkSms(ArgumentMatchers.any[java.util.List[String]], ArgumentMatchers.any[String]())).thenReturn(true)
        val notificationFn = new NotificationFunction(jobConfig, notificationUtil)
        val sentEmail = notificationFn.sendSmsNotification(notificationMap, msgId)
        sentEmail shouldNot be(false)
    }


    /*"CertPreProcess When User Last Name is String Null " should " Should get Valid getRecipientName " in {
        val event = new Event(JSONUtil.deserialize[java.util.Map[String, Any]](EventFixture.EVENT_1), 0, 0)
        val template = ScalaJsonUtil.deserialize[Map[String, String]](EventFixture.TEMPLATE_1)
        when(mockHttpUtil.get(ArgumentMatchers.contains(jobConfig.userReadApi), ArgumentMatchers.any[Map[String, String]]())).thenReturn(HTTPResponse(200, EventFixture.USER_4_NULL_STRING_VALUE_LASTNAME))
        when(mockHttpUtil.get(ArgumentMatchers.contains(jobConfig.contentReadApi), ArgumentMatchers.any[Map[String, String]]())).thenReturn(HTTPResponse(200, EventFixture.CONTENT_1))
        jedis.select(jobConfig.contentCacheStore)
        jedis.set("content_001", """{"identifier":"content_001","contentType": "selfAssess"}""")
        val certEvent: String = new CollectionCertPreProcessorFn(jobConfig, mockHttpUtil)(stringTypeInfo, cassandraUtil).issueCertificate(event, template)(cassandraUtil, cache, contentCache, mockMetrics, jobConfig, mockHttpUtil)
        certEvent shouldNot be(null)
        getRecipientName(certEvent) should be("Manju")
    }
*/

    /*def getRecipientName(certEvent: String): String = {
        import java.util
        import com.google.gson.Gson
        import com.google.gson.internal.LinkedTreeMap
        val gson = new Gson()
        val certMapEvent = gson.fromJson(certEvent, new java.util.LinkedHashMap[String, Any]().getClass)
        val certEdata = certMapEvent.getOrDefault("edata", new util.LinkedHashMap[String, Any]()).asInstanceOf[LinkedTreeMap[String, Any]]
        val certUserData = certEdata.getOrDefault("data", new util.LinkedList[util.LinkedHashMap[String, Any]]()).asInstanceOf[util.ArrayList[LinkedTreeMap[String, Any]]]
        certUserData.get(0).get("recipientName").asInstanceOf[String]
    }*/

}
