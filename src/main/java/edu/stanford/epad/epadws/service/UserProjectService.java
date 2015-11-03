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
import java.io.FileInputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.dcm4che2.data.DicomObject;
import org.dcm4che2.data.Tag;

import edu.stanford.epad.common.dicom.DicomReader;
import edu.stanford.epad.common.util.EPADConfig;
import edu.stanford.epad.common.util.EPADFileUtils;
import edu.stanford.epad.common.util.EPADLogger;
import edu.stanford.epad.common.util.MailUtil;
import edu.stanford.epad.dtos.EPADAIM;
import edu.stanford.epad.dtos.TaskStatus;
import edu.stanford.epad.epadws.aim.AIMQueries;
import edu.stanford.epad.epadws.aim.AIMSearchType;
import edu.stanford.epad.epadws.aim.AIMUtil;
import edu.stanford.epad.epadws.dcm4chee.Dcm4CheeDatabase;
import edu.stanford.epad.epadws.epaddb.EpadDatabase;
import edu.stanford.epad.epadws.epaddb.EpadDatabaseOperations;
import edu.stanford.epad.epadws.models.Project;
import edu.stanford.epad.epadws.models.Study;
import edu.stanford.epad.epadws.models.Subject;
import edu.stanford.epad.epadws.models.User;
import edu.stanford.epad.epadws.queries.DefaultEpadOperations;
import edu.stanford.epad.epadws.queries.XNATQueries;
import edu.stanford.epad.epadws.security.IdGenerator;
import edu.stanford.epad.epadws.xnat.XNATCreationOperations;
import edu.stanford.epad.epadws.xnat.XNATSessionOperations;
import edu.stanford.epad.epadws.xnat.XNATUtil;
import edu.stanford.hakan.aim4api.compability.aimv3.ImageAnnotation;

/**
 * Originally a Wrapper class to call either XNAT or EPAD project/user api (not only calls EPAD api)
 * 
 * @author Dev Gude
 *
 */
public class UserProjectService {
	private static final EPADLogger log = EPADLogger.getInstance();
	
	public static Map<String, String> pendingPNGs = new HashMap<String, String>();
	public static Map<String, String> pendingUploads = new HashMap<String, String>();

	private static final EpadProjectOperations projectOperations = DefaultEpadProjectOperations.getInstance();	
	private static final EpadDatabaseOperations databaseOperations = EpadDatabase.getInstance().getEPADDatabaseOperations();	
	
	public static final String XNAT_UPLOAD_PROPERTIES_FILE_NAME = "xnat_upload.properties";
	
	/**
	 * Check if user is collaborator
	 * @param sessionID
	 * @param username
	 * @param projectID
	 * @return
	 * @throws Exception
	 */
	public static boolean isCollaborator(String sessionID, String username, String projectID) throws Exception {
		return projectOperations.isCollaborator(username, projectID);
	}
	
	/**
	 * Check if user is owner
	 * @param sessionID
	 * @param username
	 * @param projectID
	 * @return
	 * @throws Exception
	 */
	public static boolean isOwner(String sessionID, String username, String projectID) throws Exception {
		return projectOperations.isOwner(username, projectID);
	}
	
	/**
	 * Check if user is member
	 * @param sessionID
	 * @param username
	 * @param projectID
	 * @return
	 * @throws Exception
	 */
	public static boolean isMember(String sessionID, String username, String projectID) throws Exception {
		return projectOperations.isMember(username, projectID);
	}
	
	/**
	 * Get all project ids
	 * @return
	 * @throws Exception
	 */
	public static Set<String> getAllProjectIDs() throws Exception {
		List<Project> projects = projectOperations.getAllProjects();
		Set<String> projectIDs = new HashSet<String>();
		for (Project project: projects)
			projectIDs.add(project.getProjectId());
		return projectIDs;
	}
	
	/**
	 * Get all study uids for project
	 * @param projectID
	 * @return
	 * @throws Exception
	 */
	public static Set<String> getAllStudyUIDsForProject(String projectID) throws Exception {
		List<Study> studies = projectOperations.getAllStudiesForProject(projectID);
		Set<String> studyIDs = new HashSet<String>();
		for (Study study: studies)
			studyIDs.add(study.getStudyUID());
		return studyIDs;
	}
	
	/**
	 * Get all study uids for subject
	 * @param projectID
	 * @param patientID
	 * @return
	 * @throws Exception
	 */
	public static Set<String> getStudyUIDsForSubject(String projectID, String patientID) throws Exception {
		List<Study> studies = projectOperations.getStudiesForProjectAndSubject(projectID, patientID);
		Set<String> studyIDs = new HashSet<String>();
		for (Study study: studies)
			studyIDs.add(study.getStudyUID());
		return studyIDs;
	}
	
	/**
	 * Get first project fro study
	 * @param studyUID
	 * @return
	 * @throws Exception
	 */
	public static String getFirstProjectForStudy(String studyUID) throws Exception {
		Project project = projectOperations.getFirstProjectForStudy(studyUID);
		if (project != null)
			return project.getProjectId();
		else
			return EPADConfig.xnatUploadProjectID;
	}
	
	/**
	 * Get subjectids for project
	 * @param projectID
	 * @return
	 * @throws Exception
	 */
	public static Set<String> getSubjectIDsForProject(String projectID) throws Exception {
		List<Subject> subjects = projectOperations.getSubjectsForProject(projectID);
		Set<String> studyIDs = new HashSet<String>();
		for (Subject subject: subjects)
			studyIDs.add(subject.getSubjectUID());
		return studyIDs;
	}

	/**
	 * Take a directory containing a list of DICOM files and create XNAT representations of the each DICOM image in the
	 * directory.
	 * <p>
	 * This method expects a properties file called xnat_upload.properties in the upload directory. This file should
	 * contain a property called XNATProjectName, which identifies the project ID for the new patients and their studies,
	 * and XNATSessionID, which contains the session key of the user initiating the upload.
	 * 
	 * <p>
	 * If the header of a DICOM image is missing the patient name, patient ID, or study instance UID field then it is
	 * skipped.
	 * 
	 * @param dicomUploadDirectory
	 */
	/**
	 * @param dicomUploadDirectory
	 * @return
	 */
	public static String createProjectEntitiesFromDICOMFilesInUploadDirectory(File dicomUploadDirectory, boolean zip)
	{
		int numberOfDICOMFiles = 0;
		String propertiesFilePath = dicomUploadDirectory.getAbsolutePath() + File.separator
				+ XNAT_UPLOAD_PROPERTIES_FILE_NAME;
		File xnatUploadPropertiesFile = new File(propertiesFilePath);
		try {
			Thread.sleep(5000); // Give it a couple of seconds for the property file to appear
		} catch (InterruptedException e1) {}
		String xnatUserName = null;
		if (!xnatUploadPropertiesFile.exists())
			log.warning("Could not find XNAT upload properties file " + propertiesFilePath);
		else {
			Properties xnatUploadProperties = new Properties();
			FileInputStream propertiesFileStream = null;
			try {
				propertiesFileStream = new FileInputStream(xnatUploadPropertiesFile);
				xnatUploadProperties.load(propertiesFileStream);
				String xnatProjectLabel = xnatUploadProperties.getProperty("XNATProjectName");
				if (xnatProjectLabel != null && xnatProjectLabel.equals(EPADConfig.getParamValue("UnassignedProjectID", "nonassigned")))
					throw new Exception("Files can not be uploaded to this project:" + xnatProjectLabel);
				String xnatSessionID = xnatUploadProperties.getProperty("XNATSessionID");
				xnatUserName = xnatUploadProperties.getProperty("XNATUserName");
				String patientID = xnatUploadProperties.getProperty("SubjectName");
				if (patientID == null) patientID = xnatUploadProperties.getProperty("SubjectID");
				String studyUID = xnatUploadProperties.getProperty("StudyName");
				if (studyUID == null) studyUID = xnatUploadProperties.getProperty("StudyUID");
				String seriesUID = xnatUploadProperties.getProperty("SeriesName");
				if (seriesUID == null) seriesUID = xnatUploadProperties.getProperty("SeriesUID");
				log.info("Found XNAT upload properties file " + propertiesFilePath + " project:" + xnatProjectLabel + " user:" + xnatUserName);
				log.info("Properties:" + xnatUploadProperties);
				log.info("XNAT Properties, projectID:"  + xnatProjectLabel + " username:" + xnatUserName + " patient:" + patientID + " study:" + studyUID + " series:" + seriesUID);
				if (xnatProjectLabel != null) {
					xnatUploadPropertiesFile.delete();
					numberOfDICOMFiles = createProjectEntitiesFromDICOMFilesInUploadDirectory(dicomUploadDirectory, xnatProjectLabel, xnatSessionID, xnatUserName, patientID, studyUID, seriesUID, !zip);
					if (numberOfDICOMFiles != 0)
					{
						projectOperations.createEventLog(xnatUserName, xnatProjectLabel, null, null, null, null, null, dicomUploadDirectory.getName(), "UPLOAD DICOMS", "Number of Dicoms: " +numberOfDICOMFiles, false);
						log.info("Found " + numberOfDICOMFiles + " DICOM file(s) in directory uploaded by " + xnatUserName + " for project " + xnatProjectLabel);
					}
					else
					{
						log.warning("No DICOM files found in upload directory!");
						return null;
					}
				} else {
					log.warning("Missing XNAT project name and/or session ID in properties file" + propertiesFilePath);
				}
				return xnatUserName + ":" + numberOfDICOMFiles;
			} catch (Exception e) {
				log.warning("Error processing upload in directory " + dicomUploadDirectory.getAbsolutePath(), e);
			} finally {
				IOUtils.closeQuietly(propertiesFileStream);
			}
		}
		return xnatUserName + ":" + numberOfDICOMFiles;
	}
	
	public static String getUserNameFromPropertiesFile(File dicomUploadDirectory) {
		String propertiesFilePath = dicomUploadDirectory.getAbsolutePath() + File.separator
				+ XNAT_UPLOAD_PROPERTIES_FILE_NAME;
		File xnatUploadPropertiesFile = new File(propertiesFilePath);
		try {
			Thread.sleep(2000); // Give it a couple of seconds for the property file to appear
		} catch (InterruptedException e1) {}
		if (!xnatUploadPropertiesFile.exists()) {
			log.warning("Could not find XNAT upload properties file " + propertiesFilePath);
		}
		else {
			Properties xnatUploadProperties = new Properties();
			FileInputStream propertiesFileStream = null;
			try {
				propertiesFileStream = new FileInputStream(xnatUploadPropertiesFile);
				xnatUploadProperties.load(propertiesFileStream);
				String xnatProjectLabel = xnatUploadProperties.getProperty("XNATProjectName");
				String xnatSessionID = xnatUploadProperties.getProperty("XNATSessionID");
				String xnatUserName = xnatUploadProperties.getProperty("XNATUserName");
				log.info("Properties:" + xnatUploadProperties);
				return xnatUserName;
			} catch (Exception e) {
				log.warning("Error processing upload in directory " + dicomUploadDirectory.getAbsolutePath(), e);
			} finally {
				IOUtils.closeQuietly(propertiesFileStream);
			}
		}
		return null;
	}
	
	/**
	 * Create subject/study records from uploaded dicoms and add to project 
	 * @param dicomUploadDirectory
	 * @param projectID
	 * @param sessionID
	 * @param username
	 * @return
	 * @throws Exception
	 */
	private static int createProjectEntitiesFromDICOMFilesInUploadDirectory(File dicomUploadDirectory, String projectID, String sessionID, String username, String subjectID, String studyUID, String seriesUID, boolean allFiles) throws Exception
	{
		int numberOfDICOMFiles = 0;
		Collection<File> files = listDICOMFiles(dicomUploadDirectory);
		log.info("Number of files found:" + files.size());
		int nondicoms = 0;
		long i = 0;
		for (File dicomFile : files) {
			try {
				log.info("File " + i++ + " : " +dicomFile.getName());
				if (!isDicomFile(dicomFile)) {
					if (dicomFile.getName().endsWith(".xml"))
					{
						try {
							if (AIMUtil.saveAIMAnnotation(dicomFile, projectID, 0, sessionID, username, true))
								log.warning("Error processing aim file:" + dicomFile.getName());
						} catch (Exception x) {
							log.warning("Error uploading aim file:" + dicomFile.getName() + ":" + x.getMessage());
						}
						dicomFile.delete();
						nondicoms++;
						continue;
					}
					else if ((allFiles || dicomFile.getName().endsWith(".nii")))
					{
						try {
							DefaultEpadOperations.getInstance().createFile(username, projectID, subjectID, studyUID, seriesUID, dicomFile, null, null, sessionID);
						} catch (Exception x) {
							log.warning("Error uploading file:" + dicomFile.getName() + ":" + x.getMessage(), x);
						}
						dicomFile.delete();
						nondicoms++;
						continue;
					}
					else
					{
						try {
							log.warning("Deleting non-dicom file:" + dicomFile.getName());
							dicomFile.delete();
						} catch (Exception x) {log.warning("Error deleting", x);}						
						continue;
					}
				}
				projectOperations.updateUserTaskStatus(username, TaskStatus.TASK_ADD_TO_PROJECT, projectID, dicomUploadDirectory.getName(), "Files processed: " + i, null, null);
				if (createProjectEntitiesFromDICOMFile(dicomFile, projectID, sessionID, username))
					numberOfDICOMFiles++;
			} catch (Throwable x) {
				log.warning("Error processing dicom:" + dicomFile.getName(), x);
				databaseOperations.insertEpadEvent(
						username, 
						"Error processing dicom:" + dicomFile.getName(), 
						dicomFile.getName(), "", dicomFile.getName(), dicomFile.getName(), dicomFile.getName(), projectID, "Error:" + x.getMessage());				}
		}
		projectOperations.updateUserTaskStatus(username, TaskStatus.TASK_ADD_TO_PROJECT, dicomUploadDirectory.getName(), "Files processed: " + numberOfDICOMFiles, null, new Date());
		if (nondicoms != 0)
			projectOperations.createEventLog(username, projectID, null, null, null, null, null, "UPLOAD FILES", "Number of files: " +nondicoms);
		log.info("Number of dicom files in upload:" + numberOfDICOMFiles);
		log.info("Number of non-dicom files in upload:" + nondicoms);
		return numberOfDICOMFiles;
	}
	
	/**
	 * Create subject/study records from uploaded dicom file and add to project 
	 * @param dicomFile
	 * @param projectID
	 * @param sessionID
	 * @param username
	 * @throws Exception
	 */
	public static boolean createProjectEntitiesFromDICOMFile(File dicomFile, String projectID, String sessionID, String username) throws Exception
	{
		DicomObject dicomObject = DicomReader.getDicomObject(dicomFile);
		String dicomPatientName = dicomObject.getString(Tag.PatientName);
		String dicomPatientID = dicomObject.getString(Tag.PatientID);
		String studyUID = dicomObject.getString(Tag.StudyInstanceUID);
		String studyDate = dicomObject.getString(Tag.StudyDate);
		String seriesUID = dicomObject.getString(Tag.SeriesInstanceUID);
		String modality = dicomObject.getString(Tag.Modality);
		log.debug("Uploading dicom, username:" + username + " projectID:" + projectID + " patientName:" + dicomPatientName + " patientID:" + dicomPatientID + " studyUID:" + studyUID + " studyDate:" + studyDate + " seriesUID:" + seriesUID + " modality:" + modality);
		if (dicomPatientID == null || dicomPatientID.trim().length() == 0 
				|| dicomPatientID.equalsIgnoreCase("ANON") 
				|| dicomPatientID.equalsIgnoreCase("Unknown") 
				|| dicomPatientID.contains("%") 
				|| dicomPatientID.equalsIgnoreCase("Anonymous"))
		{
			String message = "Invalid patientID:'" + dicomPatientID + "' file:" + dicomFile.getName() + ", Rejecting file";
			log.warning(message);
			if (dicomPatientID != null)
				message = "Invalid non-unique patient ID " + dicomPatientID + " in DICOM file";
			if (dicomPatientID != null && dicomPatientID.contains("%"))
			{
				message = "An invalid character in patient ID " + dicomPatientID;
			}
			databaseOperations.insertEpadEvent(
					username, 
					message, 
					seriesUID, "", "Invalid PatientID:" + dicomPatientID, dicomPatientName, studyUID, projectID, "Error in Upload");					
			dicomFile.delete();
			projectOperations.createEventLog(username, projectID, dicomPatientID, studyUID, seriesUID, null, null, dicomFile.getName(), "UPLOAD SERIES", message, true);
			return false;
		}
		if (pendingUploads.size() < 300)
			pendingUploads.put(studyUID, username + ":" + projectID);
		if (pendingPNGs.size() < 300)
			pendingPNGs.put(seriesUID, username + ":" + projectID);
		if (dicomPatientID != null && studyUID != null) {
			//databaseOperations.deleteSeriesOnly(seriesUID); // This will recreate all images
			if (dicomPatientName == null) dicomPatientName = "";
			dicomPatientName = dicomPatientName.toUpperCase(); // DCM4CHEE stores the patient name as upper case
			
			addSubjectAndStudyToProject(dicomPatientID, dicomPatientName, studyUID, studyDate, projectID, sessionID, username);
			
			if ("SEG".equals(modality))
			{
				try {
//					List<EPADAIM> aims = databaseOperations.getAIMsByDSOSeries(projectID, dicomPatientID, seriesUID);
//					List<ImageAnnotation> ias = AIMQueries.getAIMImageAnnotations(AIMSearchType.SERIES_UID, seriesUID, username, 1, 50);
//					if (ias.size() == 0 || aims.size() == 0) 
//						AIMUtil.generateAIMFileForDSO(dicomFile, username, projectID);
					List<EPADAIM> aims = databaseOperations.getAIMsByDSOSeries(null, dicomPatientID, seriesUID);
					List<ImageAnnotation> ias = AIMQueries.getAIMImageAnnotations(AIMSearchType.SERIES_UID, seriesUID, username, 1, 50);
					boolean generateAim = false;
					if (aims.size() == 1 && aims.get(0).projectID.equals(EPADConfig.xnatUploadProjectID) && !projectID.equals(EPADConfig.xnatUploadProjectID))
						generateAim = true;
					if (generateAim || ias.size() == 0 || aims.size() == 0) 
					{
						AIMUtil.generateAIMFileForDSO(dicomFile, username, projectID);
					}
					else
					{
						boolean projectAIMExists = false;
						for (EPADAIM aim: aims)
						{
							if (aim.projectID.equals(projectID))
							{
								projectAIMExists = true;
								break;
							}
						}
						if (!projectAIMExists)
							databaseOperations.addProjectToAIM(projectID, aims.get(0).aimID);
					}
					Set<String> imageUIDs = Dcm4CheeDatabase.getInstance().getDcm4CheeDatabaseOperations().getImageUIDsForSeries(seriesUID);
					if (false && !imageUIDs.isEmpty()) {
						String message = "DSO for  patientID:" + dicomPatientID + " Series:" + seriesUID + " file:" + dicomFile.getName() + " already exists. Please delete DSO before reuploading";
						log.warning(message);
						databaseOperations.insertEpadEvent(
								username, 
								message, 
								seriesUID, "", dicomPatientID, dicomPatientName, studyUID, projectID, "Error in Upload");					
						dicomFile.delete();
						projectOperations.createEventLog(username, projectID, dicomPatientID, studyUID, seriesUID, null, null, dicomFile.getName(), "UPLOAD DSO", message, true);
						return false;
					}
					String imageUID = dicomObject.getString(Tag.SOPInstanceUID);
					String pngMaskDirectoryPath = EPADConfig.getEPADWebServerPNGDir() + "/studies/" + studyUID + "/series/" + seriesUID + "/images/"
							+ imageUID + "/masks/";
					File pngDirectory = new File(pngMaskDirectoryPath);
					if (pngDirectory.exists())
					{
//						File[] files = pngDirectory.listFiles();
//						for (File file: files)
//						{
//							//file.delete();
//						}
					}
				} catch (Exception x) {
					log.warning("Error generating DSO Annotation:", x);
					databaseOperations.insertEpadEvent(
							username, 
							"Error generating DSO Annotation", 
							seriesUID, "", dicomPatientID, dicomPatientName, studyUID, projectID, "Upload " + dicomFile.getName());					
					projectOperations.createEventLog(username, projectID, dicomPatientID, studyUID, seriesUID, null, null, dicomFile.getName(), "UPLOAD DSO", "Error generating DSO Annotation", true);
				}
			}
		} else {
			log.warning("Missing patient ID or studyUID in DICOM file " + dicomFile.getAbsolutePath());
			databaseOperations.insertEpadEvent(
					username, 
					"Missing patient ID or studyUID in DICOM file", 
					seriesUID, "", dicomPatientID, dicomPatientName, studyUID, projectID, "Process Upload");					
			projectOperations.createEventLog(username, projectID, dicomPatientID, studyUID, seriesUID, null, null, null, "UPLOAD DSO", "Missing patient ID or studyUID in DICOM file " + dicomFile.getName(), true);
		}
		return true;
	}

	/**
	 * Add subject/study records to project 
	 * @param subjectID
	 * @param subjectName
	 * @param studyUID
	 * @param projectID
	 * @param sessionID
	 * @param username
	 */
	public static void addSubjectAndStudyToProject(String subjectID, String subjectName, String studyUID, String studyDate, String projectID, String sessionID, String username) {
		try {
			projectOperations.createSubject(username, subjectID, subjectName, null, null);
			projectOperations.createStudy(username, studyUID, subjectID, "", getDate(studyDate));
			log.info("Upload/Transfer: Adding Study:" +  studyUID + " Subject:" + subjectID + " to Project:" + projectID);
			projectOperations.addStudyToProject(username, studyUID, subjectID, projectID);
		} catch (Exception e) {
			log.warning("Error creating subject/study in EPAD:", e);
		}
	}
	
	private static Collection<File> listDICOMFiles(File dir)
	{
		log.info("Checking upload directory:" + dir.getAbsolutePath());
		Set<File> files = new HashSet<File>();
		if (!dir.isDirectory())
		{
			log.info("Not a directory:" + dir.getAbsolutePath());
			return files;
		}
		if (dir.listFiles() != null) {
			for (File entry : dir.listFiles()) {
				if (isDicomFile(entry))
				{
					files.add(entry);
				}
				else if (!entry.isDirectory() && entry.getName().indexOf(".") == -1)
				{
					try {
						File newFile = new File(entry.getParentFile(), entry.getName()+".dcm");
						entry.renameTo(newFile);
						files.add(newFile);
					} catch (Exception x) {log.warning("Error renaming", x);}
				}
				else if (entry.isDirectory()) 
				{
					files.addAll(listDICOMFiles(entry));
				}
				else
				{
					files.add(entry);
//					try {
//						log.warning("Deleting non-dicom file:" + entry.getName());
//						entry.delete();
//					} catch (Exception x) {log.warning("Error deleting", x);}
				}
			}
		}
		else if (!dir.getName().endsWith(".zip")){
			try {
				log.warning("Deleting non-dicom file:" + dir.getName());
				dir.delete();
			} catch (Exception x) {log.warning("Error deleting", x);}
		}
		
		return files;
	}
	
	public static void sendNewPassword(String loggedInUsername, String username) throws Exception
	{
		log.info("New password requested for " + username);
		User loggedInUser =  projectOperations.getUser(loggedInUsername);
		if (loggedInUser == null)
			throw new Exception("User not found " + loggedInUsername);
		User user = projectOperations.getUser(username);
		if (user == null)
			throw new Exception("User not found " + username);
		if (!loggedInUsername.equals(username) && !loggedInUser.isAdmin() && !loggedInUsername.equals(user.getCreator()))
			throw new Exception("No permissions to reset " + username + "'s password");
		String newPwd = username;
		if (newPwd.length() > 4) newPwd = newPwd.substring(0, 4);
		newPwd = newPwd + new IdGenerator().generateId(6);
		user.setPassword(newPwd);
		boolean tls = "true".equalsIgnoreCase(EPADConfig.getParamValue("SMTPtls", "true"));
		MailUtil mu = new MailUtil(	EPADConfig.getParamValue("SMTPHost", "smtp.gmail.com"), 
									EPADConfig.getParamValue("SMTPPort", "587"), 
									EPADConfig.getParamValue("MailUser", "epadstanford@gmail.com"), 
									EPADConfig.getParamValue("MailPassword"), 
									true);
		// No password, try sendMail
		if (EPADConfig.getParamValue("MailPassword") == null || EPADConfig.getParamValue("MailPassword").trim().length() == 0) {
			mu = new MailUtil();
		}
		mu.send(user.getEmail(), 
				EPADConfig.xnatServer + "_noreply@stanford.edu", 
				"New password for ePAD@" + EPADConfig.xnatServer, 
				"Hello " + user.getFirstName() + " " + user.getLastName() + ",\n\nYour new ePAD password is " + newPwd + "\n\nPlease login and reset your password.\n\nRegards\n\nePAD Team");
		projectOperations.updateUser("admin", username,
				null, null, null, newPwd, null, null, 
				new ArrayList<String>(), new ArrayList<String>());
	}

	/**
	 * @param file
	 * @return
	 */
	public static boolean isDicomFile(File file)
	{
		return file.isFile()
				&& (file.getName().toLowerCase().endsWith(".dcm") || file.getName().toLowerCase().endsWith(".dso") || file.getName().toLowerCase().endsWith(".pres"))
				&& !file.getName().startsWith(".");
		// return file.isFile() && DicomFileUtil.hasMagicWordInHeader(file);
	}
	
	static SimpleDateFormat dateformat = new SimpleDateFormat("yyyyMMdd");
	private static Date getDate(String dateStr)
	{
		try
		{
			return dateformat.parse(dateStr);
		}
		catch (Exception x)
		{
			return null;
		}
	}
	
}
