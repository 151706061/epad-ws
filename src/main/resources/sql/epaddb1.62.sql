ALTER TABLE series_status ADD COLUMN default_tags varchar(256);
ALTER TABLE user ADD COLUMN colorpreference varchar(128);

DROP TABLE if exists reviewer;
CREATE TABLE reviewer (id integer unsigned NOT NULL AUTO_INCREMENT,
reviewer varchar(128),
reviewee varchar(128),
creator varchar(128),
createdtime timestamp,
updatetime timestamp,
updated_by varchar(64),
PRIMARY KEY (id),
KEY FK_reviewer_user (reviewer),
CONSTRAINT FK_reviewer_user FOREIGN KEY (reviewer) REFERENCES user(username),
KEY FK_reviewee_user (reviewee),
CONSTRAINT FK_reviewee_user FOREIGN KEY (reviewee) REFERENCES user(username)) ENGINE=InnoDB DEFAULT CHARSET=utf8;
CREATE UNIQUE INDEX uk_reviewer_ind on reviewer(reviewer,reviewee);

DROP TABLE if exists project_pluginparameter;
DROP TABLE if exists project_plugin;
DROP TABLE if exists plugin;

CREATE TABLE plugin (id integer unsigned NOT NULL AUTO_INCREMENT,
plugin_id varchar(64),
name varchar(128),
description varchar(128),
javaclass varchar(256),
enabled tinyint(1),
status varchar(64),
modality varchar(64),
creator varchar(128),
createdtime timestamp,
updatetime timestamp,
updated_by varchar(64),
PRIMARY KEY (id)) ENGINE=InnoDB DEFAULT CHARSET=utf8;
CREATE UNIQUE INDEX plugin_pluginid_ind on plugin(plugin_id);

CREATE TABLE project_plugin (id integer unsigned NOT NULL AUTO_INCREMENT,
project_id integer unsigned,
plugin_id integer unsigned,
enabled tinyint(1),
creator varchar(128),
createdtime timestamp,
updatetime timestamp,
updated_by varchar(64),
PRIMARY KEY (id),
KEY FK_projectplugin_plugin (plugin_id),
CONSTRAINT FK_projectplugin_plugin FOREIGN KEY (plugin_id) REFERENCES plugin(id),
KEY FK_projectplugin_project (project_id),
CONSTRAINT FK_projectplugin_project FOREIGN KEY (project_id) REFERENCES project(id)) ENGINE=InnoDB DEFAULT CHARSET=utf8;
CREATE UNIQUE INDEX project_plugin_ind on project_plugin(project_id,plugin_id);

CREATE TABLE project_pluginparameter (id integer unsigned NOT NULL AUTO_INCREMENT,
project_id integer unsigned,
plugin_id integer unsigned,
name varchar(128),
default_value varchar(128),
creator varchar(128),
createdtime timestamp,
updatetime timestamp,
updated_by varchar(64),
PRIMARY KEY (id),
KEY FK_projectpluginparameter_plugin (plugin_id),
CONSTRAINT FK_projectpluginparameter_plugin FOREIGN KEY (plugin_id) REFERENCES plugin(id),
KEY FK_projectpluginparameter_project (project_id),
CONSTRAINT FK_projectpluginparameter_project FOREIGN KEY (project_id) REFERENCES project(id)) ENGINE=InnoDB DEFAULT CHARSET=utf8;

UPDATE dbversion SET version = '1.62';
commit;
