package org.openas2;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.IOFileFilter;
import org.apache.commons.io.filefilter.TrueFileFilter;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

/**
 * Utilities for tests
 */
public class TestUtils {

    /**
     * Wait till a file will occur on the file system.
     *
     * @param parent     a directory where file will be.
     * @param fileFilter a filter to scan the parent dir.
     * @param timeout    an amount of time units to wait
     * @param unit       a time unit
     * @return a file
     * @throws FileNotFoundException
     */
    public static File waitForFile(File parent, IOFileFilter fileFilter, int timeout, TimeUnit unit) throws FileNotFoundException {
        long finishAt = System.currentTimeMillis() + unit.toMillis(timeout);
        waitForFile(parent, timeout, unit);
        while (finishAt - System.currentTimeMillis() > 0) {
            Collection<File> files = FileUtils.listFiles(parent, fileFilter, TrueFileFilter.INSTANCE);
            if (!files.isEmpty()) {
                if (files.size() > 1) {
                    throw new IllegalStateException("Result is not unique.");
                } else {
                    return files.iterator().next();
                }
            }
        }
        throw new FileNotFoundException(parent.getAbsolutePath() + ": " + fileFilter.toString());
    }

    /**
     * Wait till file will occur on the file system.
     *
     * @param file    a file
     * @param timeout an amount of time units to wait
     * @param unit    a time unit
     */
    public static void waitForFile(File file, int timeout, TimeUnit unit) {
        FileUtils.waitFor(file, Long.valueOf(unit.toSeconds(timeout)).intValue());
    }

}
