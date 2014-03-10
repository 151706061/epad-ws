package edu.stanford.isis.epadws.processing.pipeline.watcher;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServletResponse;

import edu.stanford.isis.epad.common.util.EPADConfig;
import edu.stanford.isis.epad.common.util.EPADLogger;
import edu.stanford.isis.epadws.processing.model.DicomSeriesProcessingDescription;
import edu.stanford.isis.epadws.processing.pipeline.threads.ShutdownSignal;
import edu.stanford.isis.epadws.xnat.XNATCreationOperations;
import edu.stanford.isis.epadws.xnat.XNATSessionOperations;
import edu.stanford.isis.epadws.xnat.XNATUtil;

/**
 * Monitors the XNAT series queue. The queue contains descriptions of new DICOM series that have been uploaded to ePAD's
 * DCM4CHEE instance (and are modeled by {@link DicomSeriesProcessingDescription} objects).
 * <p>
 * This queue is populated by a {@link Dcm4CheeDatabaseWatcher}, which monitors a DCM4CHEE MySQL database for new DICOM
 * series.
 * 
 * @author martin
 */
public class XNATSeriesWatcher implements Runnable
{
	private final BlockingQueue<DicomSeriesProcessingDescription> xnatSeriesWatcherQueue;
	private final ShutdownSignal shutdownSignal = ShutdownSignal.getInstance();

	private static final EPADLogger logger = EPADLogger.getInstance();
	private static final EPADConfig config = EPADConfig.getInstance();

	private final String xnatUploadProjectID;
	private final String xnatUploadProjectUser;
	private final String xnatUploadProjectPassword;
	private String jsessionID;

	public XNATSeriesWatcher(BlockingQueue<DicomSeriesProcessingDescription> xnatSeriesWatcherQueue)
	{
		this.xnatSeriesWatcherQueue = xnatSeriesWatcherQueue;

		xnatUploadProjectID = config.getStringPropertyValue("XNATUploadProjectID");
		xnatUploadProjectUser = config.getStringPropertyValue("XNATUploadProjectUser");
		xnatUploadProjectPassword = config.getStringPropertyValue("XNATUploadProjectPassword");

		jsessionID = "";
		logger.info("Starting the XNAT series watcher");
	}

	@Override
	public void run()
	{
		while (!shutdownSignal.hasShutdown()) {
			try {
				DicomSeriesProcessingDescription dicomSeriesDescription = xnatSeriesWatcherQueue.poll(5000,
						TimeUnit.MILLISECONDS);

				if (dicomSeriesDescription != null) {

					validateDICOMSeriesProcessingDescription(dicomSeriesDescription);

					String dicomStudyIUID = dicomSeriesDescription.getStudyIUID();
					String dicomPatientID = dicomSeriesDescription.getPatientID();
					String dicomPatientName = dicomSeriesDescription.getPatientName();

					String xnatSubjectLabel = XNATUtil.dicomPatientID2XNATSubjectLabel(dicomPatientID);

					logger.info("XNAT series watcher found new DICOM study " + dicomStudyIUID + " for patient "
							+ dicomPatientName + " with ID " + dicomPatientID);

					createXNATStudy(xnatUploadProjectID, xnatSubjectLabel, dicomPatientName, dicomStudyIUID);
				}
			} catch (Exception e) {
				logger.warning("Exception in XNAT series watcher thread", e);
			}
		}
	}

	private void validateDICOMSeriesProcessingDescription(DicomSeriesProcessingDescription dicomSeriesDescription)
			throws IllegalArgumentException
	{
		if (dicomSeriesDescription == null)
			throw new IllegalArgumentException("Missing series description");
		if (dicomSeriesDescription.getSeriesUID().length() == 0)
			throw new IllegalArgumentException("Missing series IUID in series description");
		if (dicomSeriesDescription.getPatientID().length() == 0)
			throw new IllegalArgumentException("Missing  patient ID in series description");
		if (dicomSeriesDescription.getPatientName().length() == 0)
			throw new IllegalArgumentException("Missing patient name in series description");
	}

	private void createXNATStudy(String xnatProjectID, String xnatSubjectLabel, String dicomPatientName,
			String dicomStudyUID)
	{
		if (updateSessionID()) {
			XNATCreationOperations.createXNATSubjectFromDICOMPatient(xnatProjectID, xnatSubjectLabel, dicomPatientName,
					jsessionID);

			XNATCreationOperations.createXNATExperimentFromDICOMStudy(xnatProjectID, xnatSubjectLabel, dicomStudyUID,
					jsessionID);
		} else {
			logger.warning("Could not log into XNAT to upload DICOM study " + dicomStudyUID);
		}
	}

	/**
	 * 
	 * @return True if successfully updated, false otherwise
	 */
	private boolean updateSessionID()
	{
		if (!XNATSessionOperations.hasValidXNATSessionID(jsessionID)) { // Validating will extend validity
			XNATUtil.XNATSessionResponse xnatSessionResponse = XNATSessionOperations.getXNATSessionID(xnatUploadProjectUser,
					xnatUploadProjectPassword);
			if (xnatSessionResponse.statusCode != HttpServletResponse.SC_OK) {
				logger.warning("Error invoking XNAT session service for study upload; statusCode = "
						+ xnatSessionResponse.statusCode);
				jsessionID = null;
				return false;
			} else {
				jsessionID = xnatSessionResponse.response;
				return true;
			}
		} else
			return true;
	}
}
