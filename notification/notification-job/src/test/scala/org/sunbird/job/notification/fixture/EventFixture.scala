package org.sunbird.job.notification.fixture

object EventFixture {

  val EVENT_1: String =
    """
      | {"actor":{"id":"BroadCast Topic Notification","type":"System"},"eid":"BE_JOB_REQUEST","mid":"NS.1646230422793.70c10f26-631b-4d55-8aaa-fbe52b79cbc1","trace":{"X-Request-ID":null,"X-Trace-Enabled":"false"},"ets":1646230422793,"edata":{"action":"broadcast-topic-notification-all","iteration":1,"request":{"notification":{"mode":"email","deliveryType":"message","config":{"sender":"support@sunbird.com","topic":null,"otp":null,"subject":"Completion certificate"},"ids":["harip@ilimi.in"],"template":{"id":null,"data":"Hi","params":{"stateImgUrl":"https://sunbirddev.blob.core.windows.net/orgemailtemplate/img/File-0128212938260643843.png","regardsperson":"Chairperson","regards":"Minister of Gujarat","firstName":"Hari","TraningName":"test-cert-notification","heldDate":"16-12-2019","body":"email body"}},"rawData":null}}},"context":{"pdata":{"ver":"1.0","id":"org.sunbird.platform"}},"object":{"id":"93a06b829d13c9fa797ea641f484e5d38ce28868fbd75014852cbe413515177c","type":"TopicNotifyAll"}}
      |""".stripMargin

  val EVENT_2: String ="""{"actor":{"id":"BroadCast Topic Notification","type":"System"},"eid":"BE_JOB_REQUEST","mid":"NS.1657555782237.a0603d3a-f668-47e1-940f-4620786f029b","trace":{"X-Request-ID":null,"X-Trace-Enabled":"false"},"ets":1657555782237,"edata":{"action":"broadcast-topic-notification-all","iteration":1,"request":{"notification":{"mode":"phone","deliveryType":"message","config":{"sender":null,"topic":null,"otp":null,"subject":null},"ids":["8050688698"],"template":{"id":null,"data":"You have successfully completed Sunbird training.","params":{"courseName":"Sunbird training"}},"rawData":null}}},"context":{"pdata":{"ver":"1.0","id":"org.sunbird.platform"}},"object":{"id":"f5e7243feabb343b029f154341c2d55dacb92febb0c1b0349ee0676a02c9b816","type":"TopicNotifyAll"}}""".stripMargin
  
}
