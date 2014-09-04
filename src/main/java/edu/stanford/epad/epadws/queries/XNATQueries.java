package edu.stanford.epad.epadws.queries;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.servlet.http.HttpServletResponse;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.io.IOUtils;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;

import edu.stanford.epad.common.util.EPADLogger;
import edu.stanford.epad.dtos.internal.XNATExperiment;
import edu.stanford.epad.dtos.internal.XNATExperimentList;
import edu.stanford.epad.dtos.internal.XNATProject;
import edu.stanford.epad.dtos.internal.XNATProjectList;
import edu.stanford.epad.dtos.internal.XNATSubject;
import edu.stanford.epad.dtos.internal.XNATSubjectList;
import edu.stanford.epad.dtos.internal.XNATUserList;
import edu.stanford.epad.epadws.xnat.XNATQueryUtil;
import edu.stanford.epad.epadws.xnat.XNATSessionOperations;

/**
 * Methods for querying XNAT
 * 
 * 
 * @author martin
 */
public class XNATQueries
{
	private static final EPADLogger log = EPADLogger.getInstance();

	public static XNATProjectList allProjects(String sessionID)
	{
		String allProjectsQueryURL = XNATQueryUtil.buildAllProjectsQueryURL();

		return invokeXNATProjectsQuery(sessionID, allProjectsQueryURL);
	}

	public static Set<String> allProjectIDs(String sessionID)
	{
		XNATProjectList xnatProjectList = allProjects(sessionID);
		Set<String> projectIDs = new HashSet<String>();

		for (XNATProject project : xnatProjectList.ResultSet.Result)
			projectIDs.add(project.name);
		return projectIDs;
	}

	public static Set<String> getSubjectIDsForProject(String sessionID, String projectID)
	{
		XNATSubjectList xnatSubjectList = XNATQueries.getSubjectsForProject(sessionID, projectID);
		Set<String> subjectIDs = new HashSet<String>();

		for (XNATSubject subject : xnatSubjectList.ResultSet.Result)
			subjectIDs.add(subject.label);
		return subjectIDs;
	}

	public static Set<String> getAllStudyUIDsForProject(String projectID, String sessionID)
	{
		XNATExperimentList dicomExperiments = getDICOMExperiments(sessionID, projectID);
		Set<String> result = new HashSet<String>();

		for (XNATExperiment dicomExperiment : dicomExperiments.ResultSet.Result)
			result.add(dicomExperiment.label);

		return result;
	}

	// Returns map: subjectID -> set of studyUIDs
	public static Map<String, Set<String>> getSubjectsAndStudies(String sessionID, String projectID)
	{
		Map<String, Set<String>> result = new HashMap<>();

		for (String subjectID : getSubjectIDsForProject(sessionID, projectID)) {
			Set<String> studyUIDs = getStudyUIDsForSubject(sessionID, projectID, subjectID);
			result.put(subjectID, studyUIDs);
		}
		return result;
	}

	public static Set<String> getStudyUIDsForSubject(String sessionID, String projectID, String subjectID)
	{
		Set<String> studyIDs = new HashSet<String>();
		XNATExperimentList xnatExperiments = XNATQueries.getDICOMExperiments(sessionID, projectID, subjectID);

		for (XNATExperiment xnatExperiment : xnatExperiments.ResultSet.Result) {
			String studyID = xnatExperiment.label.replace("_", ".");
			studyIDs.add(studyID);
		}
		return studyIDs;
	}
	
	public static boolean isCollaborator(String sessionID, String username, String projectID) throws Exception
	{
		String role = getUserProjectRole(sessionID, username, projectID);
		if (role == null && username.equals("admin")) return false;
		if (role == null)
			throw new Exception("Does not exist in project:" + projectID);
		if (role.toLowerCase().startsWith("collaborator"))
			return true;
		else
			return false;
	}
	
	public static boolean isMember(String sessionID, String username, String projectID) throws Exception
	{
		String role = getUserProjectRole(sessionID, username, projectID);
		if (role == null && username.equals("admin")) return true;
		if (role == null)
			throw new Exception("Does not exist in project:" + projectID);
		if (role.toLowerCase().startsWith("member"))
			return true;
		else
			return false;
	}
	
	public static boolean isOwner(String sessionID, String username, String projectID) throws Exception
	{
		String role = getUserProjectRole(sessionID, username, projectID);
		if (role == null && username.equals("admin")) return true;
		if (role == null)
			throw new Exception("Does not exist in project:" + projectID);
		if (role.toLowerCase().startsWith("owner"))
			return true;
		else
			return false;
	}
	
	public static String getUserProjectRole(String sessionID, String username, String projectID)
	{
		XNATUserList userList = getUsersForProject(projectID);
		Map<String, String> roles = userList.getRoles();
		for (String loginName: roles.keySet())
		{
			if (loginName.equals(username))
			{
				return roles.get(loginName);
			}
		}
		return null;
	}

	public static Set<String> getAllStudyUIDs()
	{
		XNATExperimentList dicomExperiments = getAllDICOMExperiments();
		Set<String> result = new HashSet<String>();

		for (XNATExperiment dicomExperiment : dicomExperiments.ResultSet.Result)
			result.add(dicomExperiment.label);

		return result;
	}

	public static XNATUserList getUsersForProject(String projectID)
	{
		String allUsersForProjectQueryURL = XNATQueryUtil.buildAllUsersForProjectQueryURL(projectID);
		String adminSessionID = XNATSessionOperations.getXNATAdminSessionID();
		log.info("Users query:" + allUsersForProjectQueryURL);
		return invokeXNATUsersQuery(adminSessionID, allUsersForProjectQueryURL);
	}

	public static Set<String> getPatientNamesForProject(String sessionID, String projectID)
	{
		XNATSubjectList xnatSubjectList = XNATQueries.getSubjectsForProject(sessionID, projectID);
		Set<String> subjectIDs = new HashSet<String>();

		for (XNATSubject subject : xnatSubjectList.ResultSet.Result) {
			subjectIDs.add(subject.src);
		}
		return subjectIDs;
	}

	public static XNATSubjectList getSubjectsForProject(String sessionID, String projectID)
	{
		String allSubjectsForProjectQueryURL = XNATQueryUtil.buildAllSubjectsForProjectQueryURL(projectID);

		return invokeXNATSubjectsQuery(sessionID, allSubjectsForProjectQueryURL);
	}

	public static int getNumberOfDICOMExperiments(String sessionID, String projectID)
	{
		String xnatExperimentsQueryURL = XNATQueryUtil.buildDICOMExperimentsForProjectQueryURL(projectID);

		// TODO Need a count without getting all records.
		return invokeXNATDICOMExperimentsQuery(sessionID, xnatExperimentsQueryURL).ResultSet.totalRecords;
	}

	// TODO Fix this so that it makes a direct query with the subjectUID to XNAT
	public static XNATExperiment getDICOMExperiment(String sessionID, String projectID, String subjectID, String studyUID)
	{
		XNATExperimentList xnatExperimentList = getDICOMExperiments(sessionID, projectID, subjectID);

		for (XNATExperiment xnatExperiment : xnatExperimentList.ResultSet.Result)
			if (xnatExperiment.label.equals(studyUID))
				return xnatExperiment;

		return null;
	}

	private static XNATExperimentList getDICOMExperiments(String sessionID, String projectID)
	{
		String xnatExperimentsQueryURL = XNATQueryUtil.buildDICOMExperimentsForProjectQueryURL(projectID);

		return invokeXNATDICOMExperimentsQuery(sessionID, xnatExperimentsQueryURL);
	}

	private static XNATExperimentList getAllDICOMExperiments()
	{
		String xnatExperimentsQueryURL = XNATQueryUtil.buildDICOMExperimentsQueryURL();
		String adminSessionID = XNATSessionOperations.getXNATAdminSessionID();

		return invokeXNATDICOMExperimentsQuery(adminSessionID, xnatExperimentsQueryURL);
	}

	private static XNATExperimentList getDICOMExperiments(String sessionID, String projectID, String subjectID)
	{
		String xnatExperimentsQueryURL = XNATQueryUtil.buildDICOMExperimentsForProjectAndSubjectQueryURL(projectID,
				subjectID);

		return invokeXNATDICOMExperimentsQuery(sessionID, xnatExperimentsQueryURL);
	}

	private static XNATProjectList invokeXNATProjectsQuery(String sessionID, String xnatProjectsQueryURL)
	{
		HttpClient client = new HttpClient();
		GetMethod method = new GetMethod(xnatProjectsQueryURL);
		int xnatStatusCode;

		method.setRequestHeader("Cookie", "JSESSIONID=" + sessionID);

		try {
			xnatStatusCode = client.executeMethod(method);
		} catch (IOException e) {
			log.warning("Error performing XNAT projects query " + xnatProjectsQueryURL, e);
			xnatStatusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		}
		return processXNATProjectsQueryResponse(method, xnatStatusCode); // Will release connection
	}

	private static XNATUserList invokeXNATUsersQuery(String sessionID, String xnatUsersQueryURL)
	{
		HttpClient client = new HttpClient();
		GetMethod method = new GetMethod(xnatUsersQueryURL);
		int xnatStatusCode;

		method.setRequestHeader("Cookie", "JSESSIONID=" + sessionID);

		try {
			xnatStatusCode = client.executeMethod(method);
		} catch (IOException e) {
			log.warning("Error performing XNAT projects query " + xnatUsersQueryURL, e);
			xnatStatusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		}
		return processXNATUsersQueryResponse(method, xnatStatusCode); // Will release connection
	}

	private static XNATUserList processXNATUsersQueryResponse(GetMethod method, int xnatStatusCode)
	{
		try {
			if (xnatStatusCode == HttpServletResponse.SC_OK) {
				return extractXNATUsersFromResponse(method);
			} else if (xnatStatusCode == HttpServletResponse.SC_UNAUTHORIZED) {
				log.warning("Invalid session token for XNAT projects query");
				return XNATUserList.emptyUsers();
			} else {
				log.warning("Error performing XNAT projects query; XNAT status code = " + xnatStatusCode);
				return XNATUserList.emptyUsers();
			}
		} finally {
			method.releaseConnection();
		}
	}

	private static XNATProjectList processXNATProjectsQueryResponse(GetMethod method, int xnatStatusCode)
	{
		try {
			if (xnatStatusCode == HttpServletResponse.SC_OK) {
				return extractXNATProjectsFromResponse(method);
			} else if (xnatStatusCode == HttpServletResponse.SC_UNAUTHORIZED) {
				log.warning("Invalid session token for XNAT projects query");
				return XNATProjectList.emptyProjects();
			} else {
				log.warning("Error performing XNAT projects query; XNAT status code = " + xnatStatusCode);
				return XNATProjectList.emptyProjects();
			}
		} finally {
			method.releaseConnection();
		}
	}

	private static XNATUserList extractXNATUsersFromResponse(GetMethod method)
	{
		InputStreamReader isr = null;
		BufferedReader br = null;

		try {
			Gson gson = new Gson();
			isr = new InputStreamReader(method.getResponseBodyAsStream(), "UTF-8");
			br = new BufferedReader(isr);
			return gson.fromJson(br, XNATUserList.class);
		} catch (IOException e) {
			log.warning("Error processing XNAT users query result", e);
			return XNATUserList.emptyUsers();
		} catch (JsonSyntaxException e) {
			log.warning("Error processing XNAT users query result", e);
			return XNATUserList.emptyUsers();
		} finally {
			IOUtils.closeQuietly(isr);
			IOUtils.closeQuietly(br);
			method.releaseConnection();
		}
	}

	private static XNATProjectList extractXNATProjectsFromResponse(GetMethod method)
	{
		InputStreamReader isr = null;
		BufferedReader br = null;

		try {
			Gson gson = new Gson();
			isr = new InputStreamReader(method.getResponseBodyAsStream(), "UTF-8");
			br = new BufferedReader(isr);
			return gson.fromJson(br, XNATProjectList.class);
		} catch (IOException e) {
			log.warning("Error processing XNAT projects query result", e);
			return XNATProjectList.emptyProjects();
		} catch (JsonSyntaxException e) {
			log.warning("Error processing XNAT projects query result", e);
			return XNATProjectList.emptyProjects();
		} finally {
			IOUtils.closeQuietly(br);
			IOUtils.closeQuietly(isr);
		}
	}

	private static XNATSubjectList invokeXNATSubjectsQuery(String sessionID, String xnatSubjectsQueryURL)
	{
		HttpClient client = new HttpClient();
		GetMethod method = new GetMethod(xnatSubjectsQueryURL);
		int xnatStatusCode;

		method.setRequestHeader("Cookie", "JSESSIONID=" + sessionID);

		try {
			xnatStatusCode = client.executeMethod(method);
		} catch (IOException e) {
			log.warning("Warning: error performing XNAT subject query " + xnatSubjectsQueryURL, e);
			xnatStatusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		}
		return processXNATSubjectQueryResponse(method, xnatStatusCode); // Will release connection
	}

	private static XNATSubjectList processXNATSubjectQueryResponse(GetMethod method, int xnatStatusCode)
	{
		try {
			if (xnatStatusCode == HttpServletResponse.SC_OK) {
				return extractXNATSubjectsFromResponse(method);
			} else if (xnatStatusCode == HttpServletResponse.SC_UNAUTHORIZED) {
				log.warning("Invalid session token for XNAT subjects query");
				return XNATSubjectList.emptySubjects();
			} else {
				log.warning("Error performing XNAT subjects query; XNAT status code = " + xnatStatusCode);
				return XNATSubjectList.emptySubjects();
			}
		} finally {
			method.releaseConnection();
		}
	}

	private static XNATSubjectList extractXNATSubjectsFromResponse(GetMethod method)
	{
		InputStreamReader isr = null;
		BufferedReader br = null;

		try {
			Gson gson = new Gson();
			isr = new InputStreamReader(method.getResponseBodyAsStream(), "UTF-8");
			br = new BufferedReader(isr);
			return gson.fromJson(br, XNATSubjectList.class);
		} catch (IOException e) {
			log.warning("Error processing XNAT subjects query result", e);
			return XNATSubjectList.emptySubjects();
		} catch (JsonSyntaxException e) {
			log.warning("Error processing XNAT subjects query result", e);
			return XNATSubjectList.emptySubjects();
		} finally {
			IOUtils.closeQuietly(br);
			IOUtils.closeQuietly(isr);
		}
	}

	private static XNATExperimentList invokeXNATDICOMExperimentsQuery(String sessionID,
			String xnatDICOMExperimentsQueryURL)
	{
		HttpClient client = new HttpClient();
		GetMethod method = new GetMethod(xnatDICOMExperimentsQueryURL);
		int xnatStatusCode;

		method.setRequestHeader("Cookie", "JSESSIONID=" + sessionID);

		try {
			xnatStatusCode = client.executeMethod(method);
		} catch (IOException e) {
			log.warning("Error performing XNAT experiment query " + xnatDICOMExperimentsQueryURL, e);
			xnatStatusCode = HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
		}
		return processXNATExperimentsQueryResponse(method, xnatStatusCode); // Will release connection
	}

	private static XNATExperimentList processXNATExperimentsQueryResponse(GetMethod method, int xnatStatusCode)
	{
		try {
			if (xnatStatusCode == HttpServletResponse.SC_OK) {
				return extractXNATExperimentsFromResponse(method);
			} else if (xnatStatusCode == HttpServletResponse.SC_UNAUTHORIZED) {
				log.warning("Invalid session token for XNAT experiments query");
				return XNATExperimentList.emptyExperiments();
			} else {
				log.warning("Error performing XNAT experiments query; XNAT status code = " + xnatStatusCode);
				return XNATExperimentList.emptyExperiments();
			}
		} finally {
			method.releaseConnection();
		}
	}

	private static XNATExperimentList extractXNATExperimentsFromResponse(GetMethod method)
	{
		InputStreamReader isr = null;
		BufferedReader br = null;

		try {
			Gson gson = new Gson();
			isr = new InputStreamReader(method.getResponseBodyAsStream(), "UTF-8");
			br = new BufferedReader(isr);
			return gson.fromJson(br, XNATExperimentList.class);
		} catch (IOException e) {
			log.warning("Error processing XNAT experiments query result", e);
			return XNATExperimentList.emptyExperiments();
		} catch (JsonSyntaxException e) {
			log.warning("Error processing XNAT experiments query result", e);
			return XNATExperimentList.emptyExperiments();
		} finally {
			IOUtils.closeQuietly(br);
			IOUtils.closeQuietly(isr);
		}
	}
}
