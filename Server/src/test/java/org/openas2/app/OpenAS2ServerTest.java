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
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
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
            int amountOfMessages = msgCnt;
            List<Callable<TestMessage>> callers = new ArrayList<Callable<TestMessage>>(amountOfMessages);

            // prepare messages
            for (int i = 0; i < amountOfMessages; i++) {
                callers.add(new Callable<TestMessage>() {
                    @Override
                    public TestMessage call() throws Exception {
                        return sendMessage(partnerA, partnerB);
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


    @Test
    public void shouldSendMessagesAsync() throws Exception {
        int amountOfMessages = msgCnt;
        List<Callable<TestMessage>> callers = new ArrayList<Callable<TestMessage>>(amountOfMessages);

        // prepare messages
        for (int i = 0; i < amountOfMessages; i++) {
            callers.add(new Callable<TestMessage>() {
                @Override
                public TestMessage call() throws Exception {
                    return sendMessage(partnerB, partnerA);
                }
            });

        }

        // send and verify all messages in parallel
        for (Future<TestMessage> result : executorService.invokeAll(callers)) {
            verifyMessageDelivery(result.get());
        }
    }

    @AfterClass
    public static void tearDown() throws Exception {
        //executorService.awaitTermination(100, TimeUnit.SECONDS);
        executorService.shutdown();
        partnerA.getServer().shutdown();
        partnerB.getServer().shutdown();
    }

    private TestMessage sendMessage(TestPartner fromPartner, TestPartner toPartner) throws IOException {
        String outgoingMsgFileName = RandomStringUtils.randomAlphanumeric(10) + ".txt";
        String outgoingMsgBody = RandomStringUtils.randomAlphanumeric(1024);
        File outgoingMsg = tmp.newFile(outgoingMsgFileName);
        FileUtils.write(outgoingMsg, outgoingMsgBody, "UTF-8");

        FileUtils.copyFileToDirectory(outgoingMsg, fromPartner.getOutbox());

        return new TestMessage(outgoingMsgFileName, outgoingMsgBody, fromPartner, toPartner);

    }

    private void verifyMessageDelivery(TestMessage testMessage) throws IOException {
        // wait till delivery occurs
        File deliveredMsg = waitForFile(testMessage.toPartner.getInbox(), new PrefixFileFilter(testMessage.fileName), 20, TimeUnit.SECONDS);

        {
            String deliveredMsgBody = FileUtils.readFileToString(deliveredMsg, "UTF-8");
            assertThat("Verify content of delivered message", deliveredMsgBody, is(testMessage.body));
        }

        {
            File deliveryConfirmationMDN = waitForFile(testMessage.fromPartner.getRxdMDNs(), new PrefixFileFilter(testMessage.fileName), 10, TimeUnit.SECONDS);
            assertThat("Verify MDN was received by " + testMessage.fromPartner.getName(), deliveryConfirmationMDN.exists(), is(true));
        }

        {
            File deliveryConfirmationMDN = waitForFile(testMessage.toPartner.getSentMDNs(), new PrefixFileFilter(testMessage.fileName), 10, TimeUnit.SECONDS);
            assertThat("Verify MDN was stored by " + testMessage.toPartner.getName(), deliveryConfirmationMDN.exists(), is(true));
        }
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
        for (Map.Entry<String, Object> pair : partners.entrySet()) {
            if (pair.getKey().equals(partnerB.getName())) {
                Map<String, String> partner = (Map<String, String>) pair.getValue();
                partnerB.setAs2Id(partner.get(Partnership.PID_AS2));
            } else if (pair.getKey().equals(partnerA.getName())) {
                Map<String, String> partner = (Map<String, String>) pair.getValue();
                partnerA.setAs2Id(partner.get(Partnership.PID_AS2));
            }
        }
        String partnershipFolderAtoB = partnerA.getAs2Id() + "-" + partnerB.getAs2Id();
        String partnershipFolderBtoA = partnerB.getAs2Id() + "-" + partnerA.getAs2Id();

        partnerA.setHome(RESOURCE.get(partnerA.getName()));
        partnerA.setOutbox(FileUtils.getFile(partnerA.getHome(), "data", "to" + partnerB.getName()));
        partnerA.setInbox(FileUtils.getFile(partnerA.getHome(), "data", partnershipFolderBtoA, "inbox"));
        partnerA.setSentMDNs(FileUtils.getFile(partnerA.getHome(), "data", partnershipFolderBtoA, "mdn", DateUtil.formatDate("yyyy-MM-dd")));
        partnerA.setRxdMDNs(FileUtils.getFile(partnerA.getHome(), "data", partnershipFolderAtoB, "mdn", DateUtil.formatDate("yyyy-MM-dd")));

        partnerB.setHome(RESOURCE.get(partnerB.getName()));
        partnerB.setOutbox(FileUtils.getFile(partnerB.getHome(), "data", "to" + partnerA.getName()));
        partnerB.setInbox(FileUtils.getFile(partnerB.getHome(), "data", partnershipFolderAtoB, "inbox"));
        partnerB.setSentMDNs(FileUtils.getFile(partnerB.getHome(), "data", partnershipFolderAtoB, "mdn", DateUtil.formatDate("yyyy-MM-dd")));
        partnerB.setRxdMDNs(FileUtils.getFile(partnerB.getHome(), "data", partnershipFolderBtoA, "mdn", DateUtil.formatDate("yyyy-MM-dd")));

    }

    private static void getPartnership() throws Exception {
        // Set Partner B to request ASYNC MDN
        PartnershipFactory pf = serverA.getSession().getPartnershipFactory();
        Partnership p = new Partnership();
        Partnership asyncPartnership = pf.getPartnership(p, false);
        if (asyncPartnership != null) {
            asyncPartnership.setAttribute(Partnership.PA_AS2_RECEIPT_OPTION, "http://localhost:20081");
        } else {
            throw new Exception("Could not set partnership to ~ASYNC mode");
        }

    }

    private static class TestMessage {
        private final String fileName;
        private final String body;
        private final TestPartner fromPartner, toPartner;

        private TestMessage(String fileName, String body, TestPartner fromPartner, TestPartner toPartner) {
            this.fileName = fileName;
            this.body = body;
            this.fromPartner = fromPartner;
            this.toPartner = toPartner;
        }
    }

}
