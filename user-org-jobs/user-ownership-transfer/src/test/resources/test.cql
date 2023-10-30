CREATE KEYSPACE IF NOT EXISTS sunbird_courses with replication = {'class':'SimpleStrategy','replication_factor':1};

CREATE TABLE IF NOT EXISTS sunbird_courses.course_batch (
    courseid text,
    batchid text,
    cert_templates map<text, frozen<map<text, text>>>,
    createdby text,
    createddate text,
    createdfor list<text>,
    description text,
    enddate text,
    enrollmentenddate text,
    enrollmenttype text,
    mentors list<text>,
    name text,
    startdate text,
    status int,
    updateddate text,
    PRIMARY KEY (courseid, batchid)
);


INSERT INTO sunbird_courses.course_batch(courseid, batchid, createdBy) VALUES ('do_2129195698820055041246','01295417094601113627','02c4e0dc-3e25-4f7d-b811-242c73e24a01');
INSERT INTO sunbird_courses.course_batch(courseid, batchid, createdBy, mentors) VALUES ('do_21289599347869286411276','01289599909937152015','fca2925f-1eee-4654-9177-fece3fd6afc9',['fca2925f-1eee-4654-9177-fece3fd6afc9']);
INSERT INTO sunbird_courses.course_batch(courseid, batchid, createdBy, mentors) VALUES ('do_2129555111659520001388','0129555223509401604','fca2925f-1eee-4654-9177-fece3fd6afc9',['fca2925f-1eee-4654-9177-fece3fd6afc9']);