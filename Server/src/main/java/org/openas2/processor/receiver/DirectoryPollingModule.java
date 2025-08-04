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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public abstract class DirectoryPollingModule extends PollingModule {
    public static final String PARAM_OUTBOX_DIRECTORY = "outboxdir";
    public static final String PARAM_ERROR_DIRECTORY = "errordir";
    public static final String PARAM_SENT_DIRECTORY = "sentdir";
    public static final String PARAM_FILE_EXTENSION_FILTER = "fileextensionfilter";
    public static final String PARAM_FILE_EXTENSION_EXCLUDE_FILTER = "fileextensionexcludefilter";
    public static final String PARAM_FILE_NAME_EXCLUDE_FILTER = "filenameexcluderegexfilter";
    public static final String PARAM_PROCESS_IN_PARALLEL = "process_files_in_paralllel";
    public static final String PARAM_MAX_PARALLEL_FILES = "max_parallel_files";
    private Map<String, Long> trackedFiles;
    private String errorDir = null;
    private String sentDir = null;
    private boolean processFilesAsThreads = false;
    private int maxProcessingThreads = 20;
    // support fixed size thread group to run file processing as threads 
    private ExecutorService executorService = null;
    private List<String> allowExtensions;
    private List<String> excludeExtensions;
    private String excludeFilenameRegexFilter = null;

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
            
            
            String allowExtensionFilter = getParameter(PARAM_FILE_EXTENSION_FILTER, "");
            String excludeExtensionFilter = getParameter(PARAM_FILE_EXTENSION_EXCLUDE_FILTER, "");
            String excludeFilenameRegexFilter = getParameter(PARAM_FILE_NAME_EXCLUDE_FILTER, "");

            if (allowExtensionFilter == null || allowExtensionFilter.length() < 1) {
                this.allowExtensions = new ArrayList<String>();
            } else {
                this.allowExtensions = Arrays.asList(allowExtensionFilter.split("\\s*,\\s*"));
            }
            if (excludeExtensionFilter == null || excludeExtensionFilter.length() < 1) {
                this.excludeExtensions = new ArrayList<String>();
            } else {
                this.excludeExtensions = Arrays.asList(excludeExtensionFilter.split("\\s*,\\s*"));
            }
            if (excludeFilenameRegexFilter != null && excludeFilenameRegexFilter.length() > 0) {
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

        /* Claudio.Degioanni - Versione modificata 20210628 2.11 - Start */

        /**
         * Rispetto alla versione tesi ho aggiunto il supporto per allowExtensions e
         * excludeExtensions aggiunto nelle versioni dopo
         */
        if (logger.isTraceEnabled()) {
            logger.trace("Polling module scanning directory: " + directory);
        }
        File directoryAsFile = IOUtil.getDirectoryFile(directory);
        // Wrap in try-with-resources block to ensure close() is called
        try (DirectoryStream<Path> dirs = Files.newDirectoryStream(directoryAsFile.toPath(), entry -> {
                String name = entry.getFileName().toString();
                if (Files.isDirectory(entry)) {
                    return false;
                }
                if (logger.isTraceEnabled()) {
                    logger.trace("Polling module file name found: " + name);
                }
                String extension = name.substring(name.lastIndexOf(".") + 1);
                boolean isAllowed = true;
                if (!allowExtensions.isEmpty()) {
                    isAllowed = allowExtensions.contains(extension);
                }
                // Check for the excluded filters if not already disallowed
                if (isAllowed && !excludeExtensions.isEmpty()) {
                    isAllowed = !excludeExtensions.contains(extension);
                }
                // Check if there are filename regex exclusions if not already disallowed
                if (isAllowed && excludeFilenameRegexFilter != null) {
                    isAllowed = !name.matches(excludeFilenameRegexFilter);
                }
                return isAllowed;

            })) {

            for (Path dir : dirs) {
                File currentFile = dir.toFile();

                if (checkFile(currentFile)) {
                    // start watching the file's size if it's not already being watched
                    trackFile(currentFile);
                }
            }
            /* Claudio.degioanni - Versione modificata 20210628 2.11 - End */
        }
    }

    protected boolean checkFile(File file) {
        if (file.exists() && file.isFile()) {
            try {
                // check for a write-lock on file, will skip file if it's write
                // locked
                FileOutputStream fOut = new FileOutputStream(file, true);
                fOut.close();
                return true;
            } catch (IOException ioe) {
                // a sharing violation occurred, ignore the file for now
                if (logger.isDebugEnabled()) {
                    try {
                        logger.debug("Directory poller detected a non-writable file and will be ignored: " + file.getCanonicalPath());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        return false;
    }

    private void trackFile(File file) {
        Map<String, Long> trackedFiles = getTrackedFiles();
        String filePath = file.getAbsolutePath();
        if (trackedFiles.get(filePath) == null) {
            trackedFiles.put(filePath, file.length());
        }
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
        } finally {
            trackedFiles.remove(fileEntryKey);
        }        
    }

    private void updateTracking() {
        // clone the trackedFiles map, iterator through the clone and modify the
        // original to avoid iterator exceptions
        // is there a better way to do this?
        Map<String, Long> trackedFiles = getTrackedFiles();
        Map<String, Long> trackedFilesClone = new HashMap<String, Long>(trackedFiles);

        for (Map.Entry<String, Long> fileEntry : trackedFilesClone.entrySet()) {
            // get the file and it's stored length
            String fileEntryKey = fileEntry.getKey();
            File file = new File(fileEntry.getKey());
            long fileLength = fileEntry.getValue().longValue();

            // if the file no longer exists, remove it from the tracker
            if (!checkFile(file)) {
                trackedFiles.remove(fileEntryKey);
            } else {
                // if the file length has changed, update the tracker
                long newLength = file.length();
                if (newLength != fileLength) {
                    trackedFiles.put(fileEntryKey, Long.valueOf(newLength));
                } else {
                    // if the file length has stayed the same, process the file
                    // and then stop tracking it
                    if (processFilesAsThreads) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Parallel processing mode handling file: " + file.getName());
                        }
                        executorService.execute(new Runnable() {
                            @Override
                            public void run() {
                                processSingleFile(file, fileEntryKey);
                            }
                        });

                    } else {
                        processSingleFile(file, fileEntryKey);
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

    private Map<String, Long> getTrackedFiles() {
        if (trackedFiles == null) {
            trackedFiles = new HashMap<String, Long>();
        }
        return trackedFiles;
    }
}
