//Copyright (c) 2015 The Board of Trustees of the Leland Stanford Junior University
//All rights reserved.
//
//Redistribution and use in source and binary forms, with or without modification, are permitted provided that
//the following conditions are met:
//
//Redistributions of source code must retain the above copyright notice, this list of conditions and the following
//disclaimer.
//
//Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the
//following disclaimer in the documentation and/or other materials provided with the distribution.
//
//Neither the name of The Board of Trustees of the Leland Stanford Junior University nor the names of its
//contributors (Daniel Rubin, et al) may be used to endorse or promote products derived from this software without
//specific prior written permission.
//
//THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
//INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
//DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
//SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
//SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
//WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE
//USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
package edu.stanford.epad.epadws.processing.pipeline.watcher;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import edu.stanford.epad.common.util.EPADConfig;
import edu.stanford.epad.common.util.EPADFileUtils;
import edu.stanford.epad.common.util.EPADLogger;
import edu.stanford.epad.common.util.FileKey;
import edu.stanford.epad.epadws.dcm4chee.Dcm4CheeOperations;
import edu.stanford.epad.epadws.epaddb.EpadDatabase;
import edu.stanford.epad.epadws.epaddb.EpadDatabaseOperations;
import edu.stanford.epad.epadws.processing.model.DicomUploadFile;
import edu.stanford.epad.epadws.processing.pipeline.threads.ShutdownSignal;
import edu.stanford.epad.epadws.service.DefaultEpadProjectOperations;
import edu.stanford.epad.epadws.service.EpadProjectOperations;
import edu.stanford.epad.epadws.service.UserProjectService;

/**
 * Watches for a new directory containing ZIP or DICOM files in the ePAD upload directory. When a new directory is found
 * it puts a "dir.found" file into it. If the upload is a ZIP file it waits for the ZIP upload to complete and then
 * unzips it.
 * <p>
 * It then generates DICOM tag files for each DICOM file, creates XNAT entities for the DICOM files, and sends the DICOM
 * files to DCM4CHEE.
 * 
 * @author amsnyder
 */
public class EPADUploadDirWatcher implements Runnable
{
	private static final int CHECK_INTERVAL = 5000; // Check every 5 seconds
	private static final String FOUND_DIR_FILE = "dir.found";
	private static final long MAX_WAIT_TIME = 3600000; // 1 hour (was 20 minutes before)
	private static final EPADLogger log = EPADLogger.getInstance();
	private final EpadProjectOperations projectOperations = DefaultEpadProjectOperations.getInstance();

	@Override
	public void run()
	{
		try {
			ShutdownSignal shutdownSignal = ShutdownSignal.getInstance();
			File rootUploadDirectory = new File(EPADConfig.getEPADWebServerUploadDir());
			log.info("Starting the ePAD upload directory watcher; directory =" + EPADConfig.getEPADWebServerUploadDir());
			long count = 0;
			while (true) {
				if (shutdownSignal.hasShutdown())
				{
					log.info("Warning: EPADUploadDirWatcher shutdown signal received.");
					return;
				}

				try {
					if (count%720 == 0)
						log.info("EPADUploadDirWatcher: Checking new uploads, count:" + count);
					count++;
					List<File> newUploadDirectories = findNewUploadDirectory(rootUploadDirectory);
					if (newUploadDirectories != null) {
						for (File newUploadDirectory : newUploadDirectories) {
							processUploadDirectory(newUploadDirectory);
						}
					}
				} catch (Exception e) {
					log.warning("EPADUploadDirWatcher thread error ", e);
				}
				if (shutdownSignal.hasShutdown())
				{
					log.info("Warning: EPADUploadDirWatcher shutdown signal received.");
					return;
				}
				TimeUnit.MILLISECONDS.sleep(CHECK_INTERVAL);
			}
		} catch (Exception e) {
			log.severe("Warning: EPADUploadDirWatcher thread error", e);
		} finally {
			log.info("Warning: EPADUploadDirWatcher thread done.");
		}
		log.info("Warning: EPADUploadDirWatcher shutting down.");
	}

	private List<File> findNewUploadDirectory(File dir)
	{ // Looks for new directories without the dir.found file.
		List<File> retVal = new ArrayList<File>();

		File[] allFiles = dir.listFiles();
		for (File currFile : allFiles) {
			if (currFile.isDirectory()) {
				if (!hasFoundDirFile(currFile)) {
					retVal.add(currFile);
				}
			}
		}
		return retVal;
	}

	private boolean hasFoundDirFile(File dir)
	{
		String[] allFilePaths = dir.list();
		for (String currPath : allFilePaths) {
			if (currPath.indexOf(FOUND_DIR_FILE) > 0) {
				return true;
			}
		}
		return false;
	}

	private void processUploadDirectory(File directory) throws InterruptedException
	{
		File zipFile = null;
		try {
			boolean hasZipFile = waitOnEmptyUploadDirectory(directory);
			if (hasZipFile) {
				zipFile = waitForZipUploadToComplete(directory);
				unzipFiles(zipFile);
			}
			// TODO Should not create XNAT entities until the DICOM send succeeds.
			String username = UserProjectService.createProjectEntitiesFromDICOMFilesInUploadDirectory(directory);
			log.info("Cleaning upload directory");
			cleanUploadDirectory(directory);
			if (username != null)
				sendFilesToDcm4Chee(username, directory);
		} catch (Exception e) {
			log.warning("Exception uploading " + directory.getAbsolutePath(), e);
			String userName = UserProjectService.getUserNameFromPropertiesFile(directory);
			if (userName != null) {
				String zipName = "DicomFile";
				if (zipFile != null) zipName = zipFile.getName();
				EpadDatabaseOperations epadDatabaseOperations = EpadDatabase.getInstance().getEPADDatabaseOperations();
				epadDatabaseOperations.insertEpadEvent(
						userName, 
						"Error processing uploaded file:" + zipName, 
						zipName, "", zipName, zipName, zipName, zipName, "Upload Error:" + e.getMessage());
				projectOperations.userErrorLog(userName, "Error processing zip file:" + zipName);
			}
			writeExceptionLog(directory, e);
		} finally {
			log.info("Upload of directory " + directory.getAbsolutePath() + " finished");
			deleteUploadDirectory(directory);
		}
	}

	private void cleanUploadDirectory(File dir)
	{ // TODO Should be deleteFilesInDirectoryWithoutExtension("dcm");
		EPADFileUtils.deleteFilesInDirectoryWithExtension(dir, "properties");
		EPADFileUtils.deleteFilesInDirectoryWithExtension(dir, "zip");
		EPADFileUtils.deleteFilesInDirectoryWithExtension(dir, "log");
		EPADFileUtils.deleteFilesInDirectoryWithExtension(dir, "json");
	}

	private boolean waitOnEmptyUploadDirectory(File dir) throws InterruptedException
	{
		log.info("Found new upload - waiting for it to complete in directory " + dir.getAbsolutePath());
		// If this file has only one ZIP file, wait for it to complete upload.
		long emptyDirStartWaitTime = System.currentTimeMillis();
		boolean hasZipFile = false;

		long oldSize = -1;
		int oldNumberOfFiles = -1;

		while (true) {
			String[] filePaths = dir.list();

			if (filePaths != null) {
				if (filePaths.length > 0) {
					long newSize = dir.getTotalSpace();
					int newNumberOfFiles = filePaths.length;

					if (oldNumberOfFiles != newNumberOfFiles || oldSize != newSize) {
						oldNumberOfFiles = newNumberOfFiles;
						oldSize = newSize;
					} else {
						log.info("Files uploaded:" + Arrays.toString(filePaths));
						for (String currPath : filePaths) {
							currPath = currPath.toLowerCase();
							if (currPath.endsWith(".zip")) {
								hasZipFile = true;
							}
						}
						return hasZipFile;
					}
				}
			}
			if ((System.currentTimeMillis() - emptyDirStartWaitTime) > MAX_WAIT_TIME)
				throw new IllegalStateException("Exceeded maximum wait time to upload a ZIP file");
			Thread.sleep(2000);
		}
	}

	private File waitForZipUploadToComplete(File dir) throws InterruptedException
	{
		log.info("Waiting for completion of unzip in upload directory " + dir.getAbsolutePath());
		long zipFileStartWaitTime = System.currentTimeMillis();
		long prevZipFileSize = -1;
		long prevZipFileLastUpdated = 0;

		while (true) {
			File[] zipFiles = dir.listFiles(new FilenameFilter() {
				@Override
				public boolean accept(File dir, String name)
				{
					return name.toLowerCase().endsWith(".zip");
				}
			});

			if (zipFiles == null) {
				throw new IllegalStateException("No ZIP file in upload directory " + dir.getAbsolutePath());
			} else if (zipFiles.length > 1) {
				int numZipFiles = zipFiles.length;
				throw new IllegalStateException("Too many ZIP files (" + numZipFiles + ") in upload directory:"
						+ dir.getAbsolutePath());
			}
			FileKey zipFileKey = new FileKey(zipFiles[0]);
			DicomUploadFile zipFile = new DicomUploadFile(zipFileKey.getFile());

			long currZipFileSize = zipFile.getSize();
			long currZipFileLastUpdated = zipFile.getLastUpdated();

			if (prevZipFileSize == currZipFileSize && prevZipFileLastUpdated == currZipFileLastUpdated) {
				return zipFileKey.getFile(); // Uploading complete
			} else {
				prevZipFileSize = currZipFileSize;
				prevZipFileLastUpdated = currZipFileLastUpdated;
			}
			if ((System.currentTimeMillis() - zipFileStartWaitTime) > MAX_WAIT_TIME) {
				throw new IllegalStateException("ZIP file upload time exceeded");
			}
			Thread.sleep(2000);
		}
	}

	private void unzipFiles(File zipFile) throws IOException
	{
		log.info("Unzipping " + zipFile.getAbsolutePath());
		EPADFileUtils.extractFolder(zipFile.getAbsolutePath());
	}

	private void sendFilesToDcm4Chee(String username, File directory) throws Exception
	{
		try {
			log.info("Sending DICOM files in upload directory " + directory.getAbsolutePath() + " to DCM4CHEE");
			projectOperations.userInfoLog(username, "Sending DICOM files in upload directory " + directory.getAbsolutePath() + " to DCM4CHEE");
			Dcm4CheeOperations.dcmsnd(directory, true);
		} catch (Exception x) {
			EpadDatabaseOperations epadDatabaseOperations = EpadDatabase.getInstance().getEPADDatabaseOperations();
			epadDatabaseOperations.insertEpadEvent(
					username, 
					"Error sending DICOM files to DCM4CHEE", 
					"Dicoms", "Dicoms", "Dicoms", "Dicoms", "Dicoms", "Dicoms", "Error Processing Upload");					
			projectOperations.userErrorLog(username, "Error sending DICOM files to DCM4CHEE:" + x);
		}
	}

	private void deleteUploadDirectory(File dir)
	{
		log.info("Deleting upload directory " + dir.getAbsolutePath());
		EPADFileUtils.deleteDirectoryAndContents(dir);
	}

	private void writeExceptionLog(File dir, Exception e)
	{
		String fileName = dir.getAbsolutePath() + "/exception_" + System.currentTimeMillis() + ".log";
		String content = makeLogExpContents(e);
		EPADFileUtils.write(new File(fileName), content);
	}

	private String makeLogExpContents(Exception e)
	{
		StringBuilder sb = new StringBuilder();
		sb.append("Failed to dcmsnd file for reason: ").append(e.getMessage()).append("\n");

		StackTraceElement[] ste = e.getStackTrace();
		for (StackTraceElement currSte : ste) {
			sb.append(currSte.getFileName()).append(".").append(currSte.getMethodName()).append(currSte.getLineNumber());
		}
		return sb.toString();
	}
}
