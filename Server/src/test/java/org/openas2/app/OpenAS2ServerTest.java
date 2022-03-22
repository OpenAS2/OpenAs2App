package org.openas2.app;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.PrefixFileFilter;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openas2.ComponentNotFoundException;
import org.openas2.TestPartner;
import org.openas2.TestResource;
import org.openas2.partner.Partnership;
import org.openas2.partner.PartnershipFactory;
import org.openas2.util.DateUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.openas2.TestUtils.waitForFile;

public class OpenAS2ServerTest {

    private static final TestResource RESOURCE = TestResource.forClass(OpenAS2ServerTest.class);

    private static TestPartner partnerA;
    private static TestPartner partnerB;
    private static OpenAS2Server serverA;
    private static OpenAS2Server serverB;
    private final int msgCnt = 2;

    private static ExecutorService executorService;
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @BeforeClass
    public static void startServers() throws Exception {
        //System.setProperty("org.openas2.logging.defaultlog", "TRACE");
        System.setProperty("org.apache.commons.logging.Log", "org.openas2.logging.Log");
        try {
            partnerA = new TestPartner("OpenAS2A");
            partnerB = new TestPartner("OpenAS2B");

            serverA = new OpenAS2Server.Builder().run(RESOURCE.get(partnerA.getName(), "config", "config.xml").getAbsolutePath());
            partnerA.setServer(serverA);

            serverB = new OpenAS2Server.Builder().run(RESOURCE.get(partnerB.getName(), "config", "config.xml").getAbsolutePath());
            partnerB.setServer(serverB);
            enhancePartners();
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
    public void shouldSendMessages() throws Exception {
        try {
            List<Callable<TestMessage>> callers = new ArrayList<Callable<TestMessage>>(msgCnt);

            // write messages to outbox and build callables with test message objects
            for (int i = 0; i < msgCnt; i++) {
            	TestMessage testMsg = sendMessage(partnerA, partnerB);
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
        } catch (Throwable e) {
            // Aid debugging JUnit test failures
            System.out.println("ERROR OCCURRED: " + ExceptionUtils.getStackTrace(e));
            throw new Exception(e);
        }
    }

    /*
    @Test
    public void shouldSendMessagesAsync() throws Exception {
        try {
            List<Callable<TestMessage>> callers = new ArrayList<Callable<TestMessage>>(msgCnt);

            // write messages to outbox and build callables with test message objects
            for (int i = 0; i < msgCnt; i++) {
            	TestMessage testMsg = sendMessage(partnerB, partnerA);
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
        } catch (Throwable e) {
            // Aid debugging JUnit test failures
            System.out.println("ERROR OCCURRED: " + ExceptionUtils.getStackTrace(e));
            throw new Exception(e);
        }
    }
    */

    @AfterClass
    public static void tearDown() throws Exception {
        //executorService.awaitTermination(15, TimeUnit.SECONDS);
        executorService.shutdown();
        String dataDirPartnerA = partnerA.getHome().getAbsolutePath() + File.separator + "data";
        String dataDirPartnerB = partnerB.getHome().getAbsolutePath() + File.separator + "data";
        partnerA.getServer().shutdown();
        partnerB.getServer().shutdown();
        // NOTE: For debugging "missing" files it is best to comment this out
        try {
			FileUtils.deleteDirectory(new File(dataDirPartnerA));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        try {
			FileUtils.deleteDirectory(new File(dataDirPartnerB));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }

    private TestMessage sendMessage(TestPartner fromPartner, TestPartner toPartner) throws IOException {
        String outgoingMsgFileName = RandomStringUtils.randomAlphanumeric(10) + ".txt";
        String outgoingMsgBody = RandomStringUtils.randomAlphanumeric(1024);
        File outgoingMsg = tmp.newFile(outgoingMsgFileName);
        FileUtils.write(outgoingMsg, outgoingMsgBody, "UTF-8");

        FileUtils.copyFileToDirectory(outgoingMsg, fromPartner.getOutbox());
    	//System.out.println("**** ****   FILE COPIED: " + fromPartner.getOutbox() + "/" + outgoingMsg.getName());

        return new TestMessage(outgoingMsgFileName, outgoingMsgBody, fromPartner, toPartner);

    }

    private TestMessage getDeliveredMessage(TestMessage testMessage) throws IOException {
        // Wait a while - will depend on the sender poller interval how long it takes to arrive
    	testMessage.deliveredMsg = waitForFile(testMessage.toPartner.getInbox(), new PrefixFileFilter(testMessage.fileName), 20, TimeUnit.SECONDS);
    	return testMessage;
    }

    private void verifyMessageDelivery(TestMessage testMessage) throws IOException {
    	assertThat("A file was received by " + testMessage.toPartner.getName() + " from " + testMessage.fromPartner.getName(),  testMessage.deliveredMsg != null, is(true));
        String deliveredMsgBody = FileUtils.readFileToString(testMessage.deliveredMsg, "UTF-8");
        assertThat("Verify content of delivered message", deliveredMsgBody, is(testMessage.body));

        File rxdMDN = waitForFile(testMessage.toPartner.getRxdMDNs(), new PrefixFileFilter(testMessage.fileName), 10, TimeUnit.SECONDS);
        assertThat("Verify MDN was received by " + testMessage.toPartner.getName(), rxdMDN.exists(), is(true));

        File sentMDN = waitForFile(testMessage.fromPartner.getSentMDNs(), new PrefixFileFilter(testMessage.fileName), 10, TimeUnit.SECONDS);
        assertThat("Verify sent MDN was stored by " + testMessage.fromPartner.getName(), sentMDN.exists(), is(true));
    }

    /**
     * Add additional attributes to partner objects.
     *
     * @throws ComponentNotFoundException
     * @throws FileNotFoundException
     */
    // TODO:  Should try to extract more of them from config to help make tests less brittle
    private static void enhancePartners() throws ComponentNotFoundException, FileNotFoundException {
        PartnershipFactory pf = serverA.getSession().getPartnershipFactory();
        Map<String, Object> partners = pf.getPartners();
        for (Map.Entry<String, Object> attribute : partners.entrySet()) {
            if (attribute.getKey().equals(partnerB.getName())) {
                Map<String, String> partner = (Map<String, String>) attribute.getValue();
                partnerB.setAs2Id(partner.get(Partnership.PID_AS2));
            } else if (attribute.getKey().equals(partnerA.getName())) {
                Map<String, String> partner = (Map<String, String>) attribute.getValue();
                partnerA.setAs2Id(partner.get(Partnership.PID_AS2));
            }
        }
        String partnershipFolderAtoB = partnerA.getAs2Id() + "-" + partnerB.getAs2Id();
        String partnershipFolderBtoA = partnerB.getAs2Id() + "-" + partnerA.getAs2Id();

        partnerA.setHome(RESOURCE.get(partnerA.getName()));
        partnerA.setOutbox(FileUtils.getFile(partnerA.getHome(), "data", "to" + partnerB.getName()));
        partnerA.setInbox(FileUtils.getFile(partnerA.getHome(), "data", partnershipFolderBtoA, "inbox"));
        partnerA.setSentMDNs(FileUtils.getFile(partnerA.getHome(), "data", partnershipFolderAtoB, "mdn", DateUtil.formatDate("yyyy-MM-dd")));
        partnerA.setRxdMDNs(FileUtils.getFile(partnerA.getHome(), "data", partnershipFolderBtoA, "mdn", DateUtil.formatDate("yyyy-MM-dd")));

        partnerB.setHome(RESOURCE.get(partnerB.getName()));
        partnerB.setOutbox(FileUtils.getFile(partnerB.getHome(), "data", "to" + partnerA.getName()));
        partnerB.setInbox(FileUtils.getFile(partnerB.getHome(), "data", partnershipFolderAtoB, "inbox"));
        partnerB.setSentMDNs(FileUtils.getFile(partnerB.getHome(), "data", partnershipFolderBtoA, "mdn", DateUtil.formatDate("yyyy-MM-dd")));
        partnerB.setRxdMDNs(FileUtils.getFile(partnerB.getHome(), "data", partnershipFolderAtoB, "mdn", DateUtil.formatDate("yyyy-MM-dd")));

    }

    @SuppressWarnings("unused")
	private Partnership getPartnership(OpenAS2Server servicerInstance, String senderAS2Id, String receiverAS2Id) throws Exception {
        PartnershipFactory pf = servicerInstance.getSession().getPartnershipFactory();
        Map<String, Object> senderIDs = new HashMap<String, Object>();
        senderIDs.put(Partnership.PID_AS2, senderAS2Id);
        Map<String, Object> receiverIDs = new HashMap<String, Object>();
        receiverIDs.put(Partnership.PID_AS2, receiverAS2Id);
        return pf.getPartnership(senderIDs, receiverIDs);
	}

    @SuppressWarnings("unused")
    private void setPartnershipToAsync(Partnership partnership) throws Exception {
        if (partnership != null) {
            partnership.setAttribute(Partnership.PA_AS2_RECEIPT_OPTION, "http://localhost:20081");
        } else {
            throw new Exception("Could not set partnership to ~ASYNC mode");
        }
    }

    @SuppressWarnings("unused")
    private void enableSentMDNStorage(Partnership partnership) throws Exception {
        if (partnership != null) {
            partnership.setAttribute(Partnership.PA_AS2_RECEIPT_OPTION, "http://localhost:20081");
        } else {
            throw new Exception("Could not set partnership to ~ASYNC mode");
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
