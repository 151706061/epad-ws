package edu.stanford.isis.epadws.resources.server;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.restlet.data.Status;

import edu.stanford.isis.epad.common.FileKey;
import edu.stanford.isis.epad.common.dicom.DicomFormatUtil;
import edu.stanford.isis.epadws.resources.WindowLevelResource;
import edu.stanford.isis.epadws.server.managers.leveling.WindowLevelFactory;

/**
 * To test:
 * 
 * <pre>
 * curl -X GET "http://localhost:8080/level?cmd=query&seriesuid=23&studyuid=32"
 * curl -X POST "http://localhost:8080/level?cmd=create"
 * </pre>
 */
public class WindowLevelServerResource extends BaseServerResource implements WindowLevelResource
{
	private static final String SUCCESS_MESSAGE = "JPEGs levelled!";
	private static final String MALFORMED_REQUEST_MESSAGE = "Malformed request - command should be QUERY or CREATE!";
	private static final String UNKNOWN_GET_COMMAND_MESSAGE = "Bad request - unknown GET level command: ";
	private static final String UNKNOWN_POST_COMMAND_MESSAGE = "Bad request - unknown POST level command: ";
	private static final String EMPTY_COMMAND_MESSAGE = "Bad request - no command";
	private static final String CREATE_ERROR_MESSAGE = "Level create internal error: ";
	private static final String QUERY_ERROR_MESSAGE = "Level query internal error: ";

	// Caches which series belongs to which study in case the request doesn't have that info.
	private static final Map<String, String> seriesToStudyMap = new HashMap<String, String>();

	public WindowLevelServerResource()
	{
		setNegotiated(false); // Disable content negotiation
	}

	@Override
	protected void doCatch(Throwable throwable)
	{
		log.debug("An exception was thrown in the window level resource.\n");
	}

	@Override
	public String query()
	{
		if (getQueryValue(COMMAND_NAME) == null) {
			setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
			return MALFORMED_REQUEST_MESSAGE;
		} else {
			String commandName = getQueryValue(COMMAND_NAME);

			if (WindowLevelCommand.QUERY.hasCommandName(commandName)) {
				try {
					String out = queryLeveledJpegs();
					setStatus(Status.SUCCESS_OK);
					return out;
				} catch (Exception e) {
					setStatus(Status.SERVER_ERROR_INTERNAL);
					return QUERY_ERROR_MESSAGE + e.getMessage();
				}
			} else {
				setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
				if (commandName == null)
					return EMPTY_COMMAND_MESSAGE;
				else
					return UNKNOWN_GET_COMMAND_MESSAGE + commandName;
			}
		}
	}

	@Override
	public String create()
	{
		if (getQueryValue(COMMAND_NAME) == null) {
			setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
			return MALFORMED_REQUEST_MESSAGE;
		} else {
			String commandName = getQueryValue(COMMAND_NAME);

			if (WindowLevelCommand.CREATE.hasCommandName(commandName)) {
				try {
					createLevelJpegs();
					setStatus(Status.SUCCESS_OK);
					return SUCCESS_MESSAGE;
				} catch (IOException e) {
					setStatus(Status.SERVER_ERROR_INTERNAL);
					return CREATE_ERROR_MESSAGE + e.getMessage();
				} catch (InterruptedException e) {
					setStatus(Status.SERVER_ERROR_INTERNAL);
					return CREATE_ERROR_MESSAGE + e.getMessage();
				}
			} else {
				setStatus(Status.CLIENT_ERROR_BAD_REQUEST);
				if (commandName == null)
					return EMPTY_COMMAND_MESSAGE;
				else
					return UNKNOWN_POST_COMMAND_MESSAGE + commandName;
			}
		}
	}

	/**
	 * Put in a request to level all of the images.
	 */
	private void createLevelJpegs() throws IOException, InterruptedException
	{
		String wl = getQueryValue("wl"); // TODO Define constants for these strings.
		String ww = getQueryValue("ww");
		String seriesuid = getQueryValue("seriesuid");
		String studyuid = getQueryValue("studyuid");
		// String direction = req.getParameter("direction");
		// String order = req.getParameter("order");

		File seriesDir = findSeriesDirectory(studyuid, seriesuid);

		// Get all the DICOM files in this directory.
		File[] dicomFiles = seriesDir.listFiles(new FileFilter() {
			@Override
			public boolean accept(File file)
			{
				return file.getName().endsWith(".dcm");
			}
		});

		// ToDo: sort the DICOM files into an optimized order.

		WindowLevelFactory levelFactory = WindowLevelFactory.getInstance();
		int windowWidth = Integer.parseInt(ww);
		int windowLevel = Integer.parseInt(wl);
		String seriesToLevel = DicomFormatUtil.formatDirToUid(seriesuid);

		levelFactory.setLevel(windowWidth, windowLevel, seriesToLevel);
		for (File dicomFile : dicomFiles) {
			levelFactory.submitDicomFile(dicomFile, seriesToLevel);
		}

		//
		// ToDo: (a) Read the order file
		// (b) determine an optimized order of creating the files.
		// (c) delete any work in the queue creating a different window level setting.
		// (d) Add the all into the queue most important first.
	}

	/**
	 * Get a list of all the jpegs that currently have level images to the parameters requested.
	 * 
	 * @return String
	 */
	private String queryLeveledJpegs() throws IOException
	{
		String wl = getQueryValue("wl"); // TODO Constants for these strings.
		String ww = getQueryValue("ww");
		String seriesuid = getQueryValue("seriesuid");
		String studyuid = getQueryValue("studyuid");
		StringBuilder out = new StringBuilder();

		File seriesDir = findSeriesDirectory(studyuid, seriesuid);

		File[] leveledJPegFiles = listLeveledJPegFiles(seriesDir, ww, wl);

		// ToDo: possibly return this in order-file order with spaces, for missing files.
		if (leveledJPegFiles.length == 0) {
			out.append("No Files found for ww=" + ww + ", wl=" + wl + "\n");
		} else {
			for (File currJPEG : leveledJPegFiles) {
				out.append(FileKey.getCanonicalPath(currJPEG) + "\n");
			}
		}
		return out.toString();
	}

	/**
	 * Return the directory.
	 * 
	 * @param studyuid String
	 * @param seriesuid String
	 * @return File
	 */
	private File findSeriesDirectory(String studyuid, String seriesuid)
	{
		if (studyuid == null || "".equals(studyuid)) {
			// the study was not specified, look for this series in the directories.
			studyuid = findStudyForSeries(seriesuid);
		}

		if (studyuid == null) {
			throw new IllegalStateException("Failed to find a study for seriesuid=" + seriesuid);
		}

		return new File(DicomFormatUtil.createDicomDirPath(studyuid, seriesuid));
	}

	/**
	 * Returns the study directory for this series.
	 * 
	 * @param seriesuid String
	 * @return String
	 */
	private String findStudyForSeries(String seriesuid)
	{
		String cachedStudyDir = seriesToStudyMap.get(seriesuid); // is the answer cached?
		if (cachedStudyDir != null) {
			return cachedStudyDir;
		}

		String dicomBaseDirPath = DicomFormatUtil.getDicomBaseDirPath();

		if (dicomBaseDirPath == null)
			throw new RuntimeException("no DICOM directory specified");

		File baseDicomDir = new File(DicomFormatUtil.getDicomBaseDirPath());

		File[] studyDirs = baseDicomDir.listFiles();

		if (studyDirs == null)
			throw new RuntimeException("invalid DICOM directory " + dicomBaseDirPath);

		for (File currStudyDir : studyDirs) {

			File[] seriesDirs = currStudyDir.listFiles();
			for (File currSeriesDir : seriesDirs) {

				String seriesDir = currSeriesDir.getName();
				String seriesUid = DicomFormatUtil.formatDirToUid(seriesDir);

				if (seriesDir.equals(seriesuid) || seriesUid.equals(seriesuid)) {
					// Found it!!
					String studyDirName = currStudyDir.getName();
					seriesToStudyMap.put(seriesDir, studyDirName);
					return studyDirName;
				}
			}
		}
		return null;
	}

	/**
	 * 
	 * @param ww String
	 * @param wl String
	 * @return String[]
	 */
	File[] listLeveledJPegFiles(File seriesDir, String ww, String wl)
	{

		String leveledDirName = "ww" + ww + "wl" + wl;

		File leveledDir = new File(seriesDir.getAbsolutePath() + "/" + leveledDirName);
		if (!leveledDir.exists()) {
			throw new IllegalArgumentException("Didn't find leveled directory :" + leveledDir.getAbsolutePath());
		}

		File[] retVal = leveledDir.listFiles(new FileFilter() {
			@Override
			public boolean accept(File file)
			{
				return file.getName().endsWith(".jpg");
			}
		});

		return retVal;
	}
}
