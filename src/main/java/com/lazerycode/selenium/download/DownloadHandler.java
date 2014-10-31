package com.lazerycode.selenium.download;

import com.lazerycode.selenium.extract.BinaryFileNames;
import com.lazerycode.selenium.extract.ExtractFilesFromArchive;
import com.lazerycode.selenium.repository.FileDetails;
import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.Logger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import java.io.File;
import java.util.Map;

public class DownloadHandler {

    private static final Logger LOG = Logger.getLogger(DownloadHandler.class);
    private final File rootStandaloneServerDirectory;
    private final File downloadedZipFileDirectory;
    private final Map<String, FileDetails> filesToDownload;
    final int fileDownloadRetryAttempts;
    private boolean overwriteFilesThatExist = false;
    private boolean checkFileHash = true;
    private FileDownloader fileDownloader;

    public DownloadHandler(File rootStandaloneServerDirectory, File downloadedZipFileDirectory, int fileDownloadRetryAttempts, int fileDownloadConnectTimeout, int fileDownloadReadTimeout, Map<String, FileDetails> filesToDownload, boolean overwriteFilesThatExist, boolean checkFileHash, boolean useSystemProxy) throws MojoFailureException {
        this.rootStandaloneServerDirectory = rootStandaloneServerDirectory;
        this.downloadedZipFileDirectory = downloadedZipFileDirectory;
        if (fileDownloadRetryAttempts < 1) {
            LOG.warn("Invalid number of retry attempts specified, defaulting to '1'...");
            this.fileDownloadRetryAttempts = 1;
        } else {
            this.fileDownloadRetryAttempts = fileDownloadRetryAttempts;
        }
        this.filesToDownload = filesToDownload;
        this.overwriteFilesThatExist = overwriteFilesThatExist;
        this.checkFileHash = checkFileHash;

        this.fileDownloader = new FileDownloader(downloadedZipFileDirectory, useSystemProxy);
        this.fileDownloader.setReadTimeout(fileDownloadReadTimeout);
        this.fileDownloader.setConnectTimeout(fileDownloadConnectTimeout);
    }

    public void getStandaloneExecutableFiles() throws Exception {
        LOG.info("Archives will be downloaded to '" + this.downloadedZipFileDirectory.getAbsolutePath() + "'");
        LOG.info("Standalone executable files will be extracted to '" + this.rootStandaloneServerDirectory + "'");
        LOG.info(" ");
        LOG.info("Preparing to download Selenium Standalone Executable Binaries...");
        for (Map.Entry<String, FileDetails> fileToDownload : this.filesToDownload.entrySet()) {
            LOG.info(" ");
            String currentFileAbsolutePath = this.downloadedZipFileDirectory + File.separator + FilenameUtils.getName(fileToDownload.getValue().getFileLocation().getFile());
            File desiredFile = new File(currentFileAbsolutePath);
            File fileToUnzip = downloadFile(fileToDownload.getValue());
            LOG.info("Checking to see if archive file '" + currentFileAbsolutePath + "' exists  : " + desiredFile.exists());
            if (desiredFile.exists()) {
                if (checkFileHash) {
                    FileHashChecker fileHashChecker = new FileHashChecker(desiredFile);
                    fileHashChecker.setExpectedHash(fileToDownload.getValue().getHash(), fileToDownload.getValue().getHashType());
                    boolean fileIsValid = fileHashChecker.fileIsValid();
                    LOG.info("Checking to see if archive file '" + currentFileAbsolutePath + "' is valid: " + fileIsValid);
                    if (fileIsValid) {
                        fileToUnzip = new File(currentFileAbsolutePath);
                    }
                } else {
                    fileToUnzip = new File(currentFileAbsolutePath);
                }
            }
            String extractionDirectory = this.rootStandaloneServerDirectory.getAbsolutePath() + File.separator + fileToDownload.getKey();
            String binaryForOperatingSystem = fileToDownload.getKey().replace("\\", "/").split("/")[1].toUpperCase();  //TODO should really store the OSType we have extracted somewhere rather than doing this hack!
            LOG.debug("Detected a binary for OSType: " + binaryForOperatingSystem);
            if (ExtractFilesFromArchive.extractFileFromArchive(fileToUnzip, extractionDirectory, this.overwriteFilesThatExist, BinaryFileNames.valueOf(binaryForOperatingSystem))) {
                LOG.info("File(s) copied to " + extractionDirectory);
            }
        }
    }

    /**
     * Perform the file download
     *
     * @return File
     * @throws MojoExecutionException
     */
    File downloadFile(FileDetails fileDetails) throws Exception {
        final String filename = FilenameUtils.getName(fileDetails.getFileLocation().getFile());
        for (int n = 0; n < this.fileDownloadRetryAttempts; n++) {

            File downloadedFile = fileDownloader.attemptToDownload(fileDetails.getFileLocation());

            if (null != downloadedFile) {
                if (!checkFileHash) {
                    return downloadedFile;
                }

                LOG.info("Checking to see if downloaded copy of '" + downloadedFile.getName() + "' is valid.");
                FileHashChecker fileHashChecker = new FileHashChecker(downloadedFile);
                fileHashChecker.setExpectedHash(fileDetails.getHash(), fileDetails.getHashType());
                if (fileHashChecker.fileIsValid()) {
                    return downloadedFile;
                }
            }

            LOG.info("Problem downloading '" + filename + "'... ");

            if (n + 1 < this.fileDownloadRetryAttempts) {
                LOG.info("Trying to download'" + filename + "' again...");
            }
        }

        throw new MojoExecutionException("Unable to successfully downloaded '" + filename + "'!");
    }
}
