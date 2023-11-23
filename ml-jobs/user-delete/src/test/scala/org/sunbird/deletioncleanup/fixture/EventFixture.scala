package org.sunbird.deletioncleanup.fixture

object EventFixture {

  val EVENT_1: String =
    """
      | {"eid":"BE_JOB_REQUEST", "ets": 1669196680963, "mid": "LP.1669196680963.0e6ac196-e57d-40bb-ab39-f5ec479da3e6", "actor": {"id": "User Ownership Transfer", "type": "System"}, "context":{"pdata":{"ver":"1.0","id":"org.sunbird.platform"}, "channel":"01309282781705830427","env":"sunbirdstaging"},"object":{"ver":"1669196652993","id":"02c4e0dc-3e25-4f7d-b811-242c73e24a01"},"edata": {"action":"user-ownership-transfer","iteration":1,"userId":"5deed393-6e04-449a-b98d-7f0fbf88f22e","organisationId":"01309282781705830427" }}
      |""".stripMargin

}
