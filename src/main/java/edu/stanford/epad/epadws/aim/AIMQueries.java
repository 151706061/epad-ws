package edu.stanford.epad.epadws.aim;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import edu.stanford.epad.common.util.EPADConfig;
import edu.stanford.epad.common.util.EPADLogger;
import edu.stanford.hakan.aim3api.base.AimException;
import edu.stanford.hakan.aim3api.base.ImageAnnotation;
import edu.stanford.hakan.aim3api.usage.AnnotationGetter;

public class AIMQueries
{
	private static final EPADLogger log = EPADLogger.getInstance();

	private static final String aimNamespace = EPADConfig.aim3Namespace;
	private static final String eXistServerUrl = EPADConfig.eXistServerUrl;
	private static final String eXistUsername = EPADConfig.eXistUsername;
	private static final String eXistPassword = EPADConfig.eXistPassword;
	private static final String xsdFilePath = EPADConfig.xsdFilePath;
	private static final String eXistCollection = EPADConfig.eXistCollection;
	private static final String useV4 = EPADConfig.useV4;
	private static final String aim4Namespace = EPADConfig.aim4Namespace;
	private static final String eXistCollectionV4 = EPADConfig.eXistCollectionV4;

	public static List<ImageAnnotation> getAIMAnnotationsForPerson(String personName, String username)
	{
		return getAIMImageAnnotations(AIMSearchType.PERSON_NAME, personName, username);
	}

	public static List<ImageAnnotation> getAIMAnnotationsForPatient(String patientId, String username)
	{
		return getAIMImageAnnotations(AIMSearchType.PATIENT_ID, patientId, username);
	}

	public static List<ImageAnnotation> getAIMAnnotationsForSeriesUID(String seriesUID, String username)
	{
		return getAIMImageAnnotations(AIMSearchType.SERIES_UID, seriesUID, username);
	}

	public static List<ImageAnnotation> getAIMAnnotationsForAnnotationUID(String annotationUID, String username)
	{
		return getAIMImageAnnotations(AIMSearchType.ANNOTATION_UID, annotationUID, username);
	}

	public static int getNumberOfAIMAnnotationsForPatients(String sessionID, String username, Set<String> patientIDs)
	{ // Only count annotations for subjects in this project
		int totalAIMAnnotations = 0;

		for (String patientID : patientIDs)
			totalAIMAnnotations += getNumberOfAIMAnnotationsForPatient(patientID, username);

		return totalAIMAnnotations;
	}

	public static int getNumberOfAIMAnnotationsForPatient(String patientId, String username)
	{
		return getNumberOfAIMAnnotations(AIMSearchType.PATIENT_ID, patientId, username);
	}

	public static int getNumberOfAIMAnnotationsForSeries(String seriesUID, String username)
	{
		return getNumberOfAIMAnnotations(AIMSearchType.SERIES_UID, seriesUID, username);
	}

	public static int getNumberOfAIMAnnotationsForSeriesSet(Set<String> seriesUIDs, String username)
	{
		int numberOfAIMAnnotations = 0;

		for (String seriesUID : seriesUIDs)
			numberOfAIMAnnotations += getNumberOfAIMAnnotations(AIMSearchType.SERIES_UID, seriesUID, username);

		return numberOfAIMAnnotations;
	}

	public static int getNumberOfAIMAnnotationsForAnnotationUID(String annotationUID, String username)
	{
		return getNumberOfAIMAnnotations(AIMSearchType.ANNOTATION_UID, annotationUID, username);
	}

	/**
	 * Read the annotations from the AIM database by patient name, patient id, series id, annotation id, or just get all
	 * of them on a GET. Can also delete by annotation id.
	 * 
	 * @param aimSearchType One of personName, patientId, seriesUID, annotationUID, deleteUID
	 * @param value
	 * @param user
	 * @return List<ImageAnnotation>
	 * @throws edu.stanford.hakan.aim4api.base.AimException
	 */
	public static List<ImageAnnotation> getAIMImageAnnotations(AIMSearchType aimSearchType, String value, String username)
	{
		List<ImageAnnotation> resultAims = new ArrayList<ImageAnnotation>();
		List<ImageAnnotation> aims = null;
		ImageAnnotation aim = null;

		if (username.equals("")) { // TODO Temporary hack to get all annotations!
			try {
				log.info("Getting all AIM annotations");
				if (useV4.equals("false"))
					aims = AnnotationGetter.getImageAnnotationsFromServerByCagridIdEqual(eXistServerUrl, aimNamespace,
							eXistCollection, eXistUsername, eXistPassword, "0", xsdFilePath);
				else {
					List<edu.stanford.hakan.aim4api.base.ImageAnnotationCollection> iacs = edu.stanford.hakan.aim4api.usage.AnnotationGetter
							.getAllImageAnnotationCollections(eXistServerUrl, aim4Namespace, eXistCollectionV4, eXistUsername,
									eXistPassword);
					if (aims == null)
						aims = new ArrayList<ImageAnnotation>();
					for (int i = 0; i < iacs.size(); i++)
						aims.add(new ImageAnnotation(iacs.get(i)));
				}
			} catch (AimException | edu.stanford.hakan.aim4api.base.AimException e) {
				log.warning("Exception in AnnotationGetter.getImageAnnotationsFromServerByCagridIdEqual", e);
			}
			if (aims != null)
				resultAims.addAll(aims);
		} else if (aimSearchType == AIMSearchType.PERSON_NAME) {
			String personName = value;
			try {
				if (useV4.equals("false"))
					aims = AnnotationGetter.getImageAnnotationsFromServerByPersonNameEqual(eXistServerUrl, aimNamespace,
							eXistCollection, eXistUsername, eXistPassword, personName, xsdFilePath);
				else {
					List<edu.stanford.hakan.aim4api.base.ImageAnnotationCollection> iacs = edu.stanford.hakan.aim4api.usage.AnnotationGetter
							.getImageAnnotationCollectionByPersonNameEqual(eXistServerUrl, aim4Namespace, eXistCollectionV4,
									eXistUsername, eXistPassword, personName);
					if (aims == null)
						aims = new ArrayList<ImageAnnotation>();
					for (int i = 0; i < iacs.size(); i++)
						aims.add(new ImageAnnotation(iacs.get(i)));
				}
			} catch (AimException | edu.stanford.hakan.aim4api.base.AimException e) {
				log.warning("Exception in AnnotationGetter.getImageAnnotationsFromServerByPersonNameEqual " + personName, e);
			}
			if (aims != null)
				resultAims.addAll(aims);
		} else if (aimSearchType == AIMSearchType.PATIENT_ID) {
			String patientId = value;
			try {
				if (useV4.equals("false"))
					aims = AnnotationGetter.getImageAnnotationsFromServerByPersonIDAndUserNameEqual(eXistServerUrl, aimNamespace,
							eXistCollection, eXistUsername, eXistPassword, patientId, username, xsdFilePath);
				else {
					List<edu.stanford.hakan.aim4api.base.ImageAnnotationCollection> iacs = edu.stanford.hakan.aim4api.usage.AnnotationGetter
							.getImageAnnotationCollectionByUserNameAndPersonIdEqual(eXistServerUrl, aim4Namespace, eXistCollectionV4,
									eXistUsername, eXistPassword, username, patientId);
					if (aims == null)
						aims = new ArrayList<ImageAnnotation>();
					for (int i = 0; i < iacs.size(); i++)
						aims.add(new ImageAnnotation(iacs.get(i)));
				}
			} catch (AimException | edu.stanford.hakan.aim4api.base.AimException e) {
				log.warning("Exception in AnnotationGetter.getImageAnnotationsFromServerByPersonIdEqual " + patientId, e);
			}
			if (aims != null)
				resultAims.addAll(aims);
		} else if (aimSearchType == AIMSearchType.SERIES_UID) {
			String seriesUID = value;
			try {
				if (useV4.equals("false"))
					aims = AnnotationGetter.getImageAnnotationsFromServerByImageSeriesInstanceUIDEqual(eXistServerUrl,
							aimNamespace, eXistCollection, eXistUsername, eXistPassword, seriesUID, xsdFilePath);
				else {
					List<edu.stanford.hakan.aim4api.base.ImageAnnotationCollection> iacs = edu.stanford.hakan.aim4api.usage.AnnotationGetter
							.getImageAnnotationCollectionByImageSeriesInstanceUIDEqual(eXistServerUrl, aim4Namespace,
									eXistCollectionV4, eXistUsername, eXistPassword, seriesUID);
					if (aims == null)
						aims = new ArrayList<ImageAnnotation>();
					for (int i = 0; i < iacs.size(); i++)
						aims.add(new ImageAnnotation(iacs.get(i)));
				}
			} catch (AimException | edu.stanford.hakan.aim4api.base.AimException e) {
				log.warning("Exception in AnnotationGetter.getImageAnnotationsFromServerByImageSeriesInstanceUIDEqual "
						+ seriesUID, e);
			}
			if (aims != null) {
				resultAims.addAll(aims);
			}
		} else if (aimSearchType == AIMSearchType.ANNOTATION_UID) {
			String annotationUID = value;
			if (value.equals("all")) {

				// String query = "SELECT FROM " + collection + " WHERE (ImageAnnotation.cagridId like '0')";
				try {
					if (useV4.equals("false"))
						aims = AnnotationGetter.getImageAnnotationsFromServerByUserLoginNameContains(eXistServerUrl, aimNamespace,
								eXistCollection, eXistUsername, eXistPassword, username);
					else {
						List<edu.stanford.hakan.aim4api.base.ImageAnnotationCollection> iacs = edu.stanford.hakan.aim4api.usage.AnnotationGetter
								.getImageAnnotationCollectionByUserLoginNameContains(eXistServerUrl, aim4Namespace, eXistCollectionV4,
										eXistUsername, eXistPassword, username);
						if (aims == null)
							aims = new ArrayList<ImageAnnotation>();
						for (int i = 0; i < iacs.size(); i++)
							aims.add(new ImageAnnotation(iacs.get(i)));
					}

				} catch (AimException | edu.stanford.hakan.aim4api.base.AimException e) {
					log.warning("Exception in AnnotationGetter.getImageAnnotationsFromServerWithAimQuery ", e);
				}
				if (aims != null)
					resultAims.addAll(aims);
			} else {
				try {
					if (useV4.equals("false"))
						aim = AnnotationGetter.getImageAnnotationFromServerByUniqueIdentifier(eXistServerUrl, aimNamespace,
								eXistCollection, eXistUsername, eXistPassword, annotationUID, xsdFilePath);
					else {
						edu.stanford.hakan.aim4api.base.ImageAnnotationCollection iac = edu.stanford.hakan.aim4api.usage.AnnotationGetter
								.getImageAnnotationCollectionByUniqueIdentifier(eXistServerUrl, aim4Namespace, eXistCollectionV4,
										eXistUsername, eXistPassword, annotationUID);
						if (iac != null)
							aim = new ImageAnnotation(iac);
					}

				} catch (AimException | edu.stanford.hakan.aim4api.base.AimException e) {
					log.warning("Exception in AnnotationGetter.getImageAnnotationFromServerByUniqueIdentifier " + annotationUID,
							e);
				}
				if (aim != null)
					resultAims.add(aim);
			}
		} else {
			log.warning("Unknown AIM search type " + aimSearchType.getName());
		}
		return resultAims;
	}

	private static int getNumberOfAIMAnnotations(AIMSearchType valueType, String value, String username)
	{
		return 1; // TODO This call is too slow if we have a lot of data on an ePAD instance so we disable for the moment.
		// return getCountAIMImageAnnotations(valueType, value, username);
	}

	@SuppressWarnings("unused")
	private static int getCountAIMImageAnnotations(AIMSearchType aimSearchType, String value, String username)
	{
		int count = 0;

		log.info("AIM count query with search type " + aimSearchType + ", value " + value + ", username " + username);

		if (aimSearchType == AIMSearchType.PERSON_NAME) {
			String personName = value;
			try {
				count = AnnotationGetter.getCountImageAnnotationByPersonNameEqual(eXistServerUrl, aimNamespace,
						eXistCollection, eXistUsername, eXistPassword, personName);
			} catch (Exception e) {
				log.warning("Exception in AnnotationGetter.getCountImageAnnotationByPersonNameEqual " + personName, e);
			}
		} else if (aimSearchType == AIMSearchType.PATIENT_ID) {
			String patientId = value;
			try {
				count = AnnotationGetter.getCountImageAnnotationByPersonIdAndUserNameEqual(eXistServerUrl, aimNamespace,
						eXistCollection, eXistUsername, eXistPassword, patientId, username);
			} catch (Exception e) {
				log.warning("Exception in AnnotationGetter.getCountImageAnnotationByPersonIdEqual " + patientId, e);
			}
		} else if (aimSearchType == AIMSearchType.SERIES_UID) {
			String seriesUID = value;
			try {
				count = AnnotationGetter.getCountImageAnnotationByImageSeriesInstanceUidEqual(eXistServerUrl, aimNamespace,
						eXistCollection, eXistUsername, eXistPassword, seriesUID);
			} catch (Exception e) {
				log.warning("Exception in AnnotationGetter.getCountImageAnnotationByImageSeriesInstanceUIDEqual " + seriesUID,
						e);
			}
		} else if (aimSearchType == AIMSearchType.ANNOTATION_UID) {
			String annotationUID = value;
			if (value.equals("all")) {
				try {
					count = AnnotationGetter.getCountImageAnnotationByUserLoginNameContains(eXistServerUrl, aimNamespace,
							eXistCollection, eXistUsername, eXistPassword, username);
				} catch (Exception e) {
					log.warning("Exception in AnnotationGetter.getImageAnnotationsFromServerWithAimQuery ", e);
				}
			} else {
				try {
					count = AnnotationGetter.getCountImageAnnotationByUniqueIdentifierEqual(eXistServerUrl, aimNamespace,
							eXistCollection, eXistUsername, eXistPassword, annotationUID);
				} catch (Exception e) {
					log.warning("Exception in AnnotationGetter.getCountImageAnnotationByUniqueIdentifier " + annotationUID, e);
				}
			}
		} else {
			log.warning("Unknown AIM search type " + aimSearchType.getName());
			count = 0;
		}
		log.info("Number of annotations " + count);
		return count;
	}
}
