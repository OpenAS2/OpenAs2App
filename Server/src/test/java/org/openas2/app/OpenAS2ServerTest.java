package org.openas2.app;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.PrefixFileFilter;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.openas2.TestResource;
import org.openas2.util.DateUtil;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.openas2.TestUtils.waitForFile;

public class OpenAS2ServerTest {

    private static final TestResource RESOURCE = TestResource.forClass(OpenAS2ServerTest.class);
    private static File openAS2AHome;
    private static File openAS2AOutbox;
    private static File openAS2AMDNs;
    private static File openAS2BHome;
    private static File openAS2BInbox;
    private static File openAS2BMDNs;
    private static OpenAS2Server openAS2A;
    private static OpenAS2Server openAS2B;
    private static ExecutorService executorService;
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @BeforeClass
    public static void startServers() throws Exception
    {
        executorService = Executors.newFixedThreadPool(20);
        System.setProperty("org.apache.commons.logging.Log", "org.openas2.logging.Log");

        openAS2AHome = RESOURCE.get("OpenAS2A");
        OpenAS2ServerTest.openAS2A = new OpenAS2Server.Builder().run(RESOURCE.get("OpenAS2A", "config", "config.xml").getAbsolutePath());

        openAS2AOutbox = FileUtils.getFile(openAS2AHome, "data", "toOpenAS2B");
        openAS2AMDNs = FileUtils.getFile(openAS2AHome, "data", "OpenAS2A_OID-OpenAS2B_OID", "mdn", DateUtil.formatDate("yyyy-MM-dd"));

        openAS2B = new OpenAS2Server.Builder().run(RESOURCE.get("OpenAS2B", "config", "config.xml").getAbsolutePath());
        openAS2BHome = RESOURCE.get("OpenAS2B");
        openAS2BInbox = FileUtils.getFile(openAS2BHome, "data", "OpenAS2A_OID-OpenAS2B_OID", "inbox");
        openAS2BMDNs = FileUtils.getFile(openAS2BHome, "data", "OpenAS2A_OID-OpenAS2B_OID", "mdn", DateUtil.formatDate("yyyy-MM-dd"));
    }

    @AfterClass
    public static void tearDown() throws Exception
    {
        openAS2A.shutdown();
        openAS2B.shutdown();
        executorService.shutdown();
    }

    @Test
    public void shouldSendMessages() throws Exception
    {
        int amountOfMessages = 100;
        List<Callable<TestMessage>> callers = new ArrayList<Callable<TestMessage>>(amountOfMessages);

        // prepare messages
        for (int i = 0; i < amountOfMessages; i++)
        {
            callers.add(new Callable<TestMessage>() {
                @Override
                public TestMessage call() throws Exception
                {
                    return sendMessage();
                }
            });
        }

        //send and verify all messages in parallel
        for (Future<TestMessage> result : executorService.invokeAll(callers))
        {
            verifyMessageDelivery(result.get());
        }
    }

    private TestMessage sendMessage() throws IOException
    {
        String outgoingMsgFileName = RandomStringUtils.randomAlphanumeric(10) + ".txt";
        String outgoingMsgBody = RandomStringUtils.randomAlphanumeric(1024);
        File outgoingMsg = tmp.newFile(outgoingMsgFileName);
        FileUtils.write(outgoingMsg, outgoingMsgBody, "UTF-8");

        FileUtils.copyFileToDirectory(outgoingMsg, openAS2AOutbox);

        return new TestMessage(outgoingMsgFileName, outgoingMsgBody);

    }


    private void verifyMessageDelivery(TestMessage testMessage) throws IOException
    {

        // wait till delivery occurs
        File deliveredMsg = waitForFile(openAS2BInbox, new PrefixFileFilter(testMessage.fileName), 20, TimeUnit.SECONDS);

        {
            String deliveredMsgBody = FileUtils.readFileToString(deliveredMsg, "UTF-8");
            assertThat("Verify content of delivered message", deliveredMsgBody, is(testMessage.body));
        }

        {
            File deliveryConfirmationMDN = waitForFile(openAS2AMDNs, new PrefixFileFilter(testMessage.fileName), 1, TimeUnit.SECONDS);
            assertThat("Verify MDN was received by OpenAS2A", deliveryConfirmationMDN.exists(), is(true));
        }

        {
            File deliveryConfirmationMDN = waitForFile(openAS2BMDNs, new PrefixFileFilter(testMessage.fileName), 1, TimeUnit.SECONDS);
            assertThat("Verify MDN was stored by OpenAB2B", deliveryConfirmationMDN.exists(), is(true));
        }
    }

    private static class TestMessage {
        private final String fileName;
        private final String body;

        private TestMessage(String fileName, String body)
        {
            this.fileName = fileName;
            this.body = body;
        }
    }

}