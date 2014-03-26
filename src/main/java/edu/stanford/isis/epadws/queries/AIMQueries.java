package edu.stanford.isis.epadws.queries;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import edu.stanford.hakan.aim3api.base.AimException;
import edu.stanford.hakan.aim3api.base.ImageAnnotation;
import edu.stanford.hakan.aim3api.usage.AnnotationGetter;
import edu.stanford.isis.epad.common.util.EPADConfig;
import edu.stanford.isis.epad.common.util.EPADLogger;

public class AIMQueries
{
	private static final EPADLogger logger = EPADLogger.getInstance();

	private static String namespace = EPADConfig.getInstance().getStringPropertyValue("namespace");
	private static String serverUrl = EPADConfig.getInstance().getStringPropertyValue("serverUrl");
	private static String eXistUsername = EPADConfig.getInstance().getStringPropertyValue("username");
	private static String eXistPassword = EPADConfig.getInstance().getStringPropertyValue("password");
	private static String xsdFile = EPADConfig.getInstance().getStringPropertyValue("xsdFile");
	private static String xsdFilePath = EPADConfig.getInstance().getStringPropertyValue("baseSchemaDir") + xsdFile;
	private static String collection = EPADConfig.getInstance().getStringPropertyValue("collection");


	public static List<ImageAnnotation> getAIMAnnotationsForPerson(String personName, String username)
	{
		return getAIMImageAnnotations("personName", personName, username);
	}

	public static List<ImageAnnotation> getAIMAnnotationsForPatientId(String patientId, String username)
	{
		return getAIMImageAnnotations("patientID", patientId, username);
	}

	public static List<ImageAnnotation> getAIMAnnotationsForSeriesUID(String seriesUID, String username)
	{
		return getAIMImageAnnotations("seriesUID", seriesUID, username);
	}

	public static List<ImageAnnotation> getAIMAnnotationsForAnnotationUID(String annotationUID, String username)
	{
		return getAIMImageAnnotations("annotationUID", annotationUID, username);
	}

	public static int getNumberOfAIMAnnotationsForPatientID(String sessionID, Set<String> users, String patientID)
	{
		int numberOfAIMAnnotations = 0;

		for (String user : users)
			numberOfAIMAnnotations += getNumberOfAIMAnnotationsForPatientId(patientID, user);

		return numberOfAIMAnnotations;
	}

	public static int getNumberOfAIMAnnotationsForProject(String sessionID, Set<String> usernames, String projectID)
	{
		int totalAIMAnnotations = 0;

		for (String username : usernames) {
			totalAIMAnnotations += getNumberOfAIMAnnotationsForProject(sessionID, username, projectID);
		}

		return totalAIMAnnotations;
	}

	// Only count annotations for subjects in this project
	public static int getNumberOfAIMAnnotationsForProject(String sessionID, String username, String projectID)
	{
		Set<String> subjectIDs = XNATQueries.subjectIDsForProject(sessionID, projectID);
		int totalAIMAnnotations = 0;

		for (String subjectID : subjectIDs) {
			totalAIMAnnotations += getNumberOfAIMAnnotationsForPatientId(subjectID, username);
		}

		return totalAIMAnnotations;
	}

	public static int getNumberOfAIMAnnotationsForPerson(String personName, String username)
	{
		return getNumberOfAIMAnnotations("personName", personName, username);
	}

	public static int getNumberOfAIMAnnotationsForPatientId(String patientId, String username)
	{
		return getNumberOfAIMAnnotations("patientID", patientId, username);
	}

	public static int getNumberOfAIMAnnotationsForSeriesUID(String seriesUID, String username)
	{
		return getNumberOfAIMAnnotations("seriesUID", seriesUID, username);
	}

	public static int getNumberOfAIMAnnotationsForSeriesUID(String seriesUID, Set<String> usernames)
	{
		return getNumberOfAIMAnnotations("seriesUID", seriesUID, usernames);
	}

	public static int getNumberOfAIMAnnotationsForSeriesUIDs(Set<String> seriesUIDs, Set<String> usernames)
	{
		int numberOfAIMAnnotations = 0;
		for (String seriesUID : seriesUIDs)
			numberOfAIMAnnotations += getNumberOfAIMAnnotations("seriesUID", seriesUID, usernames);
		return numberOfAIMAnnotations;
	}

	public static int getNumberOfAIMAnnotationsForAnnotationUID(String annotationUID, String username)
	{
		return getNumberOfAIMAnnotations("annotationUID", annotationUID, username);
	}

	public static int getNumberOfAIMAnnotations(String valueType, String value, String username)
	{ // TODO In AIM 4 API there is AnnotationGetter.getCountImageAnnotationCollectionByUserNameEqual method.
		return getAIMImageAnnotations(valueType, value, username).size();
	}

	public static int getNumberOfAIMAnnotations(String valueType, String value, Set<String> usernames)
	{
		int numberOfAIMAnnotations = 0;

		for (String username : usernames)
			numberOfAIMAnnotations += getAIMImageAnnotations(valueType, value, username).size();

		return numberOfAIMAnnotations;
	}

	/**
	 * Read the annotations from the AIM database by patient name, patient id, series id, annotation id, or just get all
	 * of them on a GET. Can also delete by annotation id.
	 * 
	 * @param valueType One of personName, patientId, seriesUID, annotationUID, deleteUID
	 * @param value
	 * @param user
	 * @return List<ImageAnnotation>
	 */
	public static List<ImageAnnotation> getAIMImageAnnotations(String valueType, String value, String username)
	{
		List<ImageAnnotation> retAims = new ArrayList<ImageAnnotation>();
		List<ImageAnnotation> aims = null;
		ImageAnnotation aim = null;

		if (valueType.equals("personName")) {
			String personName = value;
			try {
				aims = AnnotationGetter.getImageAnnotationsFromServerByPersonNameEqual(serverUrl, namespace, collection,
						eXistUsername, eXistPassword, personName, xsdFilePath);

			} catch (AimException e) {
				logger.warning("Exception on AnnotationGetter.getImageAnnotationsFromServerByPersonNameEqual " + personName, e);
			}
			if (aims != null) {
				retAims.addAll(aims);
			}
		} else if (valueType.equals("patientId")) {
			String patientId = value;
			try {
				/*
				 * aims = AnnotationGetter.getImageAnnotationsFromServerByPersonIdEqual(serverUrl, namespace, collection,
				 * username, password, patientId, xsdFilePath);
				 */
				aims = AnnotationGetter.getImageAnnotationsFromServerByPersonIDAndUserNameEqual(serverUrl, namespace,
						collection, eXistUsername, eXistPassword, patientId, username, xsdFilePath);
			} catch (AimException e) {
				logger.warning("Exception on AnnotationGetter.getImageAnnotationsFromServerByPersonIdEqual " + patientId, e);
			}
			if (aims != null) {
				retAims.addAll(aims);
			}
		} else if (valueType.equals("seriesUID")) {
			String seriesUID = value;
			try {
				aims = AnnotationGetter.getImageAnnotationsFromServerByImageSeriesInstanceUIDEqual(serverUrl, namespace,
						collection, eXistUsername, eXistPassword, seriesUID, xsdFilePath);
			} catch (AimException e) {
				logger.warning("Exception on AnnotationGetter.getImageAnnotationsFromServerByImageSeriesInstanceUIDEqual "
						+ seriesUID, e);
			}
			if (aims != null) {
				retAims.addAll(aims);
			}
		} else if (valueType.equals("annotationUID")) {
			String annotationUID = value;
			if (value.equals("all")) {

				// String query = "SELECT FROM " + collection + " WHERE (ImageAnnotation.cagridId like '0')";
				try {
					aims = AnnotationGetter.getImageAnnotationsFromServerByUserLoginNameContains(serverUrl, namespace,
							collection, eXistUsername, eXistPassword, username);
					/*
					 * aims = AnnotationGetter.getImageAnnotationsFromServerWithAimQuery(serverUrl, namespace, username, password,
					 * query, xsdFilePath);
					 */
				} catch (AimException e) {
					logger.warning("Exception on AnnotationGetter.getImageAnnotationsFromServerWithAimQuery ", e);
				}
				if (aims != null) {
					retAims.addAll(aims);
				}
			} else {
				try {
					aim = AnnotationGetter.getImageAnnotationFromServerByUniqueIdentifier(serverUrl, namespace, collection,
							eXistUsername, eXistPassword, annotationUID, xsdFilePath);
				} catch (AimException e) {
					logger.warning("Exception on AnnotationGetter.getImageAnnotationFromServerByUniqueIdentifier "
							+ annotationUID, e);
				}
				if (aim != null) {
					retAims.add(aim);
				}
			}
		} else if (valueType.equals("deleteUID")) {
			String annotationUID = value;
			logger.info("calling performDelete with deleteUID on GET ");
			performDelete(annotationUID, collection, serverUrl);
			retAims = null;
		} else if (valueType.equals("key")) {
			logger.info("id1 is key id2 is " + value);
		}
		return retAims;
	}

	private static String performDelete(String uid, String collection, String serverURL)
	{
		String result = "";

		logger.info("performDelete on : " + uid);
		try {
			// AnnotationGetter.deleteImageAnnotationFromServer(serverUrl, namespace, collection, xsdFilePath,username,
			// password, uid);
			AnnotationGetter.removeImageAnnotationFromServer(serverUrl, namespace, collection, eXistUsername, eXistPassword,
					uid);

			logger.info("after deletion on : " + uid);

		} catch (Exception ex) {
			result = "XML Deletion operation is Unsuccessful (Method Name; performDelete): " + ex.getLocalizedMessage();
			logger.info("XML Deletion operation is Unsuccessful (Method Name; performDelete): " + ex.getLocalizedMessage());
		}
		logger.info("AnnotationGetter.deleteImageAnnotationFromServer result: " + result);
		return result;
	}
}
