package edu.stanford.epad.epadws.processing.pipeline.watcher;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import edu.stanford.epad.common.dicom.DicomFormatUtil;
import edu.stanford.epad.common.util.EPADConfig;
import edu.stanford.epad.common.util.EPADLogger;
import edu.stanford.epad.common.util.EPADResources;
import edu.stanford.epad.epadws.dcm4chee.Dcm4CheeDatabase;
import edu.stanford.epad.epadws.dcm4chee.Dcm4CheeDatabaseOperations;
import edu.stanford.epad.epadws.dcm4chee.Dcm4CheeDatabaseUtils;
import edu.stanford.epad.epadws.epaddb.EpadDatabase;
import edu.stanford.epad.epadws.epaddb.EpadDatabaseOperations;
import edu.stanford.epad.epadws.processing.model.DicomImageProcessingState;
import edu.stanford.epad.epadws.processing.model.DicomSeriesProcessingDescription;
import edu.stanford.epad.epadws.processing.model.DicomSeriesProcessingStatus;
import edu.stanford.epad.epadws.processing.model.DicomSeriesProcessingStatusTracker;
import edu.stanford.epad.epadws.processing.model.PngProcessingStatus;
import edu.stanford.epad.epadws.processing.pipeline.task.GeneratorTask;
import edu.stanford.epad.epadws.processing.pipeline.task.PNGGridGeneratorTask;
import edu.stanford.epad.epadws.processing.pipeline.task.PngGeneratorTask;
import edu.stanford.epad.epadws.processing.pipeline.threads.ShutdownSignal;
import edu.stanford.epad.epadws.queries.DefaultEpadOperations;
import edu.stanford.epad.epadws.queries.EpadOperations;

/**
 * Process new DICOM series appearing in the series queue. Each series is described by a
 * {@link DicomSeriesProcessingDescription}.
 * <p>
 * These descriptions are placed in the queue by a {@link Dcm4CheeDatabaseWatcher}, which picks up new series by
 * monitoring a DCM4CHEE MySQL database.
 * <p>
 * This watcher submits these to the PNG generation task queue to be processed by the {@link PngGeneratorTask}. It also
 * maintains order information for the series using the {@link DicomSeriesOrderTracker} class.
 */
public class DICOMSeriesWatcher implements Runnable
{
	private final BlockingQueue<DicomSeriesProcessingDescription> dicomSeriesWatcherQueue;
	private final BlockingQueue<GeneratorTask> pngGeneratorTaskQueue;
	private final DicomSeriesProcessingStatusTracker dicomSeriesDescriptionTracker;

	private final String dcm4cheeRootDir; // Used by the PNG grid process only.

	private final ShutdownSignal shutdownSignal = ShutdownSignal.getInstance();
	private static final EPADLogger log = EPADLogger.getInstance();

	private QueueAndWatcherManager queueAndWatcherManager;

	public DICOMSeriesWatcher(BlockingQueue<DicomSeriesProcessingDescription> dicomSeriesWatcherQueue,
			BlockingQueue<GeneratorTask> pngGeneratorTaskQueue)
	{
		log.info("Starting the DICOM series watcher");

		this.dicomSeriesWatcherQueue = dicomSeriesWatcherQueue;
		this.pngGeneratorTaskQueue = pngGeneratorTaskQueue;
		this.dicomSeriesDescriptionTracker = DicomSeriesProcessingStatusTracker.getInstance();
		this.dcm4cheeRootDir = EPADConfig.getInstance().getStringPropertyValue("dcm4cheeDirRoot");
	}

	@Override
	public void run()
	{
		EpadOperations epadQueries = DefaultEpadOperations.getInstance();

		queueAndWatcherManager = QueueAndWatcherManager.getInstance();

		while (!shutdownSignal.hasShutdown()) {
			try {
				DicomSeriesProcessingDescription dicomSeriesDescription = dicomSeriesWatcherQueue.poll(1000,
						TimeUnit.MILLISECONDS);

				if (dicomSeriesDescription != null) {
					log.info("Series watcher found new series " + dicomSeriesDescription.getSeriesUID() + " with "
							+ dicomSeriesDescription.getNumberOfInstances() + " instance(s).");
					dicomSeriesDescriptionTracker.addDicomSeriesProcessingStatus(new DicomSeriesProcessingStatus(
							dicomSeriesDescription));
				}
				// Loop through all new series and find images that have no corresponding PNG file recorded in ePAD database.
				// Update their status to reflect this so that we can monitor percent completion for each series.
				for (DicomSeriesProcessingStatus currentDicomSeriesProcessingStatus : dicomSeriesDescriptionTracker
						.getDicomSeriesProcessingStatusSet()) {
					DicomSeriesProcessingDescription currentDicomSeriesDescription = currentDicomSeriesProcessingStatus
							.getDicomSeriesProcessingDescription();
					// Each entry in list is map with keys: sop_iuid, inst_no, series_iuid, filepath, file_size.
					String seriesUID = currentDicomSeriesDescription.getSeriesUID();
					List<Map<String, String>> unprocessedDicomImageFileDescriptions = epadQueries
							.getUnprocessedDicomImageFileDescriptionsForSeries(seriesUID);

					if (unprocessedDicomImageFileDescriptions.size() > 0) {
						log.info("Found series " + currentDicomSeriesDescription.getSeriesUID() + " with "
								+ unprocessedDicomImageFileDescriptions.size() + " unprocessed DICOM image(s).");
						currentDicomSeriesDescription.updateWithDicomImageFileDescriptions(unprocessedDicomImageFileDescriptions);
						currentDicomSeriesProcessingStatus.registerActivity();
						currentDicomSeriesProcessingStatus.setState(DicomImageProcessingState.IN_PIPELINE);
						queueAndWatcherManager.addToPNGGeneratorTaskPipeline(unprocessedDicomImageFileDescriptions);
						log.info("Submitted series " + currentDicomSeriesDescription.getSeriesUID() + " to PNG generator");
					} else { // There are no unprocessed PNG files left.
						if (currentDicomSeriesProcessingStatus.getProcessingState() == DicomImageProcessingState.IN_PIPELINE) {
							// logger.info("No unprocessed PNG files left for series " +
							// currentDicomSeriesDescription.getSeriesUID());
							/*
							 * List<Map<String, String>> processedPNGImages = mySqlQueries
							 * .getProcessedDICOMImageFileDescriptionsOrdered(currentSeriesDescription.getSeriesUID());
							 * 
							 * if (processedPNGImages.size() > 0) { // Convert processed PNG files to PNG grid files
							 * logger.info("Found " + processedPNGImages.size() + " PNG images. Converting to grid images.");
							 * currentSeriesStatus.setState(DicomImageProcessingState.IN_PNG_GRID_PIPELINE);
							 * addToPNGGridGeneratorTaskPipeline(unprocessedDICOMImageFileDescriptions); }
							 */
						}
					}
				}
				// Loop through all current active series and remove them if they are done.
				for (DicomSeriesProcessingStatus currentSeriesOrderStatus : dicomSeriesDescriptionTracker
						.getDicomSeriesProcessingStatusSet()) {
					if (currentSeriesOrderStatus.isDone()) { // Remove finished series
						dicomSeriesDescriptionTracker.removeDicomSeriesProcessingStatus(currentSeriesOrderStatus);
					}
				}
			} catch (Exception e) {
				log.severe("Exception in DICOM series watcher thread", e);
			}
		}
	}

	@SuppressWarnings("unused")
	private void addToPNGGridGeneratorTaskPipeline(String seriesUID,
			List<Map<String, String>> unprocessedPNGImageDescriptions)
	{
		EpadDatabaseOperations databaseOperations = EpadDatabase.getInstance().getEPADDatabaseOperations();
		int currentImageIndex = 0;
		for (Map<String, String> currentPNGImageDescription : unprocessedPNGImageDescriptions) {
			String inputPNGFilePath = getInputFilePath(currentPNGImageDescription); // Get the input file path.
			File inputPNGFile = new File(inputPNGFilePath);
			String outputPNGGridFilePath = createOutputFilePathForDicomPNGGridImage(currentPNGImageDescription);
			if (!databaseOperations.hasEpadFileRecord(outputPNGGridFilePath)) {
				log.info("SeriesWatcher has: " + currentPNGImageDescription.get("sop_iuid") + " PNG for grid processing.");
				// Need to get slice for PNG files.
				List<File> inputPNGGridFiles = getSliceOfPNGFiles(unprocessedPNGImageDescriptions, currentImageIndex, 16);
				createPngGridFileForPNGImages(seriesUID, inputPNGFile, inputPNGGridFiles, outputPNGGridFilePath);
			}
			currentImageIndex++;
		}
	}

	private List<File> getSliceOfPNGFiles(List<Map<String, String>> imageList, int currentImageIndex, int sliceSize)
	{
		List<File> sliceOfPNGFiles = new ArrayList<File>(sliceSize);

		for (int i = currentImageIndex; i < sliceSize; i++) {
			Map<String, String> currentPNGImage = imageList.get(i);
			String pngFilePath = getInputFilePath(currentPNGImage);
			File pngFile = new File(pngFilePath);
			sliceOfPNGFiles.add(pngFile);
		}
		return sliceOfPNGFiles;
	}

	private void createPngGridFileForPNGImages(String seriesUID, File inputPNGFile, List<File> inputPNGGridFiles,
			String outputPNGGridFilePath)
	{
		// logger.info("Offering to PNGGridTaskQueue: out=" + outputPNGGridFilePath + " in=" +
		// inputPNGFile.getAbsolutePath());

		File outputPNGFile = new File(outputPNGGridFilePath);
		EpadDatabaseOperations databaseOperations = EpadDatabase.getInstance().getEPADDatabaseOperations();
		insertEpadFile(databaseOperations, outputPNGFile);

		PNGGridGeneratorTask pngGridGeneratorTask = new PNGGridGeneratorTask(seriesUID, inputPNGFile, inputPNGGridFiles,
				outputPNGFile);
		pngGeneratorTaskQueue.offer(pngGridGeneratorTask);
	}

	private void insertEpadFile(EpadDatabaseOperations epadDatabaseOperations, File outputPNGFile)
	{
		Map<String, String> epadFilesTable = Dcm4CheeDatabaseUtils.createEPadFilesTableData(outputPNGFile);
		epadFilesTable.put("file_status", "" + PngProcessingStatus.IN_PIPELINE.getCode());
		epadDatabaseOperations.insertEpadFileRecord(epadFilesTable);
	}

	String getInputFilePath(Map<String, String> currImage)
	{
		return getDcm4cheeRootDir() + currImage.get("filepath");
	}

	/**
	 * 
	 * @param currImage Map of String to String
	 * @return String
	 */
	private String createOutputFilePathForDicomPNGGridImage(Map<String, String> currImage)
	{
		String seriesIUID = currImage.get("series_iuid");
		Dcm4CheeDatabaseOperations databaseOperations = Dcm4CheeDatabase.getInstance().getDcm4CheeDatabaseOperations();
		String studyUID = databaseOperations.getStudyUIDForSeries(seriesIUID);
		String imageUID = currImage.get("sop_iuid");
		StringBuilder outputPath = new StringBuilder();

		outputPath.append(EPADResources.getEPADWebServerPNGGridDir());
		outputPath.append(DicomFormatUtil.formatUidToDir(studyUID)).append("/");
		outputPath.append(DicomFormatUtil.formatUidToDir(seriesIUID)).append("/");
		outputPath.append(DicomFormatUtil.formatUidToDir(imageUID)).append(".png");

		return outputPath.toString();
	}

	public String getDcm4cheeRootDir()
	{
		if (dcm4cheeRootDir.endsWith("/")) {
			return dcm4cheeRootDir;
		}
		return dcm4cheeRootDir + "/";
	}
}
