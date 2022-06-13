CREATE KEYSPACE sunbird_courses with replication = {'class':'SimpleStrategy','replication_factor':1};
CREATE TABLE sunbird_courses.user_content_consumption (
    userid text,
    courseid text,
    batchid text,
    contentid text,
    completedcount int,
    datetime timestamp,
    last_access_time timestamp,
    last_completed_time timestamp,
    last_updated_time timestamp,
    lastaccesstime text,
    lastcompletedtime text,
    lastupdatedtime text,
    progress int,
    status int,
    viewcount int,
    PRIMARY KEY (userid, courseid, batchid, contentid)
) WITH CLUSTERING ORDER BY (courseid ASC, batchid ASC, contentid ASC);

CREATE TABLE sunbird_courses.user_activity_agg (
    activity_type text,
    activity_id text,
    user_id text,
    context_id text,
    agg map<text, int>,
    agg_details list<text>,
    agg_last_updated map<text, timestamp>,
    aggregates map<text, double>,
    PRIMARY KEY ((activity_type, activity_id, user_id), context_id)
) WITH CLUSTERING ORDER BY (context_id ASC);

CREATE TABLE sunbird_courses.user_enrolments (
    userid text,
    courseid text,
    batchid text,
    active boolean,
    addedby text,
    certificates list<frozen<map<text, text>>>,
    completedon timestamp,
    completionpercentage int,
    contentstatus map<text, int>,
    datetime timestamp,
    enrolleddate text,
    issued_certificates list<frozen<map<text, text>>>,
    lastreadcontentid text,
    lastreadcontentstatus int,
    progress int,
    status int,
    PRIMARY KEY (userid, courseid, batchid)
) WITH CLUSTERING ORDER BY (courseid ASC, batchid ASC);

// fromuserid
INSERT INTO sunbird_courses.user_enrolments(userid, courseid, batchid,issued_certificates,completedon) VALUES ('user001','do_11309999837886054415','0131000245281587206',[{'identifier': 'certificateId', 'lastIssuedOn': '2019-08-21', 'name': '100PercentCompletionCertificate', 'token': 'P4L3Y9'}], toTimeStamp(toDate(now()))) ;
INSERT INTO sunbird_courses.user_enrolments(userid, courseid, batchid,issued_certificates,completedon) VALUES ('user001','do_11309999837886054415','0131000245281587205',[{'identifier': 'certificateId', 'lastIssuedOn': '2019-08-25', 'name': '100PercentCompletionCertificate', 'token': 'P4L3Y5'}], toTimeStamp(toDate(now()))) ;
INSERT INTO sunbird_courses.user_content_consumption(userid, courseid, batchid, contentid, last_access_time,progress,status,viewcount) VALUES ('user001','do_11309999837886054415','0131000245281587206','do_2134795598368931841927','2022-02-21 05:54:43.853',2,1,1);
//INSERT INTO sunbird_courses.user_activity_agg(activity_type, activity_id, user_id,context_id,agg,agg_details,agg_last_updated,aggregates) VALUES ('Course','do_11309999837886054415','user001','cb:0131000245281587206',null,{'identifier': 'certificateId', 'lastIssuedOn': '2019-08-21', 'name': '100PercentCompletionCertificate', 'token': 'P4L3Y9'}], toTimeStamp(toDate(now()))) ;

// touserid
INSERT INTO sunbird_courses.user_enrolments(userid, courseid, batchid,issued_certificates,completedon) VALUES ('user002','do_11309999837886054415','0131000245281587206',[{'identifier': 'certificateId', 'lastIssuedOn': '2019-08-22', 'name': '100PercentCompletionCertificate', 'token': 'P4L3Y10'}], toTimeStamp(toDate(now()))) ;
INSERT INTO sunbird_courses.user_enrolments(userid, courseid, batchid,issued_certificates,completedon) VALUES ('user002','do_11309999837886054415','0131000245281587207',[{'identifier': 'certificateId', 'lastIssuedOn': '2019-08-27', 'name': '100PercentCompletionCertificate', 'token': 'P4L3Y17'}], toTimeStamp(toDate(now()))) ;
INSERT INTO sunbird_courses.user_content_consumption(userid, courseid, batchid, contentid, last_access_time,progress,status,viewcount) VALUES ('user002','do_11309999837886054415','0131000245281587206','do_2134795598368931841927','2022-02-19 05:54:43.853',1,1,2);
//INSERT INTO sunbird_courses.user_activity_agg(activity_type, activity_id, user_id,context_id,agg,agg_details,agg_last_updated,aggregates) VALUES ('Course','do_11309999837886054415','user001','cb:0131000245281587206',null,{'identifier': 'certificateId', 'lastIssuedOn': '2019-08-21', 'name': '100PercentCompletionCertificate', 'token': 'P4L3Y9'}], toTimeStamp(toDate(now()))) ;
