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
import edu.stanford.isis.epadws.processing.model.DicomSeriesOrderTracker;
import edu.stanford.isis.epadws.processing.model.DicomImageProcessingState;
import edu.stanford.isis.epadws.processing.model.PngProcessingStatus;
import edu.stanford.isis.epadws.processing.model.DicomSeriesOrder;
import edu.stanford.isis.epadws.processing.model.DicomSeriesOrderStatus;
import edu.stanford.isis.epadws.processing.persistence.Dcm4CheeDatabaseUtils;
import edu.stanford.isis.epadws.processing.persistence.MySqlInstance;
import edu.stanford.isis.epadws.processing.persistence.MySqlQueries;
import edu.stanford.isis.epadws.processing.pipeline.task.GeneratorTask;
import edu.stanford.isis.epadws.processing.pipeline.task.PngGridGeneratorTask;
import edu.stanford.isis.epadws.processing.pipeline.threads.ShutdownSignal;

/**
 * Task to process DICOM series appearing in the series queue (which was filled by {@Dcm4CheeDatabaseWatcher
 * 
 * 
 * }, which monitors the DCM4CHEE database instance).
 * <p>
 * Submits these to the PNG generation task queue (to be processed by the {@PngGridGeneratorTask}
 * ). Also maintains order information for the series using the {@SeriesOrderTracker} class.
 * 
 * @author amsnyder
 */
public class Dcm4CheeSeriesWatcher implements Runnable
{
	private final BlockingQueue<DicomSeriesOrder> dcm4CheeSeriesWatcherQueue;
	private final BlockingQueue<GeneratorTask> pngGeneratorTaskQueue;
	private final DicomSeriesOrderTracker dicomSeriesOrderTracker;

	private final String dcm4cheeRootDir; // Used by the PNG grid process only.

	private final ShutdownSignal shutdownSignal = ShutdownSignal.getInstance();
	private final EPADLogger logger = EPADLogger.getInstance();

	private final QueueAndWatcherManager queueAndWatcherManager = QueueAndWatcherManager.getInstance();

	public Dcm4CheeSeriesWatcher(BlockingQueue<DicomSeriesOrder> dcm4CheeSeriesWatcherQueue,
			BlockingQueue<GeneratorTask> pngGeneratorTaskQueue)
	{
		this.dcm4CheeSeriesWatcherQueue = dcm4CheeSeriesWatcherQueue;
		this.pngGeneratorTaskQueue = pngGeneratorTaskQueue;
		this.dicomSeriesOrderTracker = DicomSeriesOrderTracker.getInstance();
		this.dcm4cheeRootDir = EPADConfig.getInstance().getParam("dcm4cheeDirRoot");
	}

	@Override
	public void run()
	{
		MySqlQueries mySqlQueries = MySqlInstance.getInstance().getMysqlQueries();
		while (!shutdownSignal.hasShutdown()) {
			try {
				DicomSeriesOrder seriesOrder = dcm4CheeSeriesWatcherQueue.poll(5000, TimeUnit.MILLISECONDS);

				if (seriesOrder != null) { // Add new SeriesOrder.
					logger.info("Series watcher found new series with series UID" + seriesOrder.getSeriesUID());
					dicomSeriesOrderTracker.add(new DicomSeriesOrderStatus(seriesOrder));
				}
				if (dicomSeriesOrderTracker.getStatusSet().size() > 0) {
					logger.info("SeriesOrderTracker has: " + dicomSeriesOrderTracker.getStatusSet().size() + " series.");
				}
				// Loop through all new series and find images that have no corresponding PNG file recorded in ePAD database.
				for (DicomSeriesOrderStatus currentSeriesOrderStatus : dicomSeriesOrderTracker.getStatusSet()) {
					DicomSeriesOrder currentSeriesOrder = currentSeriesOrderStatus.getSeriesOrder();
					// Each entry in list is map with keys: sop_iuid, inst_no, series_iuid, filepath, file_size.
					List<Map<String, String>> unprocessedDICOMImageFileDescriptions = mySqlQueries
							.getUnprocessedDICOMImageFileDescriptions(currentSeriesOrder.getSeriesUID());

					if (unprocessedDICOMImageFileDescriptions.size() > 0) {
						logger.info("Found " + unprocessedDICOMImageFileDescriptions.size() + " unprocessed DICOM image(s).");
						// SeriesOrder tracks instance order
						currentSeriesOrder.updateImageDescriptions(unprocessedDICOMImageFileDescriptions);
						currentSeriesOrderStatus.registerActivity();
						currentSeriesOrderStatus.setState(DicomImageProcessingState.IN_PIPELINE);
						queueAndWatcherManager.addToPNGGeneratorTaskPipeline(unprocessedDICOMImageFileDescriptions);
					} else { // There are no unprocessed PNG files left.
						if (currentSeriesOrderStatus.getProcessingState() == DicomImageProcessingState.IN_PIPELINE) {
							logger.info("No unprocesses PNG files left for series with UID " + currentSeriesOrder.getSeriesUID());
							List<Map<String, String>> processedPNGImages = mySqlQueries
									.getProcessedDICOMImageFileDescriptionsOrdered(currentSeriesOrder.getSeriesUID());
							if (processedPNGImages.size() > 0) { // Convert processed PNG files to PNG grid files
								logger.info("Found " + processedPNGImages.size() + " PNG images. Converting to grid images.");
								currentSeriesOrderStatus.setState(DicomImageProcessingState.IN_PNG_GRID_PIPELINE);
								addToPNGGridGeneratorTaskPipeline(unprocessedDICOMImageFileDescriptions);
							}
						}
					}
				}
				// Loop through all current active series and remove them if they are done.
				for (DicomSeriesOrderStatus currentSeriesOrderStatus : dicomSeriesOrderTracker.getStatusSet()) {
					if (currentSeriesOrderStatus.isDone()) { // Remove finished series
						dicomSeriesOrderTracker.remove(currentSeriesOrderStatus);
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
		// NOTE: Maybe we want to get this from the 'files' directory.

		// StringBuilder sb = new StringBuilder();
		// sb.append("[TEMP] PNG Creation. ");
		// sb.append("file_size=").append(currImage.get("file_size"));
		// sb.append(", inst_no=").append(currImage.get("inst_no"));
		// sb.append(", sop_iuid=").append(currImage.get("sop_iuid"));
		// sb.append(", series_iuid=").append(currImage.get("series_iuid"));
		// sb.append(", filepath=").append(currImage.get("filepath"));
		// logger.info(sb.toString());

		String retVal = getDcm4cheeRootDir() + currImage.get("filepath");

		return retVal;
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