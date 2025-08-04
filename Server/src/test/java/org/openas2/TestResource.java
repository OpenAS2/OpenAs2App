package org.openas2;

import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Retrieves resources used for test purposes from the file system
 * 
 */
public class TestResource {
    /* Set up a map of resource files that can be generically used by all tests
     * and some test specific property files that override the default behaviour
     * from the standard config.xml file such as for tests using 2 AS2 server instances.
     */
    public static final Map<String, String[]> resources = Map.of(
        "config", new String[]{"config", "config.xml"},
        "partnerships", new String[]{"config", "partnerships.xml"},
        "server1-props", new String[]{"custom", "server1.properties"},
        "server1-partnerships", new String[]{"custom", "server1_partnerships.xml"},
        "server2-props", new String[]{"custom", "server2.properties"},
        "server2-partnerships", new String[]{"custom", "server2_partnerships.xml"},
        "api-server-props", new String[]{"custom", "api-server.properties"}
    );

    static String pathPrefix = Paths.get("src","test","resources").toAbsolutePath().toString();


    public static String getResource(String resourceIdentifier) throws FileNotFoundException {
        /**
         * Get absolute path to a file identified by the resource identifier passed in.
         *
         * @param resourceIdentifier - an identifier matching one of the keys in this classes resources map
         * @return a file
         */
        String[] resourceAttributes = resources.get(resourceIdentifier);
        String filePath = get(resourceAttributes);
        
        return filePath;
    }

    /**
     * Get the absolute path to a file or directory within {@link #resourceBaseFolder}
     *
     * @param foldersAndFile - a list of optional folders in path with the actual file name as the last in the list
     * @return a file
     */
    public static String get(String... foldersAndFile) throws FileNotFoundException {
        String filePath = pathPrefix + File.separator + StringUtils.join(foldersAndFile, File.separator);
        File file = new File(filePath);
        if (!file.exists()) {
            throw new FileNotFoundException(filePath);
        }
        return filePath;
    }

    /**
     * Get a File object for a file or directory within {@link #resourceBaseFolder}
     *
     * @param foldersAndFile - a list of optional folders in path with the actual file name as the last in the list
     * @return a File object
     */
    public static File getFile(String... foldersAndFile) throws FileNotFoundException {
        return new File(get(foldersAndFile));
    }
}
