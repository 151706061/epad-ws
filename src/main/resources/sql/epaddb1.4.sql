DROP TABLE if exists project_subject_user;
DROP TABLE if exists project_subject_study;
DROP TABLE if exists project_subject;
DROP TABLE if exists project_user;
DROP TABLE if exists study;
DROP TABLE if exists project;
CREATE TABLE project (id integer unsigned NOT NULL AUTO_INCREMENT,
name varchar(128),
projectid varchar(128),
type varchar(32),
description varchar(1000),
creator varchar(128),
createdtime timestamp,
updatetime timestamp,
updated_by varchar(64),
PRIMARY KEY (id)) ENGINE=InnoDB DEFAULT CHARSET=utf8;
-- insert into project values(null,'Unassigned','unassigned','Public','Default Project','admin',sysdate(),sysdate(),'admin');
commit;
CREATE UNIQUE INDEX project_projectid_ind on project(projectid);
CREATE UNIQUE INDEX project_name_ind on project(name);

DROP TABLE if exists user;
CREATE TABLE user (id integer unsigned NOT NULL AUTO_INCREMENT,
username varchar(128),
firstname varchar(256),
lastname varchar(256),
email varchar(256),
password varchar(256),
enabled tinyint(1),
admin tinyint(1),
lastlogin timestamp,
creator varchar(128),
createdtime timestamp,
updatetime timestamp,
updated_by varchar(64),
PRIMARY KEY (id)) ENGINE=InnoDB DEFAULT CHARSET=utf8;
CREATE UNIQUE INDEX user_username_ind on user(username);

DROP TABLE if exists subject;
CREATE TABLE subject (id integer unsigned NOT NULL AUTO_INCREMENT,
subjectuid varchar(128),
name varchar(256),
gender varchar(16),
dob date,
creator varchar(128),
createdtime timestamp,
updatetime timestamp,
updated_by varchar(64),
PRIMARY KEY (id)) ENGINE=InnoDB DEFAULT CHARSET=utf8;
CREATE UNIQUE INDEX subject_subjectuid_ind on subject(subjectuid);

DROP TABLE if exists study;
CREATE TABLE study (id integer unsigned NOT NULL AUTO_INCREMENT,
studyuid varchar(128),
studydate date,
subject_id integer unsigned,
creator varchar(128),
createdtime timestamp,
updatetime timestamp,
updated_by varchar(64),
PRIMARY KEY (id),
KEY FK_study_subject (subject_id),
CONSTRAINT FK_study_subject FOREIGN KEY (subject_id) REFERENCES subject(id)) ENGINE=InnoDB DEFAULT CHARSET=utf8;
CREATE UNIQUE INDEX study_studyuid_ind on study(studyuid);

DROP TABLE if exists project_user;
CREATE TABLE project_user (id integer unsigned NOT NULL AUTO_INCREMENT,
project_id integer unsigned,
user_id integer unsigned,
role varchar(64),
creator varchar(128),
createdtime timestamp,
updatetime timestamp,
updated_by varchar(64),
PRIMARY KEY (id),
KEY FK_project_user_user (user_id),
CONSTRAINT FK_project_user_user FOREIGN KEY (user_id) REFERENCES user(id),
KEY FK_project_user_project (project_id),
CONSTRAINT FK_project_user_project FOREIGN KEY (project_id) REFERENCES project(id)) ENGINE=InnoDB DEFAULT CHARSET=utf8;
CREATE UNIQUE INDEX project_user_ind on project_user(project_id,user_id);

DROP TABLE if exists project_subject;
CREATE TABLE project_subject (id integer unsigned NOT NULL AUTO_INCREMENT,
project_id integer unsigned,
subject_id integer unsigned,
creator varchar(128),
createdtime timestamp,
updatetime timestamp,
updated_by varchar(64),
PRIMARY KEY (id),
KEY FK_projectsubject_subject (subject_id),
CONSTRAINT FK_projectsubject_subject FOREIGN KEY (subject_id) REFERENCES subject(id),
KEY FK_projectsubject_project (project_id),
CONSTRAINT FK_projectsubject_project FOREIGN KEY (project_id) REFERENCES project(id)) ENGINE=InnoDB DEFAULT CHARSET=utf8;
CREATE UNIQUE INDEX project_subject_ind on project_subject(project_id,subject_id);

DROP TABLE if exists project_subject_study;
CREATE TABLE project_subject_study (id integer unsigned NOT NULL AUTO_INCREMENT,
proj_subj_id integer unsigned,
study_id integer unsigned,
creator varchar(128),
createdtime timestamp,
updatetime timestamp,
updated_by varchar(64),
PRIMARY KEY (id),
KEY FK_projectsubjectstudy_projectsubject (proj_subj_id),
CONSTRAINT FK_projectsubjectstudy_projectsubject FOREIGN KEY (proj_subj_id) REFERENCES project_subject(id),
KEY FK_projectsubjectstudy_study (study_id),
CONSTRAINT FK_projectsubjectstudy_study FOREIGN KEY (study_id) REFERENCES study(id)) ENGINE=InnoDB DEFAULT CHARSET=utf8;
CREATE UNIQUE INDEX project_subject_study_ind on project_subject_study(proj_subj_id,study_id);

DROP TABLE if exists project_subject_user;
CREATE TABLE project_subject_user (id integer unsigned NOT NULL AUTO_INCREMENT,
proj_subj_id integer unsigned,
user_id integer unsigned,
status varchar(64),
statustime timestamp,
creator varchar(128),
createdtime timestamp,
updatetime timestamp,
updated_by varchar(64),
PRIMARY KEY (id),
KEY FK_projectsubjectuser_projectsubject (proj_subj_id),
CONSTRAINT FK_projectsubjectuser_projectsubject FOREIGN KEY (proj_subj_id) REFERENCES project_subject(id),
KEY FK_projectsubjectuser_user (user_id),
CONSTRAINT FK_projectsubjectuser_user FOREIGN KEY (user_id) REFERENCES user(id)) ENGINE=InnoDB DEFAULT CHARSET=utf8;
CREATE UNIQUE INDEX project_subject_user_ind on project_subject_user(proj_subj_id,user_id);

ALTER TABLE dbversion MODIFY COLUMN version varchar(6);

UPDATE dbversion SET version = '1.4';

-- CREATE INDEX annotations_series_ind ON annotations(seriesuid);
-- CREATE INDEX annotations_project_ind ON annotations(projectuid);

