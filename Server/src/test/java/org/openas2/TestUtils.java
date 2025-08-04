package org.openas2;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
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
    public static File waitForFile(File dir, String fileNameSubstr, int timeout, TimeUnit unit) throws FileNotFoundException {
        long finishAt = System.currentTimeMillis() + unit.toMillis(timeout);
        FilenameFilter subStringFilter = (d, s) -> {
            return s.contains(fileNameSubstr);
        };
        while (finishAt - System.currentTimeMillis() > 0) {
            String[] files = dir.list(subStringFilter);
            if (files.length == 1) {
                return new File(dir.getPath() + File.separator + files[0]);
            } else if (files.length > 1) {
                    throw new IllegalStateException("Result is not unique.");
            }
        }
        throw new FileNotFoundException("Directory: " + dir.getAbsolutePath() + " File Name Substring: " + fileNameSubstr);
    }

    public static boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }
}
