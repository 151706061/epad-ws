package edu.stanford.epad.epadws.service;

//Copyright (c) 2014 The Board of Trustees of the Leland Stanford Junior University
//All rights reserved.
//
//Redistribution and use in source and binary forms, with or without modification, are permitted provided that
//the following conditions are met:
//
//Redistributions of source code must retain the above copyright notice, this list of conditions and the following
//disclaimer.
//
//Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
//following disclaimer in the documentation and/or other materials provided with the distribution.
//
//Neither the name of The Board of Trustees of the Leland Stanford Junior University nor the names of its
//contributors (Daniel Rubin, et al) may be used to endorse or promote products derived from this software without
//specific prior written permission.
//
//THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
//INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
//DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
//SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
//SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
//WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
//USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Level;
import org.mindrot.jbcrypt.BCrypt;

import edu.stanford.epad.common.util.EPADConfig;
import edu.stanford.epad.common.util.EPADLogger;
import edu.stanford.epad.epadws.models.EpadFile;
import edu.stanford.epad.epadws.models.Project;
import edu.stanford.epad.epadws.models.ProjectToSubject;
import edu.stanford.epad.epadws.models.ProjectToSubjectToStudy;
import edu.stanford.epad.epadws.models.ProjectToSubjectToUser;
import edu.stanford.epad.epadws.models.ProjectToUser;
import edu.stanford.epad.epadws.models.ProjectType;
import edu.stanford.epad.epadws.models.Study;
import edu.stanford.epad.epadws.models.Subject;
import edu.stanford.epad.epadws.models.User;
import edu.stanford.epad.epadws.models.User.EventLog;
import edu.stanford.epad.epadws.models.UserRole;

public class DefaultEpadProjectOperations implements EpadProjectOperations {
	
	private static final EPADLogger log = EPADLogger.getInstance();

	private static final DefaultEpadProjectOperations ourInstance = new DefaultEpadProjectOperations();
	
	// Simple Project/Subject/User cache - maybe replace it with Ehcache someday
	private static Map<String, Project> projectCache = new HashMap<String, Project>();
	private static Map<String, User> userCache = new HashMap<String, User>();
	private static Map<String, Subject> subjectCache = new HashMap<String, Subject>();

	private DefaultEpadProjectOperations()
	{
	}

	public static DefaultEpadProjectOperations getInstance()
	{
		return ourInstance;
	}

	@Override
	public void clearCache() {
		projectCache = new HashMap<String, Project>();
		userCache = new HashMap<String, User>();
		subjectCache = new HashMap<String, Subject>();
	}

	@Override
	public Project createProject(String loggedInUser, String projectId, String projectName,
			String description, ProjectType type) throws Exception {
		Project project = new Project();
		project.setProjectId(projectId);
		project.setName(projectName);
		project.setDescription(description);
		project.setType(type.getName());
		project.setCreator(loggedInUser);
		project.save();
		projectCache.put(project.getProjectId(), project);
		return project;
	}

	@Override
	public Project updateProject(String loggedInUser, String projectId,
			String projectName, String description, ProjectType type)
			throws Exception {
		Project project = getProject(projectId);
		if (projectName != null)
			project.setName(projectName);
		if (description != null)
			project.setDescription(description);
		if (type != null)
			project.setType(type.getName());
		project.save();
		projectCache.put(project.getProjectId(), project);
		return project;
	}

	@Override
	public User createUser(String loggedInUser, String username, String firstName, String lastName, String email,
			String password) throws Exception {
		User user = new User();
		user.setUsername(username);
		user.setFirstName(firstName);
		user.setLastName(lastName);
		user.setEmail(email);
		String hashedPW = BCrypt.hashpw(password, BCrypt.gensalt());
		user.setPassword(hashedPW);
		user.setCreator(loggedInUser);
		user.setEnabled(true);
		user.save();
		userCache.put(user.getUsername(), user);
		return user;
	}

	@Override
	public User updateUser(String loggedInUserName, String username,
			String firstName, String lastName, String email, String newpassword, String oldpassword)
			throws Exception {
		User loggedInUser = getUser(loggedInUserName);
		User user = new User();
		user = (User) user.getObject("username = " + user.toSQL(username));
		if (firstName != null) user.setFirstName(firstName);
		if (lastName != null) user.setLastName(lastName);
		if (email != null) user.setEmail(email);
		if (newpassword != null) {
			if (loggedInUser.isAdmin() || (user.getPassword().length() < 60 && oldpassword.equals(user.getPassword())) 
					|| BCrypt.checkpw(oldpassword, user.getPassword()))
			{
				String hashedPW = BCrypt.hashpw(newpassword, BCrypt.gensalt());
				user.setPassword(hashedPW);
			}
			else
				throw new Exception("Invalid old password");
		}
		user.save();
		userCache.put(user.getUsername(), user);
		return user;
	}

	@Override
	public void enableUser(String loggedInUser, String username) throws Exception {
		User user = getUser(username);
		user.setEnabled(true);
		user.save();
		userCache.put(user.getUsername(), user);
	}

	@Override
	public void disableUser(String loggedInUser, String username) throws Exception {
		User user = getUser(username);
		user.setEnabled(false);
		user.save();
		userCache.put(user.getUsername(), user);
	}

	@Override
	public void userErrorLog(String username, String message) {
		try {
			User user = getUser(username);
			if (user != null)
				user.addEventLog(Level.ERROR, message);
		} catch (Exception e) {	}
	}

	@Override
	public void userWarningLog(String username, String message) {
		try {
			User user = getUser(username);
			if (user != null)
				user.addEventLog(Level.WARN, message);
		} catch (Exception e) {	}
	}

	@Override
	public void userInfoLog(String username, String message) {
		try {
			User user = getUser(username);
			if (user != null)
				user.addEventLog(Level.INFO, message);
		} catch (Exception e) {	}
	}

	@Override
	public void addUserToProject(String loggedInUser, String projectId,
			String username, UserRole role) throws Exception {
		User user = getUser(username);
		Project project = getProject(projectId);
		ProjectToUser ptou = (ProjectToUser) new ProjectToUser().getObject("project_id = " + project.toSQL(projectId) + " and user_id=" + user.getId());
		if (ptou == null)
		{
			ptou = new ProjectToUser();
			ptou.setCreator(loggedInUser);
		}
		ptou.setProjectId(project.getId());
		ptou.setUserId(user.getId());
		ptou.setRole(role.getName());
		ptou.save();
	}

	@Override
	public void removeUserFromProject(String loggedInUser, String projectId,
			String username) throws Exception {
		User user = getUser(username);
		Project project = getProject(projectId);
		ProjectToUser ptou = (ProjectToUser) new ProjectToUser().getObject("project_id = " + project.toSQL(projectId) + " and user_id=" + user.getId());
		if (ptou != null)
			ptou.delete();
	}

	@Override
	public void setUserRoleForProject(String loggedInUser, String projectId,
			String username, UserRole role) throws Exception {
		User user = getUser(username);
		Project project = getProject(projectId);
		ProjectToUser ptou = new ProjectToUser();
		ptou = (ProjectToUser) ptou.getObject("project_id = " + project.getId() + " and user_id =" + user.getId());
		ptou.setRole(role.getName());
	}

	@Override
	public Subject createSubject(String loggedInUser, String subjectUID,
			String name, Date dob, String gender) throws Exception {
		Subject subject = getSubject(subjectUID);
		if (subject == null) subject = new Subject();
		subject.setSubjectUID(subjectUID);
		if (name != null) subject.setName(name);
		if (dob != null) subject.setDob(dob);
		if (gender != null) subject.setGender(gender);
		if (subject.getId() == 0) subject.setCreator(loggedInUser);
		subject.save();
		subjectCache.put(subject.getSubjectUID(), subject);
		return subject;
	}

	@Override
	public Study createStudy(String loggedInUser, String studyUID,
			String subjectUID) throws Exception {
		Subject subject = getSubject(subjectUID);
		Study study = getStudy(studyUID);
		if (study == null)
		{
			study = new Study();
			study.setCreator(loggedInUser);
		}
		study.setStudyUID(studyUID);
		study.setSubjectId(subject.getId());
		study.save();
		return study;
	}

	@Override
	public void addSubjectToProject(String loggedInUser, String subjectUID,
			String projectId) throws Exception {
		Subject subject = new Subject();
		subject = (Subject) subject.getObject("subjectuid = " + subject.toSQL(subjectUID));
		Project project = new Project();
		project = (Project) project.getObject("projectId = " + project.toSQL(projectId));
		ProjectToSubject ptos = new ProjectToSubject();
		ptos.setProjectId(project.getId());
		ptos.setSubjectId(subject.getId());
		ptos.setCreator(loggedInUser);
		ptos.save();
	}

	@Override
	public void addStudyToProject(String loggedInUser, String studyUID,
			String subjectUID, String projectId) throws Exception {
		Study study = getStudy(studyUID);
		Subject subject = new Subject();
		if ((subjectUID == null || subjectUID.length() == 0) && study != null)
		{
			subject.setId(study.getSubjectId());
			subject.retrieve();
		}
		else	
		{
			subject = getSubject(subjectUID);
		}
		if (study == null)
		{
			study = new Study();
			study.setStudyUID(studyUID);
			study.setSubjectId(subject.getId());
			study.setCreator(loggedInUser);
			study.save();
		}
		
		Project project = getProject(projectId);
		ProjectToSubject ptos = new ProjectToSubject();
		ptos = (ProjectToSubject) ptos.getObject("project_id = " + project.getId() + " and subject_id =" + subject.getId());
		if (ptos == null)
		{
			ptos = new ProjectToSubject();
			ptos.setProjectId(project.getId());
			ptos.setSubjectId(subject.getId());
			ptos.setCreator(loggedInUser);
			ptos.save();
		}
		ProjectToSubjectToStudy pss = (ProjectToSubjectToStudy) new ProjectToSubjectToStudy().getObject("proj_subj_id = " + ptos.getId() + " and study_id=" + study.getId());
		if (pss == null)
		{
			pss = new ProjectToSubjectToStudy();
			pss.setProjSubjId(ptos.getId());
			pss.setStudyId(study.getId());
			pss.setCreator(loggedInUser);
			pss.save();
		}
	}

	@Override
	public void removeSubjectFromProject(String loggedInUser,
			String subjectUID, String projectId) throws Exception {
		Subject subject = getSubject(subjectUID);
		Project project = new Project();
		project = (Project) project.getObject("projectId = " + project.toSQL(projectId));
		ProjectToSubject ptos = new ProjectToSubject();
		ptos = (ProjectToSubject) ptos.getObject("project_id = " + project.getId() + " and subject_id =" + subject.getId());
		ProjectToSubjectToStudy pss = new ProjectToSubjectToStudy();
		pss.deleteObjects("proj_subj_id = " + ptos.getId());
		ptos.delete();
	}

	@Override
	public void removeStudyFromProject(String loggedInUser, String studyUID,
			String projectId) throws Exception {
		Study study = getStudy(studyUID);
		Subject subject = new Subject();
		subject.setId(study.getSubjectId());
		subject = (Subject) subject.retrieve();
		Project project = new Project();
		project = (Project) project.getObject("projectId = " + project.toSQL(projectId));
		ProjectToSubject ptos = new ProjectToSubject();
		ptos = (ProjectToSubject) ptos.getObject("project_id = " + project.getId() + " and subject_id =" + subject.getId());
		ProjectToSubjectToStudy pss = new ProjectToSubjectToStudy();
		pss.deleteObjects("proj_subj_id = " + ptos.getId() + " and study_id =" + study.getId());
	}

	@Override
	public EpadFile createFile(String loggedInUser, String projectID,
			String subjectUID, String studyUID, String seriesUID, File file,
			String filename, String description) throws Exception {
		EpadFile efile = new EpadFile();
		efile.setName(filename);
		efile.setDescription(description);
		if (projectID != null && projectID.length() > 0)
		{
			Project project = getProject(projectID);
			efile.setProjectId(project.getId());
		}
		if (subjectUID != null && subjectUID.length() > 0)
		{
			Subject subject = getSubject(subjectUID);
			efile.setSubjectId(subject.getId());
		}
		if (studyUID != null && studyUID.length() > 0)
		{
			Study study = getStudy(studyUID);
			efile.setStudyId(study.getId());
		}
		efile.setSeriesUid(seriesUID);
		efile.setFilePath(EPADConfig.getEPADWebServerFilesDir() + efile.getRelativePath());
		efile.setCreator(loggedInUser);
		efile.setLength(file.length());
		efile.save();
		File parent = new File(efile.getFilePath());
		parent.mkdirs();
		String physicalName = "" + efile.getId() + efile.getExtension();
		FileUtils.copyFile(file, new File(parent, physicalName));
		log.info("Created file:" + efile.getName() + " in Project:" + efile.getProjectId());
		return efile;
	}

	@Override
	public Project getProject(String projectId) throws Exception {
		Project project = projectCache.get(projectId);
		if (project != null)
			return project;
		project = new Project();
		project = (Project) project.getObject("projectId = " + project.toSQL(projectId));
		return project;
	}

	@Override
	public User getUser(String username) throws Exception {
		User user = userCache.get(username);
		if (user != null)
			return user;
		user = new User();
		user = (User) user.getObject("username = " + user.toSQL(username));
		return user;
	}

	@Override
	public User getUserByEmail(String email) throws Exception {
		User user = new User();
		List objects = user.getObjects("email = " + user.toSQL(email));
		if (objects.size() > 0)
			return (User) objects.get(0);
		else
			return null;
	}

	@Override
	public Subject getSubject(String subjectUID) throws Exception {
		Subject subject = subjectCache.get(subjectUID);
		if (subject != null)
			return subject;
		subject = new Subject();
		subject = (Subject) subject.getObject("subjectuid = " + subject.toSQL(subjectUID));
		return subject;
	}

	@Override
	public Study getStudy(String studyUID) throws Exception {
		Study study = new Study();
		study = (Study) study.getObject("studyUID = " + study.toSQL(studyUID));
		return study;
	}

	@Override
	public List<Project> getAllProjects() throws Exception {
		List objects = new Project().getObjects("1 = 1 order by projectId");
		List<Project> projects = new ArrayList<Project>();
		projects.addAll(objects);
		return projects;
	}

	@Override
	public List<Project> getPublicProjects() throws Exception {
		List objects = new Project().getObjects("type = '" + ProjectType.PUBLIC + "' order by projectId");
		List<Project> projects = new ArrayList<Project>();
		projects.addAll(objects);
		return projects;
	}

	@Override
	public List<User> getAllUsers() throws Exception {
		List objects = new User().getObjects("1 = 1 order by username");
		List<User> users = new ArrayList<User>();
		users.addAll(objects);
		return users;
	}

	@Override
	public List<Project> getProjectsForUser(String username) throws Exception {
		User user = getUser(username);
		if (user.isAdmin())
			return this.getAllProjects();
		List objects = new Project().getObjects("id in (select project_id from " 
													+ ProjectToUser.DBTABLE 
													+ " where user_id =" + user.getId() + ") order by projectId");
		List<Project> projects = new ArrayList<Project>();
		projects.addAll(objects);
		List<Project> publicProjs = getPublicProjects();
		for (Project project: publicProjs)
		{
			if (!projects.contains(project))
				projects.add(project);
		}
		return projects;
	}

	@Override
	public Project getProjectForUser(String username, String projectID) throws Exception {
		Project project = getProject(projectID);
		if (project == null) return null;
		User user = getUser(username);
		if (user.isAdmin() || project.getType().equals(ProjectType.PUBLIC))
			return project;
		List objects = new Project().getObjects("id in (select project_id from " 
													+ ProjectToUser.DBTABLE 
													+ " where user_id =" + user.getId() + " and project_id=" + project.getId() +")");
		if (objects.size() > 0)
			return project;
		else
			return null;
	}

	@Override
	public List<User> getUsersForProject(String projectId) throws Exception {
		Project project = getProject(projectId);
		
		return getUsersByProjectId(project.getId());
	}

	private List<User> getUsersByProjectId(long id)
			throws Exception {
		List objects = new User().getObjects("id in (select user_id from " 
													+ ProjectToUser.DBTABLE 
													+ " where project_id =" + id + ")");
		List<User> users = new ArrayList<User>();
		users.addAll(objects);
		
		return users;
	}
	
	@Override
	public List<User> getUsersWithRoleForProject(String projectId)
			throws Exception {
		Project project = getProject(projectId);
		List ptous = new ProjectToUser().getObjects("project_id = " + project.getId());
		List<User> users = new ArrayList<User>();
		for (Object ptou: ptous)
		{
			long userId = ((ProjectToUser) ptou).getUserId();
			User user = new User();
			user.setId(userId);
			user.retrieve();
			user.setRole(((ProjectToUser) ptou).getRole());
			users.add(user);
		}
		return users;
	}

	@Override
	public List<Subject> getSubjectsForProject(String projectId)
			throws Exception {
		Project project = getProject(projectId);
		
		return getSubjectsByProjectId(project.getId());
	}

	private List<Subject> getSubjectsByProjectId(long id)
			throws Exception {
		List objects = new Subject().getObjects("id in (select subject_id from " 
													+ ProjectToSubject.DBTABLE 
													+ " where project_id =" + id + ")");
		List<Subject> subjects = new ArrayList<Subject>();
		subjects.addAll(objects);
		
		return subjects;
	}
	
	@Override
	public Subject getSubjectForProject(String projectId, String subjectUID)
			throws Exception {
		Project project = getProject(projectId);
		Subject subject = getSubject(subjectUID);
		if (subject == null) return null;
		if (new ProjectToSubject().getCount("subject_id=" + subject.getId() + " and project_id=" + project.getId()) > 0)
			return subject;
		else
			return null;
	}

	@Override
	public Subject getSubjectFromName(String subjectName) throws Exception {
		Subject subject = new Subject();
		List<Subject> subjects = subject.getObjects("name =" +subject.toSQL(subjectName));
		if (subjects.size() > 0)
			return subjects.get(0);
		else
			return null;
	}

	@Override
	public Subject getSubjectFromNameForProject(String subjectName,
			String projectID) throws Exception {
		if (projectID == null || projectID.trim().length() == 0)
			return getSubjectFromName(subjectName);
		List<Subject> subjects = this.getSubjectsForProject(projectID);
		for (Subject subject: subjects)
			if (subjectName.equals(subject.getName())) return subject;
		return null;
	}

	@Override
	public List<Project> getProjectsForSubject(String subjectUID)
			throws Exception {
		Subject subject = getSubject(subjectUID);
		List objects = new Project().getObjects("id in (select project_id from " 
													+ ProjectToSubject.DBTABLE 
													+ " where subject_id =" + subject.getId() + ")");
		List<Project> projects = new ArrayList<Project>();
		projects.addAll(objects);
		
		return projects;
	}

	@Override
	public List<Study> getStudiesForProjectAndSubject(String projectId,
			String subjectUID) throws Exception {
		Project project = getProject(projectId);
		Subject subject = getSubject(subjectUID);
		ProjectToSubject ptos = (ProjectToSubject) new ProjectToSubject().getObject("project_id = " + project.getId() + " and subject_id=" + subject.getId());
		List<Study> studies = new ArrayList<Study>();
		List objects = new Study().getObjects("id in (select study_id from " 
				+ ProjectToSubjectToStudy.DBTABLE 
				+ " where proj_subj_id =" + ptos.getId() + ")");
		studies.addAll(objects);

		return studies;
	}

	@Override
	public Project getFirstProjectForStudy(String studyUID) throws Exception {
		Study study = getStudy(studyUID);
		List<ProjectToSubject> ptoss = new ProjectToSubject().getObjects("subject_id=" + study.getSubjectId());
		Map<Long, ProjectToSubject> ptosIdMap = new HashMap<Long, ProjectToSubject>();
		for (ProjectToSubject ptos: ptoss)
			ptosIdMap.put(ptos.getId(), ptos);
		List<ProjectToSubjectToStudy> psss = new ProjectToSubjectToStudy().getObjects("proj_subj_id in " + study.toSQL(ptosIdMap.keySet()) + " and study_id=" + study.getId());
		if (psss.size() > 0)
		{
			Project project = new Project();
			long ptosId = ((ProjectToSubjectToStudy)psss.get(0)).getProjSubjId();
			project.setId(ptosIdMap.get(ptosId).getId());
			project.retrieve();
			return project;
		}
		return null;
	}

	@Override
	public boolean isStudyInProjectAndSubject(String projectId,
			String subjectUID, String studyUID) throws Exception {
		Project project = getProject(projectId);
		Subject subject = getSubject(subjectUID);
		Study study = getStudy(studyUID);
		ProjectToSubject ptos = (ProjectToSubject) new ProjectToSubject().getObject("project_id = " + project.getId() + " and subject_id=" + subject.getId());
		return new ProjectToSubjectToStudy().getCount("proj_subj_id = " + ptos.getId() + " and study_id=" + study.getId()) > 0;
	}

	@Override
	public List<Study> getAllStudiesForProject(String projectId) throws Exception {
		Project project = getProject(projectId);
		List objects = new ProjectToSubject().getObjects("project_id = " + project.getId());
		List<Study> studies = new ArrayList<Study>();
		for (Object ptos: objects)
		{
			List sobjects = new Study().getObjects("id in (select study_id from " 
					+ ProjectToSubjectToStudy.DBTABLE 
					+ " where proj_subj_id =" + ((ProjectToSubject)ptos).getId() + ")");
			studies.addAll(sobjects);
			
		}
		return studies;
	}

	@Override
	public List<Study> getStudiesForSubject(String subjectUID) throws Exception {
		Subject subject = getSubject(subjectUID);
		List objects = new Study().getObjects("subject_id  =" + subject.getId());
		List<Study> studies = new ArrayList<Study>();
		studies.addAll(objects);		
		return studies;
	}

	@Override
	public void setUserStatusForProjectAndSubject(String loggedInUser,
			String projectID, String subjectUID, String status) throws Exception {
		Project project = getProject(projectID);
		Subject subject = getSubject(subjectUID);
		User user = getUser(loggedInUser);
		ProjectToSubject ptos = (ProjectToSubject)new ProjectToSubject().getObject("project_id=" + project.getId() + " and subject_id=" + subject.getId());
		ProjectToSubjectToUser psu = (ProjectToSubjectToUser)new ProjectToSubjectToUser().getObject("proj_subj_id=" + ptos.getId() + " and user_id=" + user.getId());
		if (psu == null)
		{
			psu = new ProjectToSubjectToUser();
			psu.setProjSubjId(ptos.getId());
			psu.setUserId(user.getId());
			psu.setCreator(loggedInUser);
		}
		psu.setStatus(status);
		psu.save();
	}

	@Override
	public String getUserStatusForProjectAndSubject(String loggedInUser,
			String projectID, String subjectUID) throws Exception {
		Project project = getProject(projectID);
		Subject subject = getSubject(subjectUID);
		User user = getUser(loggedInUser);
		ProjectToSubject ptos = (ProjectToSubject)new ProjectToSubject().getObject("project_id=" + project.getId() + " and subject_id=" + subject.getId());
		ProjectToSubjectToUser psu = (ProjectToSubjectToUser)new ProjectToSubjectToUser().getObject("proj_subj_id=" + ptos.getId() + " and user_id=" + user.getId());
		if (psu == null)
			return null;
		else
			return psu.getStatus();
	}

	@Override
	public Map<String, String> getUserStatusForProjectSubjects(
			String loggedInUser, String projectID) throws Exception {
		Project project = getProject(projectID);
		User user = getUser(loggedInUser);
		List<Subject> subjects = getSubjectsByProjectId(project.getId());
		Map<Long, String> subjectIdToUID = new HashMap<Long, String>();
		for (Subject subject: subjects)
		{
			subjectIdToUID.put(subject.getId(), subject.getSubjectUID());
		}
		List ptoss = new ProjectToSubject().getObjects("project_id=" + project.getId());
		List<Long> ptosIds = new ArrayList<Long>();
		Map<Long, Long> ptosIdToSubjectID = new HashMap<Long, Long>();
		for (Object ptos: ptoss)
		{
			ptosIds.add(((ProjectToSubject)ptos).getId());
			ptosIdToSubjectID.put(((ProjectToSubject)ptos).getId(), ((ProjectToSubject)ptos).getSubjectId());
		}
		List psus = new ProjectToSubjectToUser().getObjects("proj_subj_id in " + project.toSQL(ptosIds) + " and user_id=" + user.getId());
		Map<String, String> subjectStatus = new HashMap<String, String>();
		for (Object psu: psus)
		{
			String status = ((ProjectToSubjectToUser) psu).getStatus();
			Long subjectId = ptosIdToSubjectID.get(((ProjectToSubjectToUser) psu).getProjSubjId());
			String subjectUID = subjectIdToUID.get(subjectId);
			subjectStatus.put(subjectUID, status);
		}
		return subjectStatus;
	}

	@Override
	public Map<Long, String> getUserStatusForProjectSubjectIds(
			String loggedInUser, String projectID) throws Exception {
		Project project = getProject(projectID);
		User user = getUser(loggedInUser);
		List ptoss = new ProjectToSubject().getObjects("project_id=" + project.getId());
		List<Long> ptosIds = new ArrayList<Long>();
		Map<Long, Long> ptosIdToSubjectID = new HashMap<Long, Long>();
		for (Object ptos: ptoss)
		{
			ptosIds.add(((ProjectToSubject)ptos).getId());
			ptosIdToSubjectID.put(((ProjectToSubject)ptos).getId(), ((ProjectToSubject)ptos).getSubjectId());
		}
		List psus = new ProjectToSubjectToUser().getObjects("proj_subj_id in " + project.toSQL(ptosIds) + " and user_id=" + user.getId());
		Map<Long, String> subjectIdStatus = new HashMap<Long, String>();
		for (Object psu: psus)
		{
			String status = ((ProjectToSubjectToUser) psu).getStatus();
			Long subjectId = ptosIdToSubjectID.get(((ProjectToSubjectToUser) psu).getProjSubjId());
			subjectIdStatus.put(subjectId, status);
		}
		return subjectIdStatus;
	}

	@Override
	public boolean isCollaborator(String username, String projectID) throws Exception {
		if (projectID == null || projectID.trim().length() == 0)
		{
			if (username.equals("admin")) 
				return false;
			else
				return true;
		}
		UserRole role = getUserProjectRole(username, projectID);
		if (role == null && username.equals("admin")) return false;
		if (role == null && projectID.equals(EPADConfig.xnatUploadProjectID)) return true;
		if (role == null)
			throw new Exception("User " + username  + " does not exist in project:" + projectID);
		if (role.equals(UserRole.COLLABORATOR))
			return true;
		else
			return false;
	}

	@Override
	public boolean isMember(String username, String projectID)
			throws Exception {
		if (projectID == null || projectID.trim().length() == 0)
			return false;
		UserRole role = getUserProjectRole(username, projectID);
		if (role == null && username.equals("admin")) return true;
		if (role == null)
			throw new Exception("Does not exist in project:" + projectID);
		if (role.equals(UserRole.MEMBER))
			return true;
		else
			return false;
	}

	@Override
	public boolean isOwner(String username, String projectID)
			throws Exception {
		if (projectID == null || projectID.trim().length() == 0)
			return false;
		Project project = getProject(projectID);
		if (project.getCreator().equals(username))
			return true;
		UserRole role = getUserProjectRole(username, projectID);
		if (role == null && username.equals("admin")) return true;
		if (role == null)
			throw new Exception("Does not exist in project:" + projectID);
		if (role.equals(UserRole.OWNER))
			return true;
		else
			return false;
	}

	@Override
	public UserRole getUserProjectRole(String username,
			String projectID) throws Exception {
		User user = getUser(username);
		Project project = getProject(projectID);
		ProjectToUser pu = (ProjectToUser) new ProjectToUser().getObject("project_id =" + project.getId() + " and user_id=" + user.getId());
		if (pu == null)
			return null;
		String role = pu.getRole();
		return UserRole.getRole(role);
	}

	@Override
	public void deleteProject(String username, String projectID)
			throws Exception {
		Project project = getProject(projectID);
		new ProjectToUser().deleteObjects("project_id=" + project.getId());		
		new ProjectToSubjectToUser().deleteObjects("proj_subj_id in (select id from " + new ProjectToSubject().returnDBTABLE() + " where project_id=" + project.getId() + ")");
		new ProjectToSubjectToStudy().deleteObjects("proj_subj_id in (select id from " + new ProjectToSubject().returnDBTABLE() + " where project_id=" + project.getId() + ")");
		new ProjectToSubject().deleteObjects("project_id=" + project.getId());
		project.delete();
	}

	@Override
	public void deleteSubject(String username, String subjectUID,
			String projectID) throws Exception {
		Subject subject = getSubject(subjectUID);
		Project project = getProject(projectID);
		ProjectToSubject projSubj = (ProjectToSubject) new ProjectToSubject().getObject("project_id =" + project.getId() + " and subject_id=" + subject.getId());
		new ProjectToSubjectToUser().deleteObjects("proj_subj_id =" + projSubj.getId());
		new ProjectToSubjectToStudy().deleteObjects("proj_subj_id =" + projSubj.getId());
		projSubj.delete();
		// TODO: delete subject if not used any more
	}

	@Override
	public void deleteStudy(String username, String studyUID,
			String subjectUID, String projectID) throws Exception {
		Subject subject = getSubject(subjectUID);
		Project project = getProject(projectID);
		Study study = getStudy(studyUID);
		ProjectToSubject projSubj = (ProjectToSubject) new ProjectToSubject().getObject("project_id =" + project.getId() + " and subject_id=" + subject.getId());
		ProjectToSubjectToStudy projSubjStudy = (ProjectToSubjectToStudy) new ProjectToSubjectToStudy().getObject("proj_subj_id =" + projSubj.getId() + " and study_id=" + study.getId());
		projSubjStudy.delete();
		// TODO: delete study if not used any more
	}

	@Override
	public void deleteSubject(String username, String subjectUID) throws Exception {
		Subject subject = getSubject(subjectUID);
		subject.delete();
	}

	@Override
	public void deleteStudy(String username, String studyUID) throws Exception {
		Study study = getStudy(studyUID);
		study.delete();
	}

	@Override
	public List<EventLog> getUserLogs(String username) {
		try {
			User user = getUser(username);
			if (user != null)
				return user.getEventLogs();
		} catch (Exception e) {
		}
		return null;
	}

}