package org.openas2.app;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openas2.TestResource;
import org.openas2.TestUtils;
import org.openas2.processor.ActiveModule;
import org.openas2.processor.receiver.NetModule;
import org.openas2.util.Properties;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ExtendWith(MockitoExtension.class)
public class HealthCheckTest {
    // private static File openAS2AHome;
    private static OpenAS2Server serverInstance;
    private static ExecutorService executorService;
    @TempDir
    private static Path scratchpad;

    @BeforeAll
    public static void startServer() throws Exception {
        // Set up some override properties so we can use the standard config in tests
        // to make sure the release package is fully tested
    	scratchpad = Files.createTempDirectory("testResources");
        File customPropsFile = Files.createFile(Paths.get(scratchpad.toString(), "openas2.properties")).toFile();
        System.setProperty(Properties.OPENAS2_PROPERTIES_FILE_PROP, customPropsFile.getAbsolutePath());
        FileOutputStream fos = new FileOutputStream(customPropsFile);
        fos.write("module.HealthCheckModule.enabled=true\n".getBytes());
        fos.close();

        try {
            // System.setProperty("OPENAS@_LOG_LEVEL", "TRACE");
            executorService = Executors.newFixedThreadPool(20);

            HealthCheckTest.serverInstance = new OpenAS2Server.Builder().run(TestResource.getResource("config"));
        } catch (Throwable e) {
            // aid for debugging JUnit tests
            System.err.println("ERROR occurred: " + ExceptionUtils.getStackTrace(e));
            throw new Exception(e);
        }
    }

    @AfterAll
    public static void tearDown() throws Exception {
        serverInstance.shutdown();
        executorService.shutdown();
        System.clearProperty("openas2.properties.file");
        TestUtils.deleteDirectory(scratchpad.toFile());
    }

    @Test
    public void shouldRespondToHealthCheckOnNetModule() throws Exception {
        Class<NetModule> listenerClass = NetModule.class;
        List<ActiveModule> listeners = serverInstance.getSession().getProcessor().getActiveModulesByClass(listenerClass);
        for (ActiveModule listener : listeners) {
            List<String> failures = new ArrayList<String>();
            NetModule m = (NetModule) listener;
            assertThat("Verify healthcheck URI is working on active NetModule instance", m.healthcheck(failures), is(true));
        }
    }

}
