package edu.stanford.isis.epadws.processing.pipeline.watcher;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import edu.stanford.isis.epad.common.dicom.DicomFormatUtil;
import edu.stanford.isis.epad.common.util.EPADConfig;
import edu.stanford.isis.epad.common.util.EPADLogger;
import edu.stanford.isis.epad.common.util.ResourceUtils;
import edu.stanford.isis.epadws.processing.model.DicomImageProcessingState;
import edu.stanford.isis.epadws.processing.model.DicomSeriesDescription;
import edu.stanford.isis.epadws.processing.model.DicomSeriesDescriptionTracker;
import edu.stanford.isis.epadws.processing.model.DicomSeriesStatus;
import edu.stanford.isis.epadws.processing.model.PngProcessingStatus;
import edu.stanford.isis.epadws.processing.persistence.Dcm4CheeDatabaseUtils;
import edu.stanford.isis.epadws.processing.persistence.MySqlInstance;
import edu.stanford.isis.epadws.processing.persistence.MySqlQueries;
import edu.stanford.isis.epadws.processing.pipeline.task.GeneratorTask;
import edu.stanford.isis.epadws.processing.pipeline.task.PngGridGeneratorTask;
import edu.stanford.isis.epadws.processing.pipeline.threads.ShutdownSignal;

/**
 * Process DICOM series appearing in the series queue (which was filled by a {@Dcm4CheeDatabaseWatcher
 * 
 * 
 * 
 * 
 * }), which picks up new series by monitoring a DCM4CHEE database instance).
 * <p>
 * Submits these to the PNG generation task queue (to be processed by the {@PngGridGeneratorTask}
 * ). Also maintains order information for the series using the {@SeriesOrderTracker} class.
 */
public class DICOMSeriesWatcher implements Runnable
{
	private final BlockingQueue<DicomSeriesDescription> dicomSeriesWatcherQueue;
	private final BlockingQueue<GeneratorTask> pngGeneratorTaskQueue;
	private final DicomSeriesDescriptionTracker dicomSeriesDescriptionTracker;

	private final String dcm4cheeRootDir; // Used by the PNG grid process only.

	private final ShutdownSignal shutdownSignal = ShutdownSignal.getInstance();
	private final EPADLogger logger = EPADLogger.getInstance();

	private final QueueAndWatcherManager queueAndWatcherManager = QueueAndWatcherManager.getInstance();

	public DICOMSeriesWatcher(BlockingQueue<DicomSeriesDescription> dicomSeriesWatcherQueue,
			BlockingQueue<GeneratorTask> pngGeneratorTaskQueue)
	{
		this.dicomSeriesWatcherQueue = dicomSeriesWatcherQueue;
		this.pngGeneratorTaskQueue = pngGeneratorTaskQueue;
		this.dicomSeriesDescriptionTracker = DicomSeriesDescriptionTracker.getInstance();
		this.dcm4cheeRootDir = EPADConfig.getInstance().getParam("dcm4cheeDirRoot");
	}

	@Override
	public void run()
	{
		MySqlQueries mySqlQueries = MySqlInstance.getInstance().getMysqlQueries();
		while (!shutdownSignal.hasShutdown()) {
			try {
				DicomSeriesDescription dicomSeriesDescription = dicomSeriesWatcherQueue.poll(5000, TimeUnit.MILLISECONDS);

				if (dicomSeriesDescription != null) {
					logger.info("Series watcher found new series with series UID" + dicomSeriesDescription.getSeriesUID());
					dicomSeriesDescriptionTracker.add(new DicomSeriesStatus(dicomSeriesDescription));
				}
				if (dicomSeriesDescriptionTracker.getStatusSet().size() > 0) {
					logger.info("SeriesOrderTracker has: " + dicomSeriesDescriptionTracker.getStatusSet().size() + " series.");
				}
				// Loop through all new series and find images that have no corresponding PNG file recorded in ePAD database.
				for (DicomSeriesStatus currentSeriesStatus : dicomSeriesDescriptionTracker.getStatusSet()) {
					DicomSeriesDescription currentSeriesDescription = currentSeriesStatus.getSeriesDescription();
					// Each entry in list is map with keys: sop_iuid, inst_no, series_iuid, filepath, file_size.
					List<Map<String, String>> unprocessedDICOMImageFileDescriptions = mySqlQueries
							.getUnprocessedDICOMImageFileDescriptions(currentSeriesDescription.getSeriesUID());

					if (unprocessedDICOMImageFileDescriptions.size() > 0) {
						logger.info("Found " + unprocessedDICOMImageFileDescriptions.size() + " unprocessed DICOM image(s).");
						// SeriesOrder tracks instance order
						currentSeriesDescription.updateImageDescriptions(unprocessedDICOMImageFileDescriptions);
						currentSeriesStatus.registerActivity();
						currentSeriesStatus.setState(DicomImageProcessingState.IN_PIPELINE);
						queueAndWatcherManager.addToPNGGeneratorTaskPipeline(unprocessedDICOMImageFileDescriptions);
					} else { // There are no unprocessed PNG files left.
						if (currentSeriesStatus.getProcessingState() == DicomImageProcessingState.IN_PIPELINE) {
							logger.info("No unprocesses PNG files left for series with UID "
									+ currentSeriesDescription.getSeriesUID());
							List<Map<String, String>> processedPNGImages = mySqlQueries
									.getProcessedDICOMImageFileDescriptionsOrdered(currentSeriesDescription.getSeriesUID());
							if (processedPNGImages.size() > 0) { // Convert processed PNG files to PNG grid files
								logger.info("Found " + processedPNGImages.size() + " PNG images. Converting to grid images.");
								currentSeriesStatus.setState(DicomImageProcessingState.IN_PNG_GRID_PIPELINE);
								addToPNGGridGeneratorTaskPipeline(unprocessedDICOMImageFileDescriptions);
							}
						}
					}
				}
				// Loop through all current active series and remove them if they are done.
				for (DicomSeriesStatus currentSeriesOrderStatus : dicomSeriesDescriptionTracker.getStatusSet()) {
					if (currentSeriesOrderStatus.isDone()) { // Remove finished series
						dicomSeriesDescriptionTracker.remove(currentSeriesOrderStatus);
					}
				}
			} catch (Exception e) {
				logger.warning("Exception SeriesWatcher thread.", e);
			}
		}
	}

	private void addToPNGGridGeneratorTaskPipeline(List<Map<String, String>> unprocessedPNGImageDescriptions)
	{
		MySqlQueries queries = MySqlInstance.getInstance().getMysqlQueries();
		int currentImageIndex = 0;
		for (Map<String, String> currentPNGImage : unprocessedPNGImageDescriptions) {
			String inputPNGFilePath = getInputFilePath(currentPNGImage); // Get the input file path.
			File inputPNGFile = new File(inputPNGFilePath);
			String outputPNGGridFilePath = createOutputFilePathForDicomPNGGridImage(currentPNGImage);
			logger.info("Checking epad_files table for: " + outputPNGGridFilePath);
			if (!queries.hasEpadFile(outputPNGGridFilePath)) {
				logger.info("SeriesWatcher has: " + currentPNGImage.get("sop_iuid") + " PNG for grid processing.");
				// Need to get slice for PNG files.
				List<File> inputPNGGridFiles = getSliceOfPNGFiles(unprocessedPNGImageDescriptions, currentImageIndex, 16);
				createPngGridFileForPNGImages(inputPNGFile, inputPNGGridFiles, outputPNGGridFilePath);
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

	private void createPngGridFileForPNGImages(File inputPNGFile, List<File> inputPNGGridFiles,
			String outputPNGGridFilePath)
	{
		logger.info("Offering to PNGGridTaskQueue: out=" + outputPNGGridFilePath + " in=" + inputPNGFile.getAbsolutePath());

		File outputPNGFile = new File(outputPNGGridFilePath);
		MySqlQueries queries = MySqlInstance.getInstance().getMysqlQueries();
		insertEpadFile(queries, outputPNGFile);

		PngGridGeneratorTask pngGridGeneratorTask = new PngGridGeneratorTask(inputPNGFile, inputPNGGridFiles, outputPNGFile);
		pngGeneratorTaskQueue.offer(pngGridGeneratorTask);
	}

	private void insertEpadFile(MySqlQueries queries, File outputPNGFile)
	{
		Map<String, String> epadFilesTable = Dcm4CheeDatabaseUtils.createEPadFilesTableData(outputPNGFile);
		epadFilesTable.put("file_status", "" + PngProcessingStatus.IN_PIPELINE.getCode());
		queries.insertEpadFile(epadFilesTable);
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
		MySqlQueries queries = MySqlInstance.getInstance().getMysqlQueries();
		String studyUID = queries.getStudyUIDForSeries(seriesIUID);
		String imageUID = currImage.get("sop_iuid");
		StringBuilder outputPath = new StringBuilder();

		outputPath.append(ResourceUtils.getEPADWebServerPNGGridDir());
		outputPath.append(DicomFormatUtil.formatUidToDir(studyUID)).append("/");
		outputPath.append(DicomFormatUtil.formatUidToDir(seriesIUID)).append("/");
		outputPath.append(DicomFormatUtil.formatUidToDir(imageUID)).append(".png");

		return outputPath.toString();
	}

	/**
	 * Add a forward slash if it is missing.
	 * 
	 * @return String
	 */
	public String getDcm4cheeRootDir()
	{
		if (dcm4cheeRootDir.endsWith("/")) {
			return dcm4cheeRootDir;
		}
		return dcm4cheeRootDir + "/";
	}
}
