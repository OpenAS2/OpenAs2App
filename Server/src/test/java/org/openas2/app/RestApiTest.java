package org.openas2.app;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.mail.Header;
import javax.mail.internet.InternetHeaders;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.openas2.TestResource;
import org.openas2.util.HTTPUtil;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertThat;



@RunWith(MockitoJUnitRunner.class)
/**
 *
 * @author javier
 */
public class RestApiTest {
    private static final TestResource RESOURCE = TestResource.forGroup("SingleServerTest");
    // private static File openAS2AHome;
    private static OpenAS2Server serverInstance;
    private static ExecutorService executorService;
    private static TemporaryFolder scratchpad = new TemporaryFolder();

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @BeforeClass
    public static void startServer() throws Exception {
	// Set up some override properties so we can use the standard config in tests
	// to make sure the release package is fully tested
	scratchpad.create();
	File customPropsFile = scratchpad.newFile("openas2.properties");
	System.setProperty("openas2.properties.file", customPropsFile.getAbsolutePath());
	FileOutputStream fos = new FileOutputStream(customPropsFile);
	fos.write("restapi.command.processor.enabled=true\n".getBytes());
	fos.close();

	try {
	    System.setProperty("org.apache.commons.logging.Log", "org.openas2.logging.Log");
	    // System.setProperty("org.openas2.logging.defaultlog", "TRACE");
//	    executorService = Executors.newFixedThreadPool(20);

	    RestApiTest.serverInstance = new OpenAS2Server.Builder()
		    .run(RESOURCE.get("MyCompany", "config", "config.xml").getAbsolutePath());
	} catch (Throwable e) {
	    // aid for debugging JUnit tests
	    System.err.println("ERROR occurred: " + ExceptionUtils.getStackTrace(e));
	    throw new Exception(e);
	}
    }
    
    @BeforeClass
    public static void startClient() throws Exception {
        
    }

    @AfterClass
    public static void tearDown() throws Exception {
	serverInstance.shutdown();
//	executorService.shutdown();
	System.clearProperty("openas2.properties.file");
	scratchpad.delete();
    }

    @Test
    public void shouldRespondWithVersion() throws Exception {
        String url = "http://127.0.0.1:8080/api";
        InternetHeaders headers = new InternetHeaders();
        Map<String,String> options = new  HashMap<String,String>();
        options.put(HTTPUtil.PARAM_CONNECT_TIMEOUT, "35");
        options.put(HTTPUtil.PARAM_READ_TIMEOUT, "15");
        options.put(HTTPUtil.PARAM_SOCKET_TIMEOUT,"15");
        options.put(HTTPUtil.PARAM_HTTP_USER, "userID");
        options.put(HTTPUtil.PARAM_HTTP_PWD, "pWd");
        byte[] buffer=new byte[1024];
        ByteArrayInputStream inputStream = new ByteArrayInputStream(buffer);
        HTTPUtil.execRequest("GET", url, headers.getAllHeaders(), null, inputStream, options, 0);
        assertThat("Getting API version and server info", new String(buffer), containsString(serverInstance.getSession().getAppTitle()));
    }
}
