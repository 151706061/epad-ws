package edu.stanford.epad.epadws.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;

import com.pixelmed.dicom.AgeStringAttribute;
import com.pixelmed.dicom.Attribute;
import com.pixelmed.dicom.AttributeList;
import com.pixelmed.dicom.AttributeTag;
import com.pixelmed.dicom.CodeStringAttribute;
import com.pixelmed.dicom.DateAttribute;
import com.pixelmed.dicom.DecimalStringAttribute;
import com.pixelmed.dicom.DicomException;
import com.pixelmed.dicom.IntegerStringAttribute;
import com.pixelmed.dicom.LongStringAttribute;
import com.pixelmed.dicom.PersonNameAttribute;
import com.pixelmed.dicom.ShortStringAttribute;
import com.pixelmed.dicom.SpecificCharacterSet;
import com.pixelmed.dicom.TagFromName;
import com.pixelmed.dicom.TimeAttribute;
import com.pixelmed.dicom.UniqueIdentifierAttribute;
import com.pixelmed.network.ApplicationEntity;
import com.pixelmed.network.ApplicationEntityMap;
import com.pixelmed.query.QueryTreeModel;
import com.pixelmed.query.QueryTreeRecord;

import edu.stanford.epad.common.util.EPADConfig;
import edu.stanford.epad.common.util.EPADFileUtils;
import edu.stanford.epad.dtos.RemotePAC;
import edu.stanford.epad.dtos.RemotePACEntity;
import edu.stanford.epad.dtos.RemotePACQueryConfig;
import edu.stanford.epad.dtos.internal.DCM4CHEEStudy;
import edu.stanford.epad.dtos.internal.DCM4CHEEStudyList;
import edu.stanford.epad.epadws.epaddb.EpadDatabase;
import edu.stanford.epad.epadws.epaddb.EpadDatabaseOperations;
import edu.stanford.epad.epadws.models.Project;
import edu.stanford.epad.epadws.models.RemotePACQuery;
import edu.stanford.epad.epadws.models.Study;
import edu.stanford.epad.epadws.models.Subject;
import edu.stanford.epad.epadws.models.User;
import edu.stanford.epad.epadws.queries.Dcm4CheeQueries;

/**
 * Class to create Remote PAC Records and Query/Retrieve data for Remote PACs
 * 
 * @author Dev Gude
 *
 */
public class RemotePACService extends RemotePACSBase {

	static RemotePACService rpsinstance;
	
	public static final int MAX_CACHE_ENTRIES = 25000;
	public static final int MAX_SERIES_QUERY = 500;
	public static final int MAX_INSTANCE_QUERY = 7000;
	static Map<String, QueryTreeRecord> remoteQueryCache = new HashMap<String, QueryTreeRecord>();
	
	// SerieUID to userName:projectID
	public static Map<String, String> pendingTransfers = new HashMap<String, String>();
	
	// AETitle to userName
	public static Map<String, String> monitorTransfers = new HashMap<String, String>();
	public static Map<String, Long> monitorStart = new HashMap<String, Long>();
	
	public static RemotePACService getInstance() throws Exception {
		if (rpsinstance == null)
		{
			rpsinstance = new RemotePACService();
		}
		return rpsinstance;
	}
	
	private RemotePACService() throws DicomException, IOException {
		super();
	}

	public static void checkPropertiesFile() {
		File propertiesFile = new File(EPADConfig.getEPADRemotePACsConfigFilePath());
		if (!propertiesFile.exists()) {
			BufferedReader reader = null;
			InputStream is = null;
			StringBuilder sb = new StringBuilder();
			try {
				is = EPADFileUtils.class.getClassLoader().getResourceAsStream(propertiesFile.getName());
				reader = new BufferedReader(new InputStreamReader(is, "UTF8"));
				String line = null;
				while ((line = reader.readLine()) != null) {
					sb.append(line);
					sb.append("\n");
				}
			} catch (Exception x) {
				log.warning("Error creating properties file", x);
				return;
			} finally {
				if (reader != null)
					IOUtils.closeQuietly(reader);
				else if (is != null)
					IOUtils.closeQuietly(is);
			}
			EPADFileUtils.write(propertiesFile, sb.toString().replace("_HOSTNAME_", EPADConfig.xnatServer));
		}
	}
	
	/**
	 * Get all configured remote PACs
	 * @return
	 */
	public List<RemotePAC> getRemotePACs() {
		List<RemotePAC> rps = new ArrayList<RemotePAC>();
		ApplicationEntityMap aeMap = networkApplicationInformation.getApplicationEntityMap();
		for (Object aeName: aeMap.keySet()) {
			ApplicationEntity ae = (ApplicationEntity) aeMap.get(aeName);
			String localName = networkApplicationInformation.getLocalNameFromApplicationEntityTitle(aeName.toString());
			RemotePAC rp = new RemotePAC(localName, ae.getDicomAETitle(), ae.getPresentationAddress().getHostname(),
					ae.getPresentationAddress().getPort(), ae.getQueryModel(), ae.getPrimaryDeviceType());
			rps.add(rp);
		}
		return rps;
	}

	/**
	 * Get remote PAC by PAC ID
	 * @param pacID
	 * @return
	 */
	public RemotePAC getRemotePAC(String pacID) {
		ApplicationEntityMap aeMap = networkApplicationInformation.getApplicationEntityMap();
		for (Object aeName: aeMap.keySet()) {
			ApplicationEntity ae = (ApplicationEntity) aeMap.get(aeName);
			String localName = networkApplicationInformation.getLocalNameFromApplicationEntityTitle(aeName.toString());
			if (localName.equals(pacID))
			{
				return new RemotePAC(localName, ae.getDicomAETitle(), ae.getPresentationAddress().getHostname(),
					ae.getPresentationAddress().getPort(), ae.getQueryModel(), ae.getPrimaryDeviceType());
			}
		}
		return null;
	}
	
	/**
	 * Add a Remote PAC to configuration file
	 * @param pac
	 * @throws Exception
	 */
	public synchronized void addRemotePAC(String loggedInUser, RemotePAC pac) throws Exception {
		User user = DefaultEpadProjectOperations.getInstance().getUser(loggedInUser);
		if (!user.isAdmin() && !user.hasPermission(User.CreatePACPermission))
			throw new Exception("No permission to create PAC configuration");
		addRemotePAC(
				pac.pacID,
				pac.aeTitle,
				pac.hostname,
				pac.port,
				pac.queryModel,
				pac.primaryDeviceType);
	}
	
	/**
	 * Modify a Remote PAC in configuration file
	 * @param pac
	 * @throws Exception
	 */
	public synchronized void modifyRemotePAC(String loggedInUser, RemotePAC pac) throws Exception {
		User user = DefaultEpadProjectOperations.getInstance().getUser(loggedInUser);
		if (!user.isAdmin() && !user.hasPermission(User.CreatePACPermission))
			throw new Exception("No permission to modify PAC configuration");
		removeRemotePAC(pac.pacID);
		addRemotePAC(loggedInUser, pac);
	}
	
	/**
	 * Remove a Remote PAC from configuration file
	 * @param pac
	 * @throws Exception
	 */
	public synchronized void removeRemotePAC(String loggedInUser, RemotePAC pac) throws Exception {
		User user = DefaultEpadProjectOperations.getInstance().getUser(loggedInUser);
		if (!user.isAdmin() && !user.hasPermission(User.CreatePACPermission))
			throw new Exception("No permission to delete PAC configuration");
		List<RemotePACQuery> queries = getRemotePACQueries(pac.pacID);
		if (queries.size() > 0)
		{
			throw new Exception("Periodic queries have been configured for PAC:" + pac.pacID + ", it can not be deleted");
		}
		if (pac.hostname.equalsIgnoreCase(EPADConfig.xnatServer))
			throw new Exception("Local PAC can not be deleted");
		removeRemotePAC(pac.pacID);
		this.storeProperties(pac.pacID + " deleted by EPAD " + new Date());
	}

	/**
	 * Get all Remote PAC automatic daily queries
	 * @param pacID
	 * @return
	 * @throws Exception
	 */
	public List<RemotePACQuery> getRemotePACQueries(String pacID) throws Exception {
		List<RemotePACQuery> queries = new ArrayList<RemotePACQuery>();
		List objects = new RemotePACQuery().getObjects("pacID = '" + pacID + "' order by pacid");
		queries.addAll(objects);
		return queries;
	}

	/**
	 * Get  Remote PAC automatic daily query for pac and subject
	 * @param pacID
	 * @param subjectUID
	 * @return
	 * @throws Exception
	 */
	public RemotePACQuery getRemotePACQuery(String pacID, String subjectUID) throws Exception {
		Subject subject = DefaultEpadProjectOperations.getInstance().getSubject(subjectUID);
		if (subject == null)
			throw new Exception("Subject:" + subjectUID + " not found");
		List objects = new RemotePACQuery().getObjects("pacID = '" + pacID + "' and subject_id ='" + subject.getId() + "'");
		if (objects.size() > 1)
		{
			log.warning("More than one query found for PacID:" + pacID + " and SubjectID:" + subjectUID);
		}
		else if (objects.size() == 0)
		{
			return null;
		}
		return (RemotePACQuery) objects.get(0);
	}

	/**
	 * Create  Remote PAC automatic daily query configuration in database
	 * @param username
	 * @param pacID
	 * @param subjectUID
	 * @param patientName
	 * @param modality
	 * @param studyDate
	 * @param weekly
	 * @param projectID
	 * @return
	 * @throws Exception
	 */
	public RemotePACQuery createRemotePACQuery(String username, String pacID, String subjectUID, String patientName, String modality, String studyDate, boolean weekly, String projectID) throws Exception {
		User user = DefaultEpadProjectOperations.getInstance().getUser(username);
		if (!user.isAdmin() && !user.hasPermission(User.CreateAutoPACQueryPermission))
			throw new Exception("No permission to create PAC Patient Query configuration");
		RemotePACQuery query = null;
		try {
			query = getRemotePACQuery(pacID, subjectUID);
			if (query != null && !user.isAdmin() && !user.getUsername().equals(query.getRequestor()));
				throw new Exception("Remote PAC and Patient already configured for periodic query");
		} catch (Exception x) {};
		Project project = DefaultEpadProjectOperations.getInstance().getProject(projectID);
		if (project == null)
			throw new Exception("Project " + projectID + " not found");
		RemotePAC pac = this.getRemotePAC(pacID);
		if (pac == null)
			throw new Exception("Remote PAC " + pacID + " not found");
		Subject subject = DefaultEpadProjectOperations.getInstance().getSubject(subjectUID);
		if (subject == null)
		{
			subject = DefaultEpadProjectOperations.getInstance().createSubject(username, subjectUID, patientName, null, null);
		}
		else if (studyDate ==  null || studyDate.trim().length() == 0)
		{
			try {
				List<Study> studies = DefaultEpadProjectOperations.getInstance().getStudiesForSubject(subject.getSubjectUID());
				Set<String> studyUIDs = new HashSet<String>();
				for (Study study: studies)
				{
					studyUIDs.add(study.getStudyUID());
				}
				DCM4CHEEStudyList dcm4CheeStudyList = Dcm4CheeQueries.getStudies(studyUIDs);
				Date date = null;
				for (DCM4CHEEStudy dcs: dcm4CheeStudyList.ResultSet.Result)
				{
					if (date == null || getDate(dcs.dateAcquired).after(date))
						date = getDate(dcs.dateAcquired);
				}
				if (date != null)
				{
					Calendar cal = Calendar.getInstance();
					cal.setTime(date);
					cal.add(Calendar.DATE, 1);
					studyDate = dateformat.format(cal.getTime());
					log.info("Subject:" + subject.getSubjectUID() + " Last Study Date from DCM4CHE:" + studyDate);	
				}
			} catch (Exception x) {};
			
		}
		if (query == null)
			query = new RemotePACQuery();
		query.setRequestor(username);
		query.setPacId(pacID);
		query.setSubjectId(subject.getId());
		query.setProjectId(project.getId());
		query.setEnabled(true);
		
		// TODO: Validate modality. How???
		query.setModality(modality);
		if (getDate(studyDate) != null)
		{
			query.setLastStudyDate(studyDate);
		}
		if (weekly)
			query.setPeriod("Weekly");
		else
			query.setPeriod("Daily");
		query.save();
		return query;
	}

	/**
	 * Delete  Remote PAC automatic daily query configuration from database
	 * @param pacID
	 * @param subjectUID
	 * @throws Exception
	 */
	public void removeRemotePACQuery(String loggedInUser, String pacID, String subjectUID) throws Exception {
		User user = DefaultEpadProjectOperations.getInstance().getUser(loggedInUser);
		if (!user.isAdmin() && !user.hasPermission(User.CreateAutoPACQueryPermission))
			throw new Exception("No permission to delete PAC Patient Query configuration");
		Subject subject = DefaultEpadProjectOperations.getInstance().getSubject(subjectUID);
		if (subject == null)
			throw new Exception("Subject:" + subjectUID + " not found");
		int rows = new RemotePACQuery().deleteObjects("pacID = '" + pacID + "' and subject_id ='" + subject.getId() + "'");
		if (rows > 0)
			log.info("Query for PacID:" + pacID +" and SubjectID:" + subjectUID + " was deleted");
	}

	/**
	 * Disable  Remote PAC automatic daily query configuration from database
	 * @param pacID
	 * @param subjectID
	 * @throws Exception
	 */
	public void disableRemotePACQuery(String loggedInUser, String pacID, String subjectID) throws Exception {
		Subject subject = DefaultEpadProjectOperations.getInstance().getSubject(subjectID);
		if (subject == null)
			throw new Exception("Subject not found, ID:" + subjectID);
		List objects = new RemotePACQuery().getObjects("pacID = '" + pacID + "' and subject_id =" + subject.getId());
		if (objects.size() > 1)
		{
			log.warning("More than one query found for PacID:" + pacID + " and SubjectID:" + subjectID);
		}
		for (Object object: objects)
		{
			RemotePACQuery query = (RemotePACQuery) object;
			query.setEnabled(false);
			query.save();
		}
		if (objects.size() == 0)
			throw new Exception("Remote Query Configuration not found");
	}

	/**
	 * Enable  Remote PAC automatic daily query configuration from database
	 * @param pacID
	 * @param subjectID
	 * @throws Exception
	 */
	public void enableRemotePACQuery(String loggedInUser, String pacID, String subjectID) throws Exception {
		Subject subject = DefaultEpadProjectOperations.getInstance().getSubject(subjectID);
		if (subject == null)
			throw new Exception("Subject not found, ID:" + subjectID);
		List objects = new RemotePACQuery().getObjects("pacID = '" + pacID + "' and subject_id =" + subject.getId());
		if (objects.size() > 1)
		{
			log.warning("More than one query found for PacID:" + pacID + " and SubjectID:" + subjectID);
		}
		if (objects.size() > 0)
		{
			RemotePACQuery query = (RemotePACQuery) objects.get(0);
			query.setEnabled(true);
			query.save();
		}
		else
			throw new Exception("Remote Query Configuration not found");
	}

	/**
	 * Convert Remote PAC automatic database configuration to dto
	 * @param query
	 * @return
	 * @throws Exception
	 */
	public RemotePACQueryConfig getConfig(RemotePACQuery query) throws Exception {
		Subject subject = (Subject) new Subject(query.getSubjectId()).retrieve();
		Project project = (Project) new Project(query.getProjectId()).retrieve();
		return new RemotePACQueryConfig(query.getPacId(), query.getRequestor(),
				subject.getSubjectUID(), project.getProjectId(), query.getModality(), query.getPeriod(),
				query.isEnabled(), query.getLastStudyDate(), dateformat.format(query.getLastQueryTime()),
				query.getLastQueryStatus());
	}
	
	public static Map<String, AttributeList> currentPACQueries = new HashMap<String, AttributeList>();
	/**
	 * Query a Remote PAC given patient/studydate filters
	 * @param pac
	 * @param patientNameFilter
	 * @param patientIDFilter
	 * @param studyDateFilter
	 * @return
	 * @throws Exception
	 */
	public synchronized List<RemotePACEntity> queryRemoteData(RemotePAC pac, String patientNameFilter, String patientIDFilter, String studyIDFilter, String studyDateFilter, boolean patientsOnly, boolean studiesOnly) throws Exception {
		return queryRemoteData(pac, patientNameFilter, patientIDFilter, studyIDFilter, studyDateFilter, null, null, null, 
				patientsOnly, studiesOnly);
	}	
	
	public synchronized List<RemotePACEntity> queryRemoteData(RemotePAC pac, String patientNameFilter, String patientIDFilter, String studyIDFilter, String studyDateFilter, String[] tagGroups, String[] tagElements, String[] tagValues, boolean patientsOnly, boolean studiesOnly) throws Exception {
		
		try {
			log.info("Remote PAC Query, pacID:" + pac.pacID + " patientName:" + patientNameFilter + " patientID:" + patientIDFilter + " studyDate:" + studyDateFilter + " studyIDFilter:" + studyIDFilter + " patientsOnly:" + patientsOnly + " studiesOnly:" + studiesOnly);
			if (currentPACQueries.containsKey(pac.pacID))
				throw new Exception("Last query to this PAC still in progress");
			this.setCurrentRemoteQueryInformationModel(pac.pacID, true);
			SpecificCharacterSet specificCharacterSet = new SpecificCharacterSet((String[])null);
			AttributeList filter = new AttributeList();
			{
				AttributeTag t = TagFromName.PatientName; Attribute a = new PersonNameAttribute(t,specificCharacterSet);
				if (patientNameFilter != null && patientNameFilter.length() > 0) {
					a.addValue(patientNameFilter);
				}
				filter.put(t,a);
			}
			{
				AttributeTag t = TagFromName.PatientID; Attribute a = new ShortStringAttribute(t,specificCharacterSet);
				if (patientIDFilter != null && patientIDFilter.length() > 0) {
					a.addValue(patientIDFilter);
				}
				filter.put(t,a);
			}
//			{ AttributeTag t = TagFromName.PatientBirthDate; Attribute a = new DateAttribute(t); filter.put(t,a); }
//			{ AttributeTag t = TagFromName.PatientSex; Attribute a = new CodeStringAttribute(t); filter.put(t,a); }
//
//			{ AttributeTag t = TagFromName.StudyID; Attribute a = new ShortStringAttribute(t,specificCharacterSet); filter.put(t,a);}
//			{ AttributeTag t = TagFromName.StudyDescription; Attribute a = new LongStringAttribute(t,specificCharacterSet); filter.put(t,a); }
//			{ AttributeTag t = TagFromName.ModalitiesInStudy; Attribute a = new CodeStringAttribute(t); filter.put(t,a); }
			// StudyDate formats:
			//	from/to: 20071001-20080220
			//	before: -20080220
			//	after: 20071001-
			{
				AttributeTag t = TagFromName.StudyDate; Attribute a = new DateAttribute(t);
				if (studyDateFilter != null && studyDateFilter.length() > 0) {
					a.addValue(studyDateFilter);
				}
				filter.put(t,a);
			}
//			{ AttributeTag t = TagFromName.StudyTime; Attribute a = new TimeAttribute(t); filter.put(t,a); }
//			{ AttributeTag t = TagFromName.PatientAge; Attribute a = new AgeStringAttribute(t); filter.put(t,a); }
//
//			{ AttributeTag t = TagFromName.SeriesDescription; Attribute a = new LongStringAttribute(t,specificCharacterSet); filter.put(t,a); }
//			{ AttributeTag t = TagFromName.SeriesNumber; Attribute a = new IntegerStringAttribute(t); filter.put(t,a); }
//			{ AttributeTag t = TagFromName.Manufacturer; Attribute a = new LongStringAttribute(t); filter.put(t,a); }
//			{ AttributeTag t = TagFromName.Modality; Attribute a = new CodeStringAttribute(t); filter.put(t,a); }
//			{ AttributeTag t = TagFromName.SeriesDate; Attribute a = new DateAttribute(t); filter.put(t,a); }
//			{ AttributeTag t = TagFromName.SeriesTime; Attribute a = new TimeAttribute(t); filter.put(t,a); }
//
//			{ AttributeTag t = TagFromName.InstanceNumber; Attribute a = new IntegerStringAttribute(t); filter.put(t,a); }
//			{ AttributeTag t = TagFromName.ContentDate; Attribute a = new DateAttribute(t); filter.put(t,a); }
//			{ AttributeTag t = TagFromName.ContentTime; Attribute a = new TimeAttribute(t); filter.put(t,a); }
//			{ AttributeTag t = TagFromName.ImageType; Attribute a = new CodeStringAttribute(t); filter.put(t,a); }
//			{ AttributeTag t = TagFromName.NumberOfFrames; Attribute a = new IntegerStringAttribute(t); filter.put(t,a); }
//			{ AttributeTag t = TagFromName.WindowCenter; Attribute a = new DecimalStringAttribute(t); filter.put(t,a); }
//			{ AttributeTag t = TagFromName.WindowWidth; Attribute a = new DecimalStringAttribute(t); filter.put(t,a); }

			{ 
				AttributeTag t = TagFromName.StudyInstanceUID; Attribute a = new UniqueIdentifierAttribute(t); 
				if (studyIDFilter != null && studyIDFilter.length() > 0) {
					if (studyIDFilter.startsWith(pac.pacID + ":"))
						studyIDFilter = studyIDFilter.substring(studyIDFilter.indexOf(":")+1);
					a.addValue(studyIDFilter);
				}		
				filter.put(t,a); 
			}
//			{ AttributeTag t = TagFromName.SeriesInstanceUID; Attribute a = new UniqueIdentifierAttribute(t); filter.put(t,a); }
//			{ AttributeTag t = TagFromName.SOPInstanceUID; Attribute a = new UniqueIdentifierAttribute(t); filter.put(t,a); }
//			{ AttributeTag t = TagFromName.SOPClassUID; Attribute a = new UniqueIdentifierAttribute(t); filter.put(t,a); }
//			{ AttributeTag t = TagFromName.SpecificCharacterSet; Attribute a = new CodeStringAttribute(t); filter.put(t,a); a.addValue("ISO_IR 100"); }
			if (tagGroups != null && tagElements != null && tagValues != null) {
				for (int i = 0; i < tagGroups.length && i < tagElements.length && i < tagValues.length; i++)  {
					try {
						AttributeTag t = new AttributeTag(Integer.decode(tagGroups[i]),Integer.decode(tagElements[i]));
						Attribute a = new LongStringAttribute(t);
						a.addValue(tagValues[i]);
						filter.put(t,a);
					} catch (Exception x) {
						log.warning("Error decoding entered tag id" + tagGroups[i] + "," + tagElements[i], x);
					}
				}
			}
			
			if (remoteQueryCache.keySet().size() > MAX_CACHE_ENTRIES)
				clearQueryCache();
			List<RemotePACEntity> remoteEntities = new ArrayList<RemotePACEntity>();
			String key = pac.pacID;
			long startTime = System.currentTimeMillis();
			patientIds = new HashSet<String>();
			QueryTreeModel treeModel = currentRemoteQueryInformationModel.performHierarchicalQuery(filter);
			long endTime = System.currentTimeMillis();
			//log.info("Remote PAC Query took " + (endTime-startTime) + " msecs");
			QueryTreeRecord root = (QueryTreeRecord) treeModel.getRoot();
			remoteEntities = traverseTree(root, 0, remoteEntities, key, patientsOnly, studiesOnly);
			long traverseTime = System.currentTimeMillis();
			log.info("Remote PAC tree records took "+ (traverseTime-startTime) + " msecs. Number of entities returned:" + remoteEntities.size());
			if (patientsOnly && remoteEntities.size() > 0) 
				remoteEntities.remove(0); // Remove AE record
			if (studiesOnly && remoteEntities.size() > 0) 
				remoteEntities.remove(0); // Remove AE record
			if (studyIDFilter != null && studyIDFilter.length() > 0 && !studiesOnly && remoteEntities.size() > 1) 
			{
				remoteEntities.remove(0); // Remove AE record
				remoteEntities.remove(0); // Remove Study Record
			}
			if (patientIDFilter != null && patientIDFilter.trim().length() > 0)
			{
				EpadProjectOperations projectOperations = DefaultEpadProjectOperations.getInstance();
				EpadDatabaseOperations databaseOperations = EpadDatabase.getInstance().getEPADDatabaseOperations();
				for (RemotePACEntity rpe: remoteEntities) {
					if (rpe.entityType.equalsIgnoreCase("Study")) {
						if (projectOperations.getStudy(rpe.entityID.substring(rpe.entityID.indexOf(":")+1)) != null)
							rpe.inEpad = true;
					} else if (rpe.entityType.equalsIgnoreCase("Series")) {
						if (databaseOperations.hasSeriesInEPadDatabase(rpe.entityID.substring(rpe.entityID.indexOf(":")+1)))
							rpe.inEpad = true;
					}
				}
			}
			return remoteEntities;
		}
		catch (Exception e) {
			e.printStackTrace();
			throw e;
		} finally {
			currentPACQueries.remove(pac.pacID);
		}
	}
	
	/**
	 * 
	 */
	public void clearQueryCache()
	{
		remoteQueryCache = new HashMap<String, QueryTreeRecord>();
	}
	
	DecimalFormat decformat = new DecimalFormat("00000");
	Set<String> patientIds = new HashSet<String>();
	private List<RemotePACEntity> traverseTree(QueryTreeRecord node, int level, List<RemotePACEntity> entities, String key, boolean patientsOnly, boolean studiesOnly) {
		AttributeList al = node.getAllAttributesReturnedInIdentifier();
		String type = "";
		if (node.getInformationEntity() != null) 
		{
			type = node.getInformationEntity().toString();
		}
		else if (entities.size() == 0)
		{
			type = "AE"; 
		}

		String ukey = key;
		if (entities.size() > 0)
		{
			ukey = entities.get(0).entityID + ":" + node.getUniqueKey().getDelimitedStringValuesOrEmptyString(); // Is this key better???
		}
		RemotePACEntity entity = new RemotePACEntity(type, node.getValue(), level, ukey);
		entity.subjectID = Attribute.getSingleStringValueOrNull(al,TagFromName.PatientID);
		entity.subjectName = Attribute.getSingleStringValueOrNull(al,TagFromName.PatientName);
		if (patientsOnly && type.equals("Study") && patientIds.contains(entity.subjectID))
		{
			//log.info("Duplicate subject:" + entity.subjectID);
			return entities;
		}
		patientIds.add(entity.subjectID);
		remoteQueryCache.put(ukey, node);
		if (!studiesOnly || !type.equals("Series"))
			entities.add(entity);
		
		int num = entities.size();
		if (num%100 == 0)
			log.info("Remote Query, number of records, received:" + num);
		if (studiesOnly && entities.size() >= MAX_SERIES_QUERY)
			return entities;
		
		if (!studiesOnly && entities.size() >= MAX_INSTANCE_QUERY)
			return entities;
		
		int n = ((QueryTreeRecord)node).getChildCount();
		
		for (int i = 0; i < n; i++) {
			if ((!studiesOnly || !type.equals("Study")) && !type.equals("Series"))
			{
				//log.info("Remote Query, getting children:" + type);
				traverseTree((QueryTreeRecord)((QueryTreeRecord)node).getChildAt(i), level+1, entities, key + ":" + decformat.format(i), patientsOnly, studiesOnly);
			}
		}
		return entities;
	}
	
	/**
	 * Initiate transfer of a Remote PAC images to local PAC (patient/study id is found, it is added to project)
	 * @param pac
	 * @param entityID - This ID is contained in the RemotePACEntity query result consists of pacid and series uid or instance uid
	 * @param projectID
	 * @param userName
	 * @param sessionID
	 * @return
	 * @throws Exception
	 */
	public synchronized String retrieveRemoteData(RemotePAC pac, String entityID, String projectID, String userName, String sessionID) throws Exception {
		String uniqueKey = entityID;
		String root = uniqueKey;
		if (root.indexOf(":") != -1)
			root = root.substring(0, root.indexOf(":"));
		if (!uniqueKey.startsWith(pac.pacID))
			uniqueKey = pac.pacID + ":" + uniqueKey;
		QueryTreeRecord node = remoteQueryCache.get(uniqueKey);
		// If no cached pointers, query entire PAC again (or should we give an error???)
		if (node == null)
		{
			queryRemoteData(pac, "", "", "", "", false, false);
			node = remoteQueryCache.get(uniqueKey);
			if (node == null)
				throw new Exception("Remote data not found");
		}
		String type = node.getInformationEntity().toString();
		String studyUID = null;
		String studyDate = null;
		String seriesUID = null;
		String patientID = null;
		String patientName = "";
		if (!type.equalsIgnoreCase("Study"))
		{
			if (type.equalsIgnoreCase("Series"))
				seriesUID = node.getUniqueKey().getSingleStringValueOrNull();
			QueryTreeRecord parent = (QueryTreeRecord) node.getParent();
			if (parent != null)
			{
				type = parent.getInformationEntity().toString();
				if (!type.equalsIgnoreCase("Study"))
				{
					if (type.equalsIgnoreCase("Series"))
						seriesUID = parent.getUniqueKey().getSingleStringValueOrNull();
					parent = (QueryTreeRecord) parent.getParent();
					type = parent.getInformationEntity().toString();
					if (type.equalsIgnoreCase("Study"))
					{
						studyUID = parent.getUniqueKey().getSingleStringValueOrNull();
						studyDate = Attribute.getSingleStringValueOrNull(parent.getAllAttributesReturnedInIdentifier(),TagFromName.StudyDate);
						patientID = Attribute.getSingleStringValueOrNull(parent.getAllAttributesReturnedInIdentifier(),TagFromName.PatientID);
						patientName = Attribute.getSingleStringValueOrEmptyString(parent.getAllAttributesReturnedInIdentifier(),TagFromName.PatientName);
					}
				}
				else
				{
					studyUID = parent.getUniqueKey().getSingleStringValueOrNull();
					studyDate = Attribute.getSingleStringValueOrNull(parent.getAllAttributesReturnedInIdentifier(),TagFromName.StudyDate);
					patientID = Attribute.getSingleStringValueOrNull(parent.getAllAttributesReturnedInIdentifier(),TagFromName.PatientID);
					patientName = Attribute.getSingleStringValueOrEmptyString(parent.getAllAttributesReturnedInIdentifier(),TagFromName.PatientName);
				}
			}
		}
		else
		{
			studyUID = uniqueKey;
			studyDate = Attribute.getSingleStringValueOrNull(node.getAllAttributesReturnedInIdentifier(),TagFromName.StudyDate);
			patientID = Attribute.getSingleStringValueOrNull(node.getAllAttributesReturnedInIdentifier(),TagFromName.PatientID);
			patientName = Attribute.getSingleStringValueOrEmptyString(node.getAllAttributesReturnedInIdentifier(),TagFromName.PatientName);
		}
		if (studyUID != null && patientID != null && projectID != null && projectID.trim().length() > 0)
		{
			UserProjectService.addSubjectAndStudyToProject(patientID, patientName, studyUID, projectID, sessionID, userName);
		}
		if (seriesUID != null)
			pendingTransfers.put(seriesUID, userName + ":" + projectID);
		this.setCurrentRemoteQueryInformationModel(pac.pacID, false);
		if (node != null) {
			setCurrentRemoteQuerySelection(node.getUniqueKeys(), node.getUniqueKey(), node.getAllAttributesReturnedInIdentifier());
			log.info("Request retrieval of "+currentRemoteQuerySelectionLevel+" "+currentRemoteQuerySelectionUniqueKey.getSingleStringValueOrEmptyString()+" from "+pac.pacID+" ("+currentRemoteQuerySelectionRetrieveAE+")");
	   		File xfrstart = new File(EPADConfig.dcm4cheeHome + "/" + pac.aeTitle + "_XfrStarted.log");
	   		File xfrend = new File(EPADConfig.dcm4cheeHome + "/" + pac.aeTitle + "_XfrEnded.log");
			try {
				if (xfrstart.exists()) xfrstart.delete();
				if (xfrend.exists()) xfrend.delete();
			} catch (Exception x) {}
			performRetrieve(currentRemoteQuerySelectionUniqueKeys,currentRemoteQuerySelectionLevel,currentRemoteQuerySelectionRetrieveAE);			
		}
		monitorTransfers.put(pac.aeTitle, userName + ":" + pac.pacID);
		monitorStart.put(pac.aeTitle, System.currentTimeMillis());
		if (studyUID != null)
			return studyUID + ":" + studyDate;
		else
			return seriesUID;
	}
	
	/**
	 * @return
	 */
	public List<String> getPendingTransfers()
	{
		List<String> transfers = new ArrayList<String>();
		for (String id: pendingTransfers.keySet())
		{
			String transfer = id + ":" + pendingTransfers.get(id);
			transfers.add(transfer);
		}
		return transfers;
	}
	
	public void checkTransfers()
	{
		if (monitorTransfers.isEmpty()) return;
		EpadDatabaseOperations epadDatabaseOperations = EpadDatabase.getInstance().getEPADDatabaseOperations();
		Set<String> removeAet = new HashSet<String>();
		for (String aet: monitorTransfers.keySet()) 
		{
			log.info("Checking data transfer from " + aet);
       		File xfrstart = new File(EPADConfig.dcm4cheeHome + "/" + aet + "_XfrStarted.log");
       		File xfrend = new File(EPADConfig.dcm4cheeHome + "/" + aet + "_XfrEnded.log");
       		try
       		{
	       		if (xfrstart.exists())
	       		{
	       			String logStart = EPADFileUtils.readFileAsString(xfrstart);
					epadDatabaseOperations.insertEpadEvent(
							monitorTransfers.get(aet).substring(0, monitorTransfers.get(aet).indexOf(":")), 
							logStart.replace('\n', ' '), 
							aet, aet,
							aet, 
							aet,
							"",
							"",
							"Remote PAC Transfer");
					log.info("Added PAC Transfer Started Event for " + monitorTransfers.get(aet));
					xfrstart.delete();
	       		}
	       		if (xfrend.exists())
	       		{
	       			monitorStart.put(aet, 0L);
	       			String logEnd = EPADFileUtils.readFileAsString(xfrend);
					epadDatabaseOperations.insertEpadEvent(
							monitorTransfers.get(aet).substring(0, monitorTransfers.get(aet).indexOf(":")), 
							logEnd.replace('\n', ' '), 
							aet, aet,
							aet, 
							aet, 
							"", 
							"",
							"Remote PAC Transfer");
					log.info("Added PAC Transfer Ended Event for " + monitorTransfers.get(aet));
					xfrend.delete();
	       		}
       		} catch (Exception x)
       		{
       			removeAet.add(aet);
       			log.warning("Error in checking", x);
       		}
		}
		for (String aet: monitorStart.keySet())
		{
			if ((System.currentTimeMillis() - monitorStart.get(aet)) > 3600000)
			{
       			removeAet.add(aet);
			}
		}
		for (String aet: removeAet)
		{
			monitorTransfers.remove(aet);
			monitorStart.remove(aet);
		}
	}
	
	SimpleDateFormat dateformat = new SimpleDateFormat("yyyyMMdd");
	private Date getDate(String dateStr)
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
