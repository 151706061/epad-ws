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
import java.io.IOException;
import java.util.Collection;
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
import edu.stanford.epad.common.util.EPADLogger;
import edu.stanford.epad.epadws.aim.AIMQueries;
import edu.stanford.epad.epadws.aim.AIMSearchType;
import edu.stanford.epad.epadws.aim.AIMUtil;
import edu.stanford.epad.epadws.epaddb.EpadDatabase;
import edu.stanford.epad.epadws.epaddb.EpadDatabaseOperations;
import edu.stanford.epad.epadws.models.Project;
import edu.stanford.epad.epadws.models.Study;
import edu.stanford.epad.epadws.models.Subject;
import edu.stanford.epad.epadws.queries.XNATQueries;
import edu.stanford.epad.epadws.xnat.XNATCreationOperations;
import edu.stanford.epad.epadws.xnat.XNATSessionOperations;
import edu.stanford.epad.epadws.xnat.XNATUtil;
import edu.stanford.hakan.aim3api.base.ImageAnnotation;

public class UserProjectService {
	private static final EPADLogger log = EPADLogger.getInstance();
	
	public static Map<String, String> pendingUploads = new HashMap<String, String>();

	private static final EpadProjectOperations projectOperations = DefaultEpadProjectOperations.getInstance();	
	private static final EpadDatabaseOperations databaseOperations = EpadDatabase.getInstance().getEPADDatabaseOperations();	
	
	private static final String XNAT_UPLOAD_PROPERTIES_FILE_NAME = "xnat_upload.properties";
	
	public static boolean isCollaborator(String sessionID, String username, String projectID) throws Exception {
		if (EPADConfig.UseEPADUsersProjects) {
			return projectOperations.isCollaborator(username, projectID);
		} else {
			return XNATQueries.isCollaborator(sessionID, username, projectID);
		}				
	}
	
	public static boolean isOwner(String sessionID, String username, String projectID) throws Exception {
		if (EPADConfig.UseEPADUsersProjects) {
			return projectOperations.isOwner(username, projectID);
		} else {
			return XNATQueries.isOwner(sessionID, username, projectID);
		}				
	}
	
	public static boolean isMember(String sessionID, String username, String projectID) throws Exception {
		if (EPADConfig.UseEPADUsersProjects) {
			return projectOperations.isMember(username, projectID);
		} else {
			return XNATQueries.isMember(sessionID, username, projectID);
		}				
	}
	
	public static Set<String> getAllProjectIDs() throws Exception {
		if (EPADConfig.UseEPADUsersProjects) {
			List<Project> projects = projectOperations.getAllProjects();
			Set<String> projectIDs = new HashSet<String>();
			for (Project project: projects)
				projectIDs.add(project.getProjectId());
			return projectIDs;
		} else {
			String adminSessionID = XNATSessionOperations.getXNATAdminSessionID();
			return XNATQueries.allProjectIDs(adminSessionID);
		}				
	}
	
	public static Set<String> getAllStudyUIDsForProject(String projectID) throws Exception {
		if (EPADConfig.UseEPADUsersProjects) {
			List<Study> studies = projectOperations.getAllStudiesForProject(projectID);
			Set<String> studyIDs = new HashSet<String>();
			for (Study study: studies)
				studyIDs.add(study.getStudyUID());
			return studyIDs;
		} else {
			String adminSessionID = XNATSessionOperations.getXNATAdminSessionID();
			return XNATQueries.getAllStudyUIDsForProject(projectID, adminSessionID);
		}				
	}
	
	public static Set<String> getStudyUIDsForSubject(String projectID, String patientID) throws Exception {
		if (EPADConfig.UseEPADUsersProjects) {
			List<Study> studies = projectOperations.getStudiesForProjectAndSubject(projectID, patientID);
			Set<String> studyIDs = new HashSet<String>();
			for (Study study: studies)
				studyIDs.add(study.getStudyUID());
			return studyIDs;
		} else {
			String adminSessionID = XNATSessionOperations.getXNATAdminSessionID();
			return XNATQueries.getStudyUIDsForSubject(adminSessionID, projectID, patientID);
		}				
	}
	
	public static String getFirstProjectForStudy(String studyUID) throws Exception {
		if (EPADConfig.UseEPADUsersProjects) {
			Project project = projectOperations.getFirstProjectForStudy(studyUID);
			return project.getProjectId();
		} else {
			String adminSessionID = XNATSessionOperations.getXNATAdminSessionID();
			return XNATQueries.getFirstProjectForStudy(adminSessionID, studyUID);	
		}						
	}
	
	public static Set<String> getSubjectIDsForProject(String projectID) throws Exception {
		if (EPADConfig.UseEPADUsersProjects) {
			List<Subject> subjects = projectOperations.getSubjectsForProject(projectID);
			Set<String> studyIDs = new HashSet<String>();
			for (Subject subject: subjects)
				studyIDs.add(subject.getSubjectUID());
			return studyIDs;
		} else {
			String adminSessionID = XNATSessionOperations.getXNATAdminSessionID();
			return XNATQueries.getSubjectIDsForProject(adminSessionID, projectID);
		}						
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
	public static String createProjectEntitiesFromDICOMFilesInUploadDirectory(File dicomUploadDirectory)
	{
		String propertiesFilePath = dicomUploadDirectory.getAbsolutePath() + File.separator
				+ XNAT_UPLOAD_PROPERTIES_FILE_NAME;
		File xnatUploadPropertiesFile = new File(propertiesFilePath);
		try {
			Thread.sleep(2000); // Give it a couple of seconds for the property file to appear
		} catch (InterruptedException e1) {}
		if (!xnatUploadPropertiesFile.exists())
			log.warning("Could not find XNAT upload properties file " + propertiesFilePath);
		else {
			Properties xnatUploadProperties = new Properties();
			FileInputStream propertiesFileStream = null;
			try {
				log.info("Found XNAT upload properties file " + propertiesFilePath);
				propertiesFileStream = new FileInputStream(xnatUploadPropertiesFile);
				xnatUploadProperties.load(propertiesFileStream);
				String xnatProjectLabel = xnatUploadProperties.getProperty("XNATProjectName");
				String xnatSessionID = xnatUploadProperties.getProperty("XNATSessionID");
				String xnatUserName = xnatUploadProperties.getProperty("XNATUserName");
				if (xnatProjectLabel != null && xnatSessionID != null) {
					int numberOfDICOMFiles = createProjectEntitiesFromDICOMFilesInUploadDirectory(dicomUploadDirectory, xnatProjectLabel, xnatSessionID, xnatUserName);
					if (numberOfDICOMFiles != 0)
						log.info("Found " + numberOfDICOMFiles + " DICOM file(s) in upload directory");
					else
						log.warning("No DICOM files found in upload directory!");
				} else {
					log.warning("Missing XNAT project name and/or session ID in properties file" + propertiesFilePath);
				}
				return xnatUserName;
			} catch (Exception e) {
				log.warning("Error processing upload in directory " + propertiesFilePath, e);
			} finally {
				IOUtils.closeQuietly(propertiesFileStream);
			}
		}
		return null;
	}
	
	public static int createProjectEntitiesFromDICOMFilesInUploadDirectory(File dicomUploadDirectory, String projectID, String sessionID, String username) throws Exception
	{
		int numberOfDICOMFiles = 0;
		for (File dicomFile : listDICOMFiles(dicomUploadDirectory)) {
			createProjectEntitiesFromDICOMFile(dicomFile, projectID, sessionID, username);
			numberOfDICOMFiles++;
		}
		return numberOfDICOMFiles;
	}
	
	public static void createProjectEntitiesFromDICOMFile(File dicomFile, String projectID, String sessionID, String username) throws Exception
	{
		DicomObject dicomObject = DicomReader.getDicomObject(dicomFile);
		String dicomPatientName = dicomObject.getString(Tag.PatientName);
		String dicomPatientID = dicomObject.getString(Tag.PatientID);
		String studyUID = dicomObject.getString(Tag.StudyInstanceUID);
		String seriesUID = dicomObject.getString(Tag.SeriesInstanceUID);
		String modality = dicomObject.getString(Tag.Modality);
		if (dicomPatientID == null || dicomPatientID.trim().length() == 0 
				|| dicomPatientID.equalsIgnoreCase("ANON") 
				|| dicomPatientID.equalsIgnoreCase("Unknown") 
				|| dicomPatientID.equalsIgnoreCase("Anonymous"))
		{
			String message = "Invalid patientID:" + dicomPatientID + " file:" + dicomFile.getName() + ", Rejecting file";
			log.warning(message);
			dicomFile.delete();
			projectOperations.userErrorLog(username, message);
			return;
		}
		pendingUploads.put(seriesUID, username + ":" + projectID);
		if (dicomPatientID != null && dicomPatientName != null && studyUID != null) {
			databaseOperations.deleteSeriesOnly(seriesUID); // This will recreate all images
			dicomPatientName = dicomPatientName.toUpperCase(); // DCM4CHEE stores the patient name as upper case
			
			addSubjectAndStudyToProject(dicomPatientID, dicomPatientName, studyUID, projectID, sessionID, username);
			
			if ("SEG".equals(modality))
			{
				try {
					List<ImageAnnotation> ias = AIMQueries.getAIMImageAnnotations(AIMSearchType.SERIES_UID, seriesUID, username, 1, 50);
					if (ias.size() == 0) 
						AIMUtil.generateAIMFileForDSO(dicomFile, username, projectID);
				} catch (Exception x) {
					log.warning("Error generating DSO Annotation:", x);
				}
			}
		} else
			log.warning("Missing patient name, ID or studyUID in DICOM file " + dicomFile.getAbsolutePath());
	}

	public static void addSubjectAndStudyToProject(String subjectID, String subjectName, String studyUID, String projectID, String sessionID, String username) {
		if (!EPADConfig.UseEPADUsersProjects) {
			String xnatSubjectLabel = XNATUtil.subjectID2XNATSubjectLabel(subjectID);
			XNATCreationOperations.createXNATSubject(projectID, xnatSubjectLabel, subjectName, sessionID);
			XNATCreationOperations.createXNATDICOMStudyExperiment(projectID, xnatSubjectLabel, studyUID, sessionID);
		} else {
			try {
				projectOperations.createSubject(username, subjectID, subjectName, null, null);
				projectOperations.createStudy(username, studyUID, subjectID);
				log.info("Upload/Transfer: Adding Study:" +  studyUID + " Subject:" + subjectID + " to Project:" + projectID);
				projectOperations.addStudyToProject(username, studyUID, subjectID, projectID);
			} catch (Exception e) {
				log.warning("Error creating subject/study in EPAD:", e);
			}
		}
		
	}
	
	private static Collection<File> listDICOMFiles(File dir)
	{
		Set<File> files = new HashSet<File>();
		if (dir.listFiles() != null) {
			for (File entry : dir.listFiles()) {
				if (isDicomFile(entry))
					files.add(entry);
				else
					files.addAll(listDICOMFiles(entry));
			}
		}
		return files;
	}

	public static boolean isDicomFile(File file)
	{
		return file.isFile()
				&& (file.getName().toLowerCase().endsWith(".dcm") || file.getName().toLowerCase().endsWith(".dso"));
		// return file.isFile() && DicomFileUtil.hasMagicWordInHeader(file);
	}
	
}
