package org.sunbird.userdelete.fixture

object EventFixture {

  val VALID_EVENT: String = """{"eid":"BE_JOB_REQUEST", "ets": 1669196680963, "mid": "LP.1669196680963.0e6ac196-e57d-40bb-ab39-f5ec479da3e6", "actor": {"id": "User Ownership Transfer", "type": "System"}, "context":{"pdata":{"ver":"1.0","id":"org.sunbird.platform"}, "channel":"01309282781705830427","env":"sunbirdstaging"},"object":{"ver":"1669196652993","id":"02c4e0dc-3e25-4f7d-b811-242c73e24a01"},"edata": {"action":"user-ownership-transfer","iteration":1,"userId":"5deed393-6e04-449a-b98d-7f0fbf88f22e","organisationId":"01309282781705830427" }}"""

  val EVENT_WITH_DIFFERENT_USERID: String = """{"eid":"BE_JOB_REQUEST", "ets": 1669196680963, "mid": "LP.1669196680963.0e6ac196-e57d-40bb-ab39-f5ec479da3e6", "actor": {"id": "User Ownership Transfer", "type": "System"}, "context":{"pdata":{"ver":"1.0","id":"org.sunbird.platform"}, "channel":"01309282781705830427","env":"sunbirdstaging"},"object":{"ver":"1669196652993","id":"02c4e0dc-3e25-4f7d-b811-242c73e24a01"},"edata": {"action":"user-ownership-transfer","iteration":1,"userId":"7f0fbf88f22e-6e04-449a-b98d-5deed393","organisationId":"01309282781705830427" }}"""

  val EVENT_WITH_USERID_NULL: String = """{"eid":"BE_JOB_REQUEST", "ets": 1669196680963, "mid": "LP.1669196680963.0e6ac196-e57d-40bb-ab39-f5ec479da3e6", "actor": {"id": "User Ownership Transfer", "type": "System"}, "context":{"pdata":{"ver":"1.0","id":"org.sunbird.platform"}, "channel":"01309282781705830427","env":"sunbirdstaging"},"object":{"ver":"1669196652993","id":"02c4e0dc-3e25-4f7d-b811-242c73e24a01"},"edata": {"action":"user-ownership-transfer","iteration":1,"userId":"","organisationId":"01309282781705830427" }}"""

}
