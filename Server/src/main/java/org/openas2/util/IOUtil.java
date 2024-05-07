package org.openas2.util;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.apache.commons.io.FileUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.message.InvalidMessageException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardCopyOption;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.InvalidPathException;
import java.util.List;
import java.util.UUID;
import org.openas2.params.CompositeParameters;
import org.openas2.params.DateParameters;
import org.openas2.params.InvalidParameterException;
import org.openas2.params.ParameterParser;
import org.openas2.params.RandomParameters;


public class IOUtil {
    //private static final String VALID_FILENAME_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789.@-";

    private static final Log logger = LogFactory.getLog(IOUtil.class.getSimpleName());

    public static OutputStreamWriter getOutPutStreamWriter(String target, boolean createDirectories, String charsetName) throws IOException {
        if (charsetName == null) {
            charsetName = StandardCharsets.UTF_8.name();
        }
        Charset charSet = Charset.forName(charsetName);
        return new OutputStreamWriter(getFileOutPutStream(target, createDirectories), charSet);
    }

    public static FileOutputStream getFileOutPutStream(String target, boolean createDirectories) throws IOException {
        File tgtFile = new File(target);
        if (createDirectories) {
            File dir = tgtFile.getParentFile();
            if (dir != null && !dir.exists()) {
                createDirs(dir);
            }
        }
        return new FileOutputStream(tgtFile);
    }

    protected static String getExpandedPath(String directory) throws InvalidParameterException {
        CompositeParameters compParams = new CompositeParameters(false)
                            .add("date", new DateParameters())
                            .add("rand", new RandomParameters());

        return ParameterParser.parse(directory, compParams);
    }

    public static File getDirectoryFile(String directory) throws IOException, InvalidParameterException {
        File dir = new File(getExpandedPath(directory));
        if (!dir.exists()) {
            createDirs(dir);
        }
        return dir;
    }

    public static String getTransferRate(int bytes, ProfilerStub stub) {
        StringBuffer buf = new StringBuffer();
        buf.append(bytes).append(" bytes in ");
        buf.append(stub.getCombined()).append(" at ");

        long time = stub.getDifference();

        if (time != 0) {
            double stime = time / 1000.0;
            long rate = Math.round(bytes / stime);
            buf.append(getTransferRate(rate));
        } else {
            buf.append(getTransferRate(bytes));
        }

        return buf.toString();
    }

    private static String getTransferRate(long bytesPerSecond) {
        StringBuilder buf = new StringBuilder();
        long kbytesPerSecond = bytesPerSecond / 1024;

        if (bytesPerSecond < 1024) {
            buf.append(bytesPerSecond).append(" Bps");
        } else if (kbytesPerSecond < 1024) {
            buf.append(kbytesPerSecond).append(".").append(bytesPerSecond % 1024).append(" KBps");
        } else {
            buf.append(kbytesPerSecond / 1024).append(".").append(kbytesPerSecond % 1024).append(" MBps");
        }
        return buf.toString();
    }

    public static File getUnique(File dir, String filename) {
        filename = cleanFilename(filename);
        return new File(dir, filename + "." + UUID.randomUUID());
    }

    public static String cleanFilename(String filename) {
        /*
         * byte[] fnBytes = filename.getBytes(); byte c;
         *
         * for (int i = 0; i < fnBytes.length; i++) { c = fnBytes[i];
         *
         * if (VALID_FILENAME_CHARS.indexOf(c) == -1) { fnBytes[i] = '_'; } }
         *
         * return new String(fnBytes);
         */
        String reservedFilenameChars = Properties.getProperty("reservedFilenameCharacters", "<>:\"|?*");
        if (reservedFilenameChars != null && reservedFilenameChars.length() > 0) {
            String srchReplStr = reservedFilenameChars.replaceAll("\\[", "\\[").replaceAll("\\]", "\\]");
            if (reservedFilenameChars.contains(":") && filename.matches("^[a-zA-Z]{1}:.*")) {
                filename = filename.substring(0, 2) + filename.substring(2).replaceAll("[" + srchReplStr + "]", "");
            } else {
                filename = filename.replaceAll("[" + srchReplStr + "]", "");
            }
            // also remove control characters
            filename = filename.replaceAll("[\u0000-\u001F]", "");
        }
        return filename;
    }

    /**
     * Method used to archive file on an specific directory with
     * the option to suppress the InvalidMessageException event
     * Note: Used by directory poller to move the file to an error directory
     * @param file
     * @param archiveDirectory
     * @throws OpenAS2Exception
     */
    public static void handleArchive(File file, String archiveDirectory) throws OpenAS2Exception {
        handleArchive(file, archiveDirectory, true);
    }

    /**
     * Method used to archive file on an specific directory with
     * the option to suppress the InvalidMessageException event
     * @param file
     * @param archiveDirectory
     * @param throwException
     * @throws OpenAS2Exception
     */
    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_EXCEPTION")
    public static void handleArchive(File file, String archiveDirectory, boolean throwException) throws OpenAS2Exception {
        File destFile = null;

        try {
            File archiveDir = IOUtil.getDirectoryFile(archiveDirectory);

            destFile = new File(archiveDir, file.getName());

            // move the file
            destFile = IOUtil.moveFile(file, destFile, false);
        } catch (IOException ioe) {
            InvalidMessageException im = new InvalidMessageException("Failed to move " + file.getAbsolutePath() + " to directory " + destFile.getAbsolutePath());
            im.initCause(ioe);
            throw im;
        }
        if(throwException) {
            // make sure an error of this event is logged
            InvalidMessageException imMoved = new InvalidMessageException("Moved " + file.getAbsolutePath() + " to " + destFile.getAbsolutePath());
            imMoved.log();
        }
    }

    public static File moveFile(File src, File dest, boolean overwrite) throws IOException {
        if (!overwrite && dest.exists()) {
            dest = getUnique(dest.getAbsoluteFile().getParentFile(), dest.getName());
        }
        // fast-track, try an atomic move.
        IOException moveFail = null;
        try {
            moveFileAtomic(src, dest, overwrite);
            return dest;
        } catch (IOException e) {
            moveFail = e;
        }
        // check if destination directory exists
        boolean destDirCreated = false;
        File destParent = dest.getParentFile();
        if (destParent != null && !destParent.exists()) {
            createDirs(destParent);
            destDirCreated = true;
        }
        // retry atomic move when destination directory was created
        if (destDirCreated) {
            try {
                moveFileAtomic(src, dest, overwrite);
                return dest;
            } catch (IOException e) {
                moveFail = e;
            }
        }
        if (logger.isTraceEnabled()) {
            logger.trace("Atomic move for " + src + " failed with [" + moveFail + "], trying alternatives to move to " + dest);
        }
        if (overwrite) {
            // method below overwrites destination file when it exists.
            FileUtils.copyFile(src, dest);
            deleteFile(src);
            if (logger.isDebugEnabled()) {
                logger.debug("Moved file using copy-delete from " + src + " to " + dest);
            }
        } else {
            // method below will throw an exception when destination file exists.
            FileUtils.moveFile(src, dest);
            if (logger.isDebugEnabled()) {
                logger.debug("Moved file using rename from " + src + " to " + dest);
            }
        }
        return dest;
    }

    private static void moveFileAtomic(File src, File dest, boolean overwrite) throws IOException {
        if (overwrite) {
            Files.move(src.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.move(src.toPath(), dest.toPath(), StandardCopyOption.ATOMIC_MOVE);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Moved file atomically from " + src + " to " + dest);
        }
    }

    private static synchronized void createDirs(File dir) throws IOException {
        /*
         * This method must be synchronized to prevent checking on partially constructed directories.
         */
        if (dir != null) {
            if (dir.mkdirs()) {
                logger.info("Created directory " + dir);
            } else if (!dir.isDirectory()) { // this is why we need synchronized.
                throw new IOException("Directory '" + dir + "' cannot be created");
            }
        }
    }

    public static void deleteFile(File f) throws IOException {
        try {
            // On Windows this can fail easily (e.g. read-only files will give an IO exception on delete).
            Files.delete(f.toPath());
        } catch (NoSuchFileException fileGone) {
            return; // File was already gone.
        } catch (IOException deleteFail) {
            // Old io-delete method has work-arounds for Windows odd behavior.
            if (!f.delete() && f.exists()) {
                throw deleteFail;
            }
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Deleted file " + f);
        }
    }

    public static File[] getFiles(File dir, List<String> allowedExtensions, List<String> excludedExtensions) {
        final List<String> allowExtensions = allowedExtensions;
        final List<String> excludeExtensions = excludedExtensions;

        return dir.listFiles(new FilenameFilter() {
            public boolean accept(File dir, String name) {
                String extension = name.substring(name.lastIndexOf(".") + 1);
                boolean isAllowed = true;
                if (!allowExtensions.isEmpty()) {
                    isAllowed = allowExtensions.contains(extension);
                    if (!isAllowed) {
                        return false;
                    }
                }
                // Check for the excluded filters
                if (!excludeExtensions.isEmpty()) {
                    isAllowed = !excludeExtensions.contains(extension);
                }
                return isAllowed;
            }
        });
    }


    @SuppressFBWarnings("NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE")
    public static String getSafeFilename(String proposedName) throws OpenAS2Exception {
        String outName = org.openas2.util.IOUtil.cleanFilename(proposedName).trim();
        /* Rule out a few well-known edge-cases before proceeding with other checks */
        if (outName.equals(".") || outName.equals("..") || outName.equals("")) {
            throw new InvalidMessageException("Unable to assign a valid filename for this system using message name <"
              + outName + "> (" + proposedName +")"
            );
        }
        /* Create path from proposed name */
        Path tmpPath = null;
        try {
          tmpPath = FileSystems.getDefault().getPath(outName);
        } catch (InvalidPathException ipe) {
            /* Invalid chars cannot be removed, reject filename and abort */
            String probWhere = "";
            if (ipe.getIndex() != -1) {
              probWhere = " Error in name at position " + ipe.getIndex();
            }
            InvalidMessageException im = new InvalidMessageException("Unable to assign a valid filename for this system using message name <"
              + outName + ">" + probWhere
            );
            im.initCause(ipe);
            throw im;
        }
        /* Check for directory path (e.g. C:\ or ../) embeded in filename */
        String bareName = tmpPath.getFileName().toString();
        if (!bareName.equalsIgnoreCase(outName)) {
          /* Possible path-traversal attack detected, reject filename and abort */
          InvalidMessageException im = new InvalidMessageException("Unable to use message filename containing directory path <"
            + outName + "> "
          );
          throw im;
        }
        return outName;
    }

}
