package edu.stanford.isis.epadws.queries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.epad.dtos.DCM4CHEESeries;
import edu.stanford.epad.dtos.DCM4CHEESeriesList;
import edu.stanford.epad.dtos.EPADDatabaseImage;
import edu.stanford.epad.dtos.EPADDatabaseSeries;
import edu.stanford.isis.epad.common.dicom.DicomFormatUtil;
import edu.stanford.isis.epad.common.util.EPADLogger;
import edu.stanford.isis.epadws.dcm4chee.Dcm4CheeDatabase;
import edu.stanford.isis.epadws.dcm4chee.Dcm4CheeDatabaseOperations;
import edu.stanford.isis.epadws.epaddb.EpadDatabase;
import edu.stanford.isis.epadws.epaddb.EpadDatabaseOperations;
import edu.stanford.isis.epadws.processing.pipeline.watcher.Dcm4CheeDatabaseWatcher;

public class DefaultEpadQueries implements EpadQueries
{
	private static final EPADLogger log = EPADLogger.getInstance();

	private static final DefaultEpadQueries ourInstance = new DefaultEpadQueries();

	private DefaultEpadQueries()
	{
	}

	public static DefaultEpadQueries getInstance()
	{
		return ourInstance;
	}

	@Override
	public Set<String> examTypesForSubject(String sessionID, String projectID, String subjectID)
	{
		Set<String> studyUIDs = XNATQueries.dicomStudyUIDsForSubject(sessionID, projectID, subjectID);
		Set<String> examTypes = new HashSet<String>();

		for (String studyUID : studyUIDs) {
			DCM4CHEESeriesList dcm4CheeSeriesList = Dcm4CheeQueries.getSeriesInStudy(studyUID);
			for (DCM4CHEESeries dcm4CheeSeries : dcm4CheeSeriesList.ResultSet.Result) {
				examTypes.add(dcm4CheeSeries.examType);
			}
		}
		return examTypes;
	}

	@Override
	public Set<String> dicomSeriesIDsForSubject(String sessionID, String projectID, String subjectID)
	{
		Dcm4CheeDatabaseOperations dcm4CheeDatabaseOperations = Dcm4CheeDatabase.getInstance()
				.getDcm4CheeDatabaseOperations();
		Set<String> seriesIDs = new HashSet<String>();
		Set<String> studyIDs = XNATQueries.dicomStudyUIDsForSubject(sessionID, projectID, subjectID);

		for (String studyID : studyIDs) {
			Set<String> seriesIDsForStudy = dcm4CheeDatabaseOperations.findAllSeriesUIDsInStudy(studyID);
			seriesIDs.addAll(seriesIDsForStudy);
		}
		return seriesIDs;
	}

	/**
	 * Called by {@link Dcm4CheeDatabaseWatcher} to see if new series have been uploaded to DCM4CHEE that ePAD does not
	 * know about.
	 */
	@Override
	public List<DCM4CHEESeries> getNewDcm4CheeSeriesWithStatus(int statusCode)
	{
		Dcm4CheeDatabaseOperations dcm4CheeDatabaseOperations = Dcm4CheeDatabase.getInstance()
				.getDcm4CheeDatabaseOperations();
		EpadDatabaseOperations epadDatabaseOperations = EpadDatabase.getInstance().getEPADDatabaseOperations();
		List<DCM4CHEESeries> dcm4CheeSeriesList = new ArrayList<DCM4CHEESeries>();

		Set<String> dcm4CheeSeriesUIDs = dcm4CheeDatabaseOperations.getNewDcm4CheeSeriesUIDs();
		Set<String> epadSeriesUIDs = epadDatabaseOperations.getAllSeriesUIDsFromEPadDatabase();
		dcm4CheeSeriesUIDs.removeAll(epadSeriesUIDs);

		// logger.info("There " + pacsSet.size() + " studies in DCM4CHEE database and " + epadSet.size()
		// + " in the ePAD database");

		List<String> seriesUIDList = new ArrayList<String>(dcm4CheeSeriesUIDs);

		for (String seriesUID : seriesUIDList) {
			DCM4CHEESeries dcm4CheeSeries = Dcm4CheeQueries.getSeriesWithUID(seriesUID);
			if (dcm4CheeSeries != null) {
				dcm4CheeSeriesList.add(dcm4CheeSeries);
			}
		}
		return dcm4CheeSeriesList;
	}

	@Override
	public EPADDatabaseSeries peformEPADSeriesQuery(String seriesIUID)
	{
		Dcm4CheeDatabaseOperations dcm4CheeDatabaseOperations = Dcm4CheeDatabase.getInstance()
				.getDcm4CheeDatabaseOperations();
		List<Map<String, String>> orderQueryEntries = dcm4CheeDatabaseOperations.getDicomSeriesOrder(seriesIUID);
		List<EPADDatabaseImage> epadImageList = new ArrayList<EPADDatabaseImage>();

		for (Map<String, String> entry : orderQueryEntries) {
			String imageUID = entry.get("sop_iuid");
			String fileName = createFileNameField(imageUID);
			String instanceNumberString = entry.get("inst_no");
			int instanceNumber = getInstanceNumber(instanceNumberString, seriesIUID, imageUID);
			String sliceLocation = createSliceLocation(entry); // entry.get("inst_custom1");
			String contentTime = "null"; // TODO Can we find this somewhere?

			EPADDatabaseImage epadImage = new EPADDatabaseImage(fileName, instanceNumber, sliceLocation, contentTime);
			epadImageList.add(epadImage);
		}
		EPADDatabaseSeries epadSeries = new EPADDatabaseSeries(epadImageList);
		return epadSeries;
	}

	@Override
	public List<Map<String, String>> getUnprocessedDicomImageFileDescriptionsForSeries(String seriesIUID)
	{
		Dcm4CheeDatabaseOperations dcm4CheeDatabaseOperations = Dcm4CheeDatabase.getInstance()
				.getDcm4CheeDatabaseOperations();
		EpadDatabaseOperations epadDatabaseOperations = EpadDatabase.getInstance().getEPADDatabaseOperations();

		List<Map<String, String>> dicomFilesWithoutPNGImagesFileDescriptions = new ArrayList<Map<String, String>>();

		try {
			// Get list of DICOM image descriptions from DCM4CHEE database table (pacsdb.files). Each image description is a
			// map with keys: i.sop_iuid, i.inst_no, s.series_iuid, f.filepath, f.file_size.
			List<Map<String, String>> dicomImageFileDescriptions = dcm4CheeDatabaseOperations
					.getDICOMImageFileDescriptionsForSeries(seriesIUID);

			// Get list of instance IDs for images in series from ePAD database table (epaddb.epad_files).
			List<String> finishedDICOMImageInstanceIDs = epadDatabaseOperations
					.getFinishedDICOMImageInstanceIDsForSeriesFromEPadDatabase(seriesIUID);

			// logger.info("Found " + dicomImageFileDescriptions.size() + " unprocessed DICOM image(s) with files and "
			// + finishedDICOMImageInstanceIDs.size() + " processed image(s) for series " + shortenSting(seriesIUID));

			for (Map<String, String> dicomImageFileDescription : dicomImageFileDescriptions) {
				String sopIdWithFile = dicomImageFileDescription.get("sop_iuid");

				if (!finishedDICOMImageInstanceIDs.contains(sopIdWithFile)) {
					dicomFilesWithoutPNGImagesFileDescriptions.add(dicomImageFileDescription);
				}
			}
		} catch (Exception e) {
			log.warning("getUnprocessedDICOMImageFileDescriptions had " + e.getMessage(), e);
		}
		return dicomFilesWithoutPNGImagesFileDescriptions;
	}

	private String createFileNameField(String sopInstanceUID)
	{
		return DicomFormatUtil.formatUidToDir(sopInstanceUID) + ".dcm";
	}

	private int getInstanceNumber(String instanceNumberString, String seriesIUID, String imageUID)
	{
		if (instanceNumberString != null)
			try {
				return Integer.parseInt(instanceNumberString);
			} catch (NumberFormatException e) {
				log.warning("Invalid instance number " + instanceNumberString + " in image " + imageUID + " in series "
						+ seriesIUID);
				return 1; // Invalid instance number; default to 1
			}
		else
			return 1; // Missing instance number; default to 1.
	}

	private String createSliceLocation(Map<String, String> entry)
	{
		String sliceLoc = entry.get("inst_custom1");
		if (sliceLoc == null)
			return "0.0";
		else
			return sliceLoc;
	}
}
