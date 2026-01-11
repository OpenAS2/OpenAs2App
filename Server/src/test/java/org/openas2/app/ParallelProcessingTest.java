package org.openas2.app;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openas2.TestPartner;
import org.openas2.TestResource;
import org.openas2.TestUtils;
import org.openas2.XMLSession;
import org.openas2.processor.ActiveModule;
import org.openas2.processor.ProcessorModule;
import org.openas2.processor.receiver.AS2DirectoryPollingModule;
import org.openas2.processor.receiver.DirectoryPollingModule;
import org.openas2.processor.storage.MessageFileModule;
import org.openas2.processor.storage.StorageModule;
import org.openas2.util.Properties;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.openas2.TestUtils.waitTillAllFilesReceived;


public class ParallelProcessingTest {


    private static TestPartner server1PartnerSender;
    private static TestPartner server2PartnerReceiver;
    private static OpenAS2Server server1;
    private static OpenAS2Server server2;
    private static String[] dataFolders = new String[2];
    private final int msgCnt = 1000;

    @TempDir
    public static File resourceTmp;
    @TempDir
    public static File configTmp;

    @BeforeAll
    public static void startServers() throws Exception {
        resourceTmp = Files.createTempDirectory("testResources").toFile();
        //System.setProperty("OPENAS2_LOG_LEVEL", "TRACE");
        try {
            String configFile = TestResource.getResource("config");
            System.setProperty("SERVER1_PARTNERSHIP_FILE", TestResource.getResource("server1-partnerships"));
            String propsSrcFile = TestResource.getResource("server1-props");
            String props1File = configTmp.getAbsolutePath() + File.separator + "parallel_test.properties";
            Path propsPath = Paths.get(props1File);
            Files.copy(Paths.get(propsSrcFile), propsPath);
            String[] newProps = {"pollerConfigBase.process_files_in_parallel=true"};
            TestUtils.appendLinesToFile(propsPath, newProps);
            System.setProperty(Properties.OPENAS2_PROPERTIES_FILE_PROP, props1File);
            server1 = new OpenAS2Server.Builder().run(configFile);
            // Get the data folder from Properties before starting the other server as it overwrites the Properties
            dataFolders[0] = Properties.getProperty("storageBaseDir", null);
            // Iterate over the partnerships picking the first one that has a directory poller
            server1PartnerSender = TestUtils.getFromFirstSendingPartnership(server1);

            System.setProperty("SERVER2_PARTNERSHIP_FILE", TestResource.getResource("server2-partnerships"));
            String props2File = TestResource.getResource("server2-props");
            System.setProperty(Properties.OPENAS2_PROPERTIES_FILE_PROP, props2File);
            server2 = new OpenAS2Server.Builder().run(configFile);
            // Set up the receiver server B to receive files from server A
            server2PartnerReceiver = TestUtils.getFromPartnerIds(server2, server1PartnerSender.getAs2Id(), server1PartnerSender.getPartnerAS2Id());
            dataFolders[1] = Properties.getProperty("storageBaseDir", null);
        } catch (FileNotFoundException e) {
            System.err.println("Failed to retrieve resource for test: " + e.getMessage());
            e.printStackTrace();
            throw new Exception(e);
        } catch (Throwable e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new Exception(e);
        }
    }

    @Test
    public void shouldSendMessagesInParallel() throws Exception {
        try {
            createMessages(msgCnt);
            sendAllMessages(server1PartnerSender, server2PartnerReceiver);
            int rxdCount = getDeliveredMessagesCount(msgCnt);
            assertThat("Received number of files", rxdCount, Matchers.equalTo(msgCnt));
        } catch (Throwable e) {
            // Aid debugging JUnit test failures
            System.out.println("shouldSendMessagesSyncMdn ERROR OCCURRED: " + ExceptionUtils.getStackTrace(e));
            throw new Exception(e);
        }
    }

    @Test
    public void shouldBeParallelMode() throws Exception {
        XMLSession session = (XMLSession)server1.getSession();
        DirectoryPollingModule pollingModule = session.getPartnershipPoller(server1PartnerSender.getPartnership().getName());
        String modeSetting = pollingModule.getParameter(DirectoryPollingModule.PARAM_PROCESS_IN_PARALLEL, "false");
        assertThat("Directory polling module running in parallel mode", "true", Matchers.equalTo(modeSetting));
    }

    @AfterAll
    public static void tearDown() throws Exception {
        server1PartnerSender.getServer().shutdown();
        server2PartnerReceiver.getServer().shutdown();
        // Cleanup the folders created so the test does not fail next time round from leftover data
        // NOTE: For debugging "missing" files it is best to comment this out
        for (int i = 0; i < dataFolders.length; i++) {
            try {
                FileUtils.deleteDirectory(new File(dataFolders[i]));
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    private void createMessages(int tgtCount) throws IOException {
        for (int i = 0; i < tgtCount; i++) {
            String outgoingMsgFileName = RandomStringUtils.secure().nextAlphanumeric(10) + ".txt";
            String outgoingMsgBody = RandomStringUtils.secure().nextAlphanumeric(1024);
            File msgfile = Files.createFile(Paths.get(resourceTmp.toString(), outgoingMsgFileName)).toFile();
            FileUtils.write(msgfile, outgoingMsgBody, "UTF-8");
        }
    }

    private void sendAllMessages(TestPartner fromPartner, TestPartner toPartner) throws IOException {
        File[] files = resourceTmp.listFiles();
        System.out.println("Moving " + files.length + " files to outbox for sending to:" + fromPartner.getOutbox());
        for(int i = 0; i < files.length; i++) {
            FileUtils.moveFileToDirectory(files[i], fromPartner.getOutbox(), false);
        }
    }

    private int getDeliveredMessagesCount(int expectedCnt) throws IOException {
        return waitTillAllFilesReceived(server2PartnerReceiver.getInbox(), expectedCnt, 45, TimeUnit.SECONDS);
    }

}
