CREATE KEYSPACE IF NOT EXISTS sunbird with replication = {'class':'SimpleStrategy','replication_factor':1};

CREATE TABLE IF NOT EXISTS sunbird.user (
    id text PRIMARY KEY,
    channel text,
    countrycode text,
    createdby text,
    createddate text,
    dob text,
    email text,
    emailverified boolean,
    firstname text,
    flagsvalue int,
    framework map<text, frozen<list<text>>>,
    isdeleted boolean,
    lastname text,
    locationids list<text>,
    loginid text,
    managedby text,
    maskedemail text,
    maskedphone text,
    phone text,
    phoneverified boolean,
    prevusedemail text,
    prevusedphone text,
    recoveryemail text,
    recoveryphone text,
    roles list<text>,
    rootorgid text,
    status int,
    tncacceptedon timestamp,
    tncacceptedversion text,
    updatedby text,
    updateddate text,
    userid text,
    username text,
    usertype text,
    usersubtype text,
    profileusertype text,
    profileusertypes text,
    profilelocation text
) WITH bloom_filter_fp_chance = 0.01
    AND caching = {'keys': 'ALL', 'rows_per_partition': 'NONE'}
    AND comment = ''
    AND compaction = {'class': 'org.apache.cassandra.db.compaction.SizeTieredCompactionStrategy', 'max_threshold': '32', 'min_threshold': '4'}
    AND compression = {'chunk_length_in_kb': '64', 'class': 'org.apache.cassandra.io.compress.LZ4Compressor'}
    AND crc_check_chance = 1.0
    AND dclocal_read_repair_chance = 0.1
    AND default_time_to_live = 0
    AND gc_grace_seconds = 864000
    AND max_index_interval = 2048
    AND memtable_flush_period_in_ms = 0
    AND min_index_interval = 128
    AND read_repair_chance = 0.0
    AND speculative_retry = '99PERCENTILE';

CREATE TABLE IF NOT EXISTS sunbird.usr_external_identity (
    userid text,
    idtype text,
    provider text,
    createdby text,
    createdon timestamp,
    externalid text,
    lastupdatedby text,
    lastupdatedon timestamp,
    originalexternalid text,
    originalidtype text,
    originalprovider text,
    PRIMARY KEY (userid, idtype, provider)
) WITH CLUSTERING ORDER BY (idtype ASC, provider ASC)
    AND bloom_filter_fp_chance = 0.01
    AND caching = {'keys': 'ALL', 'rows_per_partition': 'NONE'}
    AND comment = ''
    AND compaction = {'class': 'org.apache.cassandra.db.compaction.SizeTieredCompactionStrategy', 'max_threshold': '32', 'min_threshold': '4'}
    AND compression = {'chunk_length_in_kb': '64', 'class': 'org.apache.cassandra.io.compress.LZ4Compressor'}
    AND crc_check_chance = 1.0
    AND dclocal_read_repair_chance = 0.1
    AND default_time_to_live = 0
    AND gc_grace_seconds = 864000
    AND max_index_interval = 2048
    AND memtable_flush_period_in_ms = 0
    AND min_index_interval = 128
    AND read_repair_chance = 0.0
    AND speculative_retry = '99PERCENTILE';

CREATE TABLE IF NOT EXISTS sunbird.user_lookup(
    type text,
    value text,
    userId text,
    primary key((type,value))
    );

CREATE TABLE IF NOT EXISTS sunbird.user_organisation (
  userid text,
  organisationid text,
  addedby text,
  addedbyname text,
  approvaldate text,
  approvedby text,
  hashtagid text,
  id text,
  isapproved boolean,
  isdeleted boolean,
  isrejected boolean,
  orgjoindate text,
  orgleftdate text,
  position text,
 roles list<text>,
 updatedby text,
 updateddate text,
PRIMARY KEY (userid, organisationid));

INSERT INTO sunbird.user (id, userid, locationids, firstname, lastname, usertype, usersubtype, email, phone, rootorgid, channel, isdeleted,  profilelocation, status)
   VALUES ('02c4e0dc-3e25-4f7d-b811-242c73e24a01', '02c4e0dc-3e25-4f7d-b811-242c73e24a01', ['0dd2b51d-baa2-4403-bf1e-012a970b323f', 'f8228633-7742-454b-8f61-519c6a875809', 'f8228633-7742-454b-8f61-519c6a875810', 'f8228633-7742-454b-8f61-519c6a875811'], 'localuser118f', 'localuser118l','teacher', 'principal','PEhQxQlaMdJEXOzShY0NAiKg4LqC2xUDE4InNodhG/fJMhq69iAPzseEdYAlMPWegxJaAnH+tJwc\nZuqPxJCtJkiGfwlCUEj5B41z4/RjH/7XowwzRVZXH0jth3IW4Ik8TQtMGOn7lhkDdxs1iV8l8A==','1wsQrmy8Q1T4gFa+MOJsirdQC2yhyJsm2Rgj229s2b5Hk/JLNNnHMz6ywhgzYpgcQ6QILjcTLl7z\n7s4aRbsrWw==', '012500530695766016224', 'tn', False, '[{"type":"state","id":"0dd2b51d-baa2-4403-bf1e-012a970b323f"}, {"type":"district","id":"f8228633-7742-454b-8f61-519c6a875809"}, {"type":"block","id":"f8228633-7742-454b-8f61-519c6a875810"}, {"type":"cluster","id":"f8228633-7742-454b-8f61-519c6a875811"}]', 1);


INSERT INTO sunbird.user_organisation (id,userid,organisationid) VALUES ('000000000001', '56c2d9a3-fae9-4341-9862-4eeeead2e9a1','org4');

INSERT INTO sunbird.usr_external_identity (userid, idtype, provider, originalexternalid, originalprovider ) VALUES('56c2d9a3-fae9-4341-9862-4eeeead2e9a1', 'tn', 'tn', 'usrext1', 'tn');