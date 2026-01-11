package org.openas2.processor.receiver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.message.Message;
import org.openas2.params.InvalidParameterException;
import org.openas2.processor.Processor;
import org.openas2.util.IOUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public abstract class DirectoryPollingModule extends PollingModule {
    public static final String PARAM_OUTBOX_DIRECTORY = "outboxdir";
    public static final String PARAM_ERROR_DIRECTORY = "errordir";
    public static final String PARAM_SENT_DIRECTORY = "sentdir";
    public static final String PARAM_FILE_EXTENSION_FILTER = "fileextensionfilter";
    public static final String PARAM_FILE_EXTENSION_EXCLUDE_FILTER = "fileextensionexcludefilter";
    public static final String PARAM_FILE_NAME_EXCLUDE_FILTER = "filenameexcluderegexfilter";
    public static final String PARAM_PROCESS_IN_PARALLEL = "process_files_in_parallel";
    public static final String PARAM_MAX_PARALLEL_FILES = "max_parallel_files";
    public static final String PARAM_MAX_FILE_PROCESSING_TIME_MINUTES = "max_file_processing_time_minutes";

    private final int MAX_FILE_PROCESSING_TIME_DEFAULT_MINUTES = 30;

    // Files that have been registered by the poller - key is the absolute file path and value is the file size
    private Map<String, Long> trackedFiles = new HashMap<String, Long>(); 
    // Files that have been passed to a processor - key is the absolute file path and value is the processing start timestamp
    private Map<String, Long> processingFiles = new HashMap<String, Long>(); 
    // Files that have been processed in thread mode and need removing from the tracked files maps by the main thread
    Collection<String> threadProcessedFiles = Collections.synchronizedCollection(new ArrayList<String>());

    private String errorDir = null;
    private String sentDir = null;
    private boolean processFilesAsThreads = false;
    private int maxProcessingThreads = 20;
    private long maxFileProcessingTime = MAX_FILE_PROCESSING_TIME_DEFAULT_MINUTES*60*1000;
    // support fixed size thread group to run file processing as threads 
    private ExecutorService executorService = null;
    private List<String> allowExtensions = new ArrayList<String>();
    private List<String> excludeExtensions = new ArrayList<String>();
    private String excludeFilenameRegexFilter = "";

    private Logger logger = LoggerFactory.getLogger(DirectoryPollingModule.class);

    public void init(Session session, Map<String, String> options) throws OpenAS2Exception {
        super.init(session, options);
        // Check all the directories are configured and actually exist on the file
        // system
        try {
            setOutboxDir(getParameter(PARAM_OUTBOX_DIRECTORY, true));
            IOUtil.getDirectoryFile(getOutboxDir());
            errorDir = getParameter(PARAM_ERROR_DIRECTORY, true);
            IOUtil.getDirectoryFile(errorDir);
            sentDir = getParameter(PARAM_SENT_DIRECTORY, false);
            if(null != sentDir) {
                IOUtil.getDirectoryFile(sentDir);
            }
            processFilesAsThreads = getParameter(PARAM_PROCESS_IN_PARALLEL, "false").equalsIgnoreCase("true");
            maxProcessingThreads = getParameterInt(PARAM_MAX_PARALLEL_FILES, false, maxProcessingThreads);
            if (processFilesAsThreads) {
                // Create the thread pool
                executorService = Executors.newFixedThreadPool(maxProcessingThreads);
            }
            
            String pendingInfoFolder = getSession().getProcessor().getParameters().get(Processor.PENDING_MDN_INFO_DIRECTORY_IDENTIFIER);
            IOUtil.getDirectoryFile(pendingInfoFolder);
            String pendingFolder = getSession().getProcessor().getParameters().get(Processor.PENDING_MDN_MSG_DIRECTORY_IDENTIFIER);
            IOUtil.getDirectoryFile(pendingFolder);
            String maxFileProcessingTimeFromPAram = getParameter(PARAM_MAX_FILE_PROCESSING_TIME_MINUTES, "");
            if (maxFileProcessingTimeFromPAram.length() > 0) {
                maxFileProcessingTime = Long.parseLong(maxFileProcessingTimeFromPAram) * 60 * 1000;
            }
            String allowExtensionFilter = getParameter(PARAM_FILE_EXTENSION_FILTER, "");
            String excludeExtensionFilter = getParameter(PARAM_FILE_EXTENSION_EXCLUDE_FILTER, "");
            String excludeFilenameRegexFilter = getParameter(PARAM_FILE_NAME_EXCLUDE_FILTER, "");

            if (allowExtensionFilter.length() > 0) {
                this.allowExtensions = Arrays.asList(allowExtensionFilter.split("\\s*,\\s*"));
            }
            if (excludeExtensionFilter.length() > 0) {
                this.excludeExtensions = Arrays.asList(excludeExtensionFilter.split("\\s*,\\s*"));
            }
            if (excludeFilenameRegexFilter.length() > 0) {
                this.excludeFilenameRegexFilter = excludeFilenameRegexFilter;
            }

        } catch (IOException e) {
            throw new OpenAS2Exception("Failed to initialise directory poller.", e);
        }
    }

    @Override
    public boolean healthcheck(List<String> failures) {
        try {
            IOUtil.getDirectoryFile(getOutboxDir());
        } catch (IOException e) {
            failures.add(this.getClass().getSimpleName() + " - Polling directory is not accessible: " + getOutboxDir());
            LoggerFactory.getLogger(DirectoryPollingModule.class.getName()).error(null, e);
            return false;
        } catch (InvalidParameterException ex) {
            LoggerFactory.getLogger(DirectoryPollingModule.class.getName()).error(null, ex);
            return false;
        }
        return true;
    }

    public void poll() {
        try {
            // update tracking info. if a file is ready, process it
            updateTracking();

            // scan the directory for new files
            scanDirectory(getOutboxDir());
        } catch (OpenAS2Exception oae) {
            oae.log();
        } catch (Exception e) {
            logger.error("Unexpected error occurred polling directory for files to send: " + getOutboxDir(), e);
        }
    }

    protected void scanDirectory(String directory) throws IOException, InvalidParameterException {

        /**
         * Support allowed extensions excluded extensions.
         */
        if (logger.isTraceEnabled()) {
            logger.trace("Polling module scanning directory: " + directory);
        }
        File directoryAsFile = IOUtil.getDirectoryFile(directory);
        // Wrap in try-with-resources block to ensure close() is called
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(directoryAsFile.toPath(), entry -> {
                if (Files.isDirectory(entry)) {
                    return false;
                }
                if (isTracked(entry.toAbsolutePath().toString())) {
                    return false;
                }
                String name = entry.getFileName().toString();
                if (logger.isTraceEnabled()) {
                    logger.trace("Polling module file name found: " + name);
                }
                String extension = name.substring(name.lastIndexOf(".") + 1);
                if (!allowExtensions.isEmpty() && !allowExtensions.contains(extension)) {
                    // There is a list of allowed extensions and this one not in it so do not include
                    return false;
                }
                if (!excludeExtensions.isEmpty() && excludeExtensions.contains(extension)) {
                    // This is a disallowed extension so ignore this file
                    return false;
                }
                // Check if there are filename regex exclusions if not already disallowed
                if (excludeFilenameRegexFilter.length() > 0 && name.matches(excludeFilenameRegexFilter)) {
                    // This is a disallowed extension so ignore this file
                    return false;
                }
                return true;
            })) {
                for (Path dir : dirs) {
                    File currentFile = dir.toFile();
                    // Don't track locked files as this can create false positives when file is being created
                    if (!fileIsLocked(currentFile)) {
                        // Found an untracked file so add it
                        trackedFiles.put(currentFile.getAbsolutePath(), currentFile.length());
                    }
                }
        }
    }

    protected boolean fileIsLocked(File file) {
        if (file.isFile()) {
            try {
                // check for a write-lock on file, will skip file if it's write locked
                FileOutputStream fOut = new FileOutputStream(file, true);
                fOut.close();
            } catch (IOException ioe) {
                // a sharing violation occurred, ignore the file for now
                if (logger.isDebugEnabled()) {
                    try {
                        logger.debug("Directory poller detected a non-writable file and will be ignored: " + file.getCanonicalPath());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                return true;
            }
        }
        return false;
    }

    private boolean isTracked(String filePath) {
        return trackedFiles.containsKey(filePath);
    }

    private boolean isBeingProcessed(String filePath) {
        return processingFiles.containsKey(filePath);
    }

    protected void processSingleFile(File file, String fileEntryKey) {
        try {
            processFile(file);
        } catch (OpenAS2Exception e) {
            e.log();
            try {
                IOUtil.handleArchive(file, errorDir);
            } catch (OpenAS2Exception e1) {
                logger.error("Error handling file error for file: " + file.getAbsolutePath(), e1);
                //forceStop(e1);
                return;
            }
        }     
    }

    private void processFileInThread(File file, String fileEntryKey) {
        if (logger.isDebugEnabled()) {
            logger.debug("Parallel processing mode handling file: " + file.getName());
        }
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    processSingleFile(file, fileEntryKey);
                } finally {
                    // Add to list for removal from tracking maps when updateTracking is called
                    threadProcessedFiles.add(fileEntryKey);
                }
            }
        });
    }

    private void updateTracking() {
        // first remove processed files if we are running in parallel processing mode to avoid concurrency issues
        if (processFilesAsThreads) {
            synchronized (threadProcessedFiles) {
                threadProcessedFiles.forEach((fileEntryKey) -> {
                    trackedFiles.remove(fileEntryKey);
                    processingFiles.remove(fileEntryKey);
                });
                threadProcessedFiles.clear();
            }
        }
        // Use an iterator to be able to remove entries whilst iterating over the map.
        Iterator<Map.Entry<String, Long>> iter = trackedFiles.entrySet().iterator();
        while (iter.hasNext()) {
            Map.Entry<String, Long> fileEntry = iter.next();
            // get the file and it's stored length
            String fileEntryKey = fileEntry.getKey();
            File file = new File(fileEntryKey);
            long trackedFileLength = fileEntry.getValue().longValue();

            // if the file no longer exists, remove it from the tracker
            if (!file.exists()) {
                iter.remove();
                processingFiles.remove(fileEntryKey);
            } else if (isBeingProcessed(fileEntryKey)) {
                // Handle failed processing of files based on a max timeout
                long elapsedTime = System.currentTimeMillis() - processingFiles.get(fileEntryKey);
                if (elapsedTime > maxFileProcessingTime) {
                    iter.remove();
                    processingFiles.remove(fileEntryKey);
                    logger.error("WARNING: A file that was being processed has exceeded the processing time limit and has been removed from the processing tracker: " + fileEntryKey
                            + "\n\tThis will trigger a retry to send the file. If this causes duplicate sends then try to increase the " + PARAM_MAX_FILE_PROCESSING_TIME_MINUTES + " value.");
                }
            } else {
                // if the file length has increased, update the tracker
                long newLength = file.length();
                if (newLength > trackedFileLength) {
                    trackedFiles.put(fileEntryKey, Long.valueOf(newLength));
                } else {
                    // no change in file length so process the file
                    // Add the processing flag on the file now otherwise in threaded mode there is a race condition
                    processingFiles.put(fileEntryKey, System.currentTimeMillis());
                    if (processFilesAsThreads) {
                        processFileInThread(file, fileEntryKey);
                    } else {
                        try {
                            processSingleFile(file, fileEntryKey);
                        } finally {
                            iter.remove();
                            processingFiles.remove(fileEntryKey);
                        }
                    }

                }
            }
        }
    }

    protected abstract Message createMessage();

    protected void processFile(File file) throws OpenAS2Exception {

        if (logger.isInfoEnabled()) {
            logger.info("Processing file: " + file.getAbsolutePath());
        }

        try {
            processDocument(file, file.getName());
        } catch (FileNotFoundException e) {
            // Try to move original file to error dir in case error handling has not done it  for us.
            IOUtil.handleArchive(file, errorDir, false);
            throw new OpenAS2Exception("Failed to initiate processing for file:" + file.getAbsolutePath(), e);
        }
    }
 }
