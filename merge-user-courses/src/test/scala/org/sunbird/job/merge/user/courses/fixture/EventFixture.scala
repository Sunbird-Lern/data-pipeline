package org.sunbird.job.merge.user.courses.fixture

object EventFixture {

  val EVENT_1: String =
    """
      |{"eid":"BE_JOB_REQUEST","ets":1563788371969,"mid":"LMS.1563788371969","edata":{"fromAccountId":"user001","toAccountId":"user002"}}
      |""".stripMargin

  val EVENT_2: String =
    """
      |{"eid":"BE_JOB_REQUEST","ets":1563788371969,"mid":"LMS.1563788371969","edata":{"fromAccountId":"toAccountId","toAccountId":"toAccountId"}}
      |""".stripMargin

  val EVENT_3: String =
    """
      |{"eid":"BE_JOB_REQUEST","ets":1563788371969,"mid":"LMS.1563788371969","edata":{"fromAccountId":"toAccountId","toAccountId":"toAccountId"}}
      |""".stripMargin
}
