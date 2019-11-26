package org.openas2;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.net.URISyntaxException;
import java.net.URL;

/**
 * A helper class for locating files and directories on the file systems.
 * The main reason is to categorize static files used in tests.
 */
public class TestResource {

    /**
     * An absolute path to a test specific resource.
     */
    private final String pathPrefix;

    private TestResource(String clazzSimpleName) {
        try {
            URL resource = Thread.currentThread().getContextClassLoader().getResource(".");
            this.pathPrefix = new File(resource.toURI()).getAbsolutePath() + File.separator + clazzSimpleName;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public static TestResource forClass(Class<?> clazz) {
        return new TestResource(clazz.getSimpleName());
    }

    public static TestResource forGroup(String group) {
        return new TestResource(group);
    }

    /**
     * Get a file or directory within {@link #pathPrefix}
     *
     * @param fileName a file or directory name
     * @param child    a children name
     * @return a file
     */
    public File get(String fileName, String... child) throws FileNotFoundException {
        File file = new File(pathPrefix + File.separator + fileName + File.separator + StringUtils.join(child, File.separator));
        if (!file.exists()) {
            throw new FileNotFoundException(file.getAbsolutePath());
        }
        return file;
    }
}
