/* Copyright Uhuru Technology 2016 https://www.uhurutechnology.com
 * Distributed under the GPLv3 license or a commercial license must be acquired.
 */
package org.openas2.processor.receiver;

import java.io.File;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.util.FileUtil;
import org.openas2.util.IOUtil;

public class FileSplitter implements Runnable {
    private File sourceFile;
    private String outputDir;
    private long maxFileSize;
    private boolean containsHeaderRow;
    private String newFileBaseName;
    private String filenamePrefix;

    private static final Logger logger = LoggerFactory.getLogger(FileUtil.class);

    public FileSplitter(File sourceFile, String outputDir, long maxFileSize, boolean containsHeaderRow, String newFileBaseName, String filenamePrefix) {
       this.sourceFile = sourceFile;
       this.outputDir = outputDir;
       this.maxFileSize = maxFileSize;
       this.containsHeaderRow = containsHeaderRow;
       this.newFileBaseName = newFileBaseName;
       this.filenamePrefix = filenamePrefix;
    }

    public void run(){
        if (logger.isDebugEnabled()) {
            logger.debug("File splitter thread invoked for file: " + this.sourceFile.getAbsolutePath());
        }
        try {
            FileUtil.splitLineBasedFile(this.sourceFile, this.outputDir, this.maxFileSize, this.containsHeaderRow, this.newFileBaseName, this.filenamePrefix);
            if (logger.isDebugEnabled()) {
                logger.debug("Successfully split the file: " + this.sourceFile.getAbsolutePath());
            }
            // Must have been successful so remove the original file
            try {
                IOUtil.deleteFile(sourceFile);
            } catch (IOException e) {
                throw new OpenAS2Exception("Failed to delete file after split processing: " + sourceFile.getAbsolutePath(), e);
            }
        } catch (OpenAS2Exception e) {
            logger.error("Failed to successfully split the file: " + this.sourceFile.getAbsolutePath() + " - " + e.getMessage(), e);
        }
    }
}
