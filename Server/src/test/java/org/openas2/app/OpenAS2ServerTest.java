package org.openas2.app;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.PrefixFileFilter;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openas2.ComponentNotFoundException;
import org.openas2.TestPartner;
import org.openas2.TestResource;
import org.openas2.XMLSession;
import org.openas2.partner.Partnership;
import org.openas2.partner.PartnershipFactory;
import org.openas2.processor.receiver.DirectoryPollingModule;
import org.openas2.util.Properties;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.openas2.TestUtils.waitForFile;

public class OpenAS2ServerTest {


    private static TestPartner server1PartnerSender;
    private static TestPartner server2PartnerReceiver;
    private static TestPartner server2PartnerSender;
    private static TestPartner server1PartnerReceiver;
    private static OpenAS2Server server1;
    private static OpenAS2Server server2;
    private static String[] dataFolders = new String[2];
    private final int msgCnt = 2;

    private static ExecutorService executorService;
    @TempDir
    public static File tmp;

    @BeforeAll
    public static void startServers() throws Exception {
        tmp = Files.createTempDirectory("testResources").toFile();
        //System.setProperty("OPENAS2_LOG_LEVEL", "TRACE");
        try {
            String configFile = TestResource.getResource("config");
            System.setProperty("SERVER1_PARTNERSHIP_FILE", TestResource.getResource("server1-partnerships"));
            String props1File = TestResource.getResource("server1-props");
            System.setProperty(Properties.OPENAS2_PROPERTIES_FILE_PROP, props1File);
            server1 = new OpenAS2Server.Builder().run(configFile);
            // Get the data folder from Properties before starting the other server as it overwrites the Properties
            dataFolders[0] = Properties.getProperty("storageBaseDir", null);
            // Iterate over the partnerships picking the first one that has a directory poller
            server1PartnerSender = getFromFirstSendingPartnership(server1);
            // Use the 2 partners in the initial partnership to get other parnerships to test both way transfer
            // Get a receiver partnership for the matching partners in the sender partnership for server A
            server1PartnerReceiver = getFromPartnerIds(server1, server1PartnerSender.getPartnerAS2Id(), server1PartnerSender.getAs2Id());

            System.setProperty("SERVER2_PARTNERSHIP_FILE", TestResource.getResource("server2-partnerships"));
            String props2File = TestResource.getResource("server2-props");
            System.setProperty(Properties.OPENAS2_PROPERTIES_FILE_PROP, props2File);
            server2 = new OpenAS2Server.Builder().run(configFile);
            // Set up the receiver fin server B for the sender from server A
            server2PartnerReceiver = getFromPartnerIds(server2, server1PartnerSender.getAs2Id(), server1PartnerSender.getPartnerAS2Id());
            // Get a sender partnership for the matching partners in the receiver partnership for server B
            server2PartnerSender = getFromPartnerIds(server2, server1PartnerSender.getPartnerAS2Id(), server1PartnerSender.getAs2Id());
            dataFolders[1] = Properties.getProperty("storageBaseDir", null);
            executorService = Executors.newFixedThreadPool(20);
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
    public void shouldSendMessagesSyncMdn() throws Exception {
        try {
            sendMessages(server1PartnerSender, server2PartnerReceiver);
        } catch (Throwable e) {
            // Aid debugging JUnit test failures
            System.out.println("shouldSendMessagesSyncMdn ERROR OCCURRED: " + ExceptionUtils.getStackTrace(e));
            throw new Exception(e);
        }
    }

    @Test
    public void shouldSendMessagesAsyncMdn() throws Exception {
        try {
            sendMessages(server2PartnerSender, server1PartnerReceiver);
        } catch (Throwable e) {
            // Aid debugging JUnit test failures
            System.out.println("shouldSendMessagesAsyncMdn ERROR OCCURRED: " + ExceptionUtils.getStackTrace(e));
            throw new Exception(e);
        }
    }

    public void sendMessages(TestPartner sender, TestPartner receiver) throws Exception {
        List<Callable<TestMessage>> callers = new ArrayList<Callable<TestMessage>>(msgCnt);

        // write messages to outbox and build callables with test message objects
        for (int i = 0; i < msgCnt; i++) {
            TestMessage testMsg = sendMessage(sender, receiver);
            callers.add(new Callable<TestMessage>() {
                @Override
                public TestMessage call() throws Exception {
                    return getDeliveredMessage(testMsg);
                }
            });
        }
        // send and verify all messages in parallel
        for (Future<TestMessage> result : executorService.invokeAll(callers)) {
            verifyMessageDelivery(result.get());
        }
    }

    @AfterAll
    public static void tearDown() throws Exception {
        //executorService.awaitTermination(15, TimeUnit.SECONDS);
        executorService.shutdown();
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

    private TestMessage sendMessage(TestPartner fromPartner, TestPartner toPartner) throws IOException {
        String outgoingMsgFileName = RandomStringUtils.secure().nextAlphanumeric(10) + ".txt";
        String outgoingMsgBody = RandomStringUtils.secure().nextAlphanumeric(1024);
        File outgoingMsg = Files.createFile(Paths.get(tmp.toString(), outgoingMsgFileName)).toFile();
        FileUtils.write(outgoingMsg, outgoingMsgBody, "UTF-8");
        System.out.println("Copying " + outgoingMsg.getName() + " file for sending to:" + fromPartner.getOutbox());
        FileUtils.copyFileToDirectory(outgoingMsg, fromPartner.getOutbox());
        //System.out.println("**** ****   FILE COPIED: " + fromPartner.getOutbox() + "/" + outgoingMsg.getName());

        return new TestMessage(outgoingMsgFileName, outgoingMsgBody, fromPartner, toPartner);

    }

    private TestMessage getDeliveredMessage(TestMessage testMessage) throws IOException {
        // Wait a while - will depend on the sender poller interval how long it takes to arrive
        // System.out.println("**** **** INBOX AWAITING FILE: " + testMessage.toPartner.getInbox().getAbsolutePath() + "/***-" + testMessage.fileName);
        testMessage.deliveredMsg = waitForFile(testMessage.toPartner.getInbox(), testMessage.fileName, 20, TimeUnit.SECONDS);
        return testMessage;
    }

    private void verifyMessageDelivery(TestMessage testMessage) throws IOException {
        assertThat("A file was received by " + testMessage.toPartner.getName() + " from " + testMessage.fromPartner.getName(),  testMessage.deliveredMsg != null, is(true));
        String deliveredMsgBody = FileUtils.readFileToString(testMessage.deliveredMsg, "UTF-8");
        assertThat("Verify content of delivered message", deliveredMsgBody, is(testMessage.body));

        File rxdMDN = waitForFile(testMessage.toPartner.getRxdMDNs(), testMessage.fileName, 10, TimeUnit.SECONDS);
        assertThat("Verify MDN was received by " + testMessage.toPartner.getName(), rxdMDN.exists(), is(true));

        File sentMDN = waitForFile(testMessage.fromPartner.getSentMDNs(), testMessage.fileName, 10, TimeUnit.SECONDS);
        assertThat("Verify sent MDN was stored by " + testMessage.fromPartner.getName(), sentMDN.exists(), is(true));
    }

    /**
     * Finds the first partnership in the list for the server instance that has a directory poller and builds a TestPartner object using that
     * @param server - the instance of an OpenAS2 server
     * @return - a TestPartner instance based on the partnership found
     * @throws Exception
     */
    private static TestPartner getFromFirstSendingPartnership(OpenAS2Server server) throws Exception {
        PartnershipFactory pf = server.getSession().getPartnershipFactory();
        List<Partnership> partnerships = pf.getPartnerships();
        for (Iterator<Partnership> iterator = partnerships.iterator(); iterator.hasNext();) {
            Partnership partnership = (Partnership) iterator.next();
            DirectoryPollingModule pollerModule = getPollingModule((XMLSession) server.getSession(), partnership);
            if (pollerModule != null) {
                return new TestPartner(server, partnership, pollerModule);
            }
        }
        return null;
    }

    private static TestPartner getFromPartnerIds(OpenAS2Server server, String senderAs2Id, String receiverAs2Id) throws Exception {
        PartnershipFactory pf = server.getSession().getPartnershipFactory();
        List<Partnership> partnerships = pf.getPartnerships();
        for (Iterator<Partnership> iterator = partnerships.iterator(); iterator.hasNext();) {
            Partnership partnership = (Partnership) iterator.next();
            if (senderAs2Id.equals(partnership.getSenderID(Partnership.PID_AS2)) && receiverAs2Id.equals(partnership.getReceiverID(Partnership.PID_AS2))) {
                DirectoryPollingModule pollerModule = getPollingModule((XMLSession) server.getSession(), partnership);
                return new TestPartner(server, partnership, pollerModule);
            }
        }
        return null;
    }

    private static DirectoryPollingModule getPollingModule(XMLSession session, Partnership partnership) throws ComponentNotFoundException {
        DirectoryPollingModule dirPollMod = session.getPartnershipPoller(partnership.getName());
        if (dirPollMod != null) {
            return dirPollMod;
        }
        // Try to find a module defined poller since there is no matching poller by name. (config.xml defined pollers do not have the correct partnership name in the poller cache)
        return session.getPartnershipPoller(partnership.getSenderID(Partnership.PID_AS2), partnership.getReceiverID(Partnership.PID_AS2));        
    }

    @SuppressWarnings("unused")
    private void setPartnershipToAsync(Partnership partnership) throws Exception {
        if (partnership != null) {
            partnership.setAttribute(Partnership.PA_AS2_RECEIPT_OPTION, "http://localhost:20081");
        } else {
            throw new Exception("Could not set partnership to ASYNC mode");
        }
    }

    private static class TestMessage {
        private final String fileName;
        private final String body;
        private final TestPartner fromPartner, toPartner;
        @SuppressWarnings("unused")
        public File deliveredMsg = null;

        private TestMessage(String fileName, String body, TestPartner fromPartner, TestPartner toPartner) {
            this.fileName = fileName;
            this.body = body;
            this.fromPartner = fromPartner;
            this.toPartner = toPartner;
        }
    }

}
