package org.openas2.processor.sender;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.mockito.Mockito;
import org.openas2.app.BaseServerSetup;
import org.openas2.message.FileAttribute;
import org.openas2.message.Message;
import org.openas2.partner.Partnership;
import org.openas2.processor.resender.ResenderModule;
import org.openas2.util.HTTPUtil;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies how the timeouts for sending to a partner are worked out.
 * <p>
 * The sender module's "readtimeout" was replaced by "sockettimeout" when it turned out to be mapped to
 * the wrong HttpClient setting, but the shipped config.xml and the HowTo still set "readtimeout", which
 * was then read and never applied, so the socket timeout stayed at 60 seconds whatever it said. It is
 * honoured again as the socket timeout when "sockettimeout" is not set. A partnership can also set its
 * own "connecttimeout" and "sockettimeout", so one slow partner can be given longer without raising the
 * timeout for every partner.
 */
@TestInstance(Lifecycle.PER_CLASS)
public class SendTimeoutTest extends BaseServerSetup {

    // Long enough that a socket timeout well below it is clearly what ended the send
    private static final int PARTNER_RESPONSE_DELAY_MILLIS = 3000;

    private HttpServer slowPartner;
    private String slowPartnerUrl;
    private final AtomicInteger requestsReceived = new AtomicInteger();

    @BeforeAll
    public void setUp() throws Exception {
        super.createFileSystemResources(this.getClass().getName());
        // Pending and resend files go to a folder of this test's own rather than the one the test
        // configuration shares between test classes, so the sends here leave nothing for others to find
        try (FileOutputStream fos = new FileOutputStream(openAS2PropertiesFile)) {
            fos.write(("storageBaseDir=" + new File(configDir, "data").getAbsolutePath().replace('\\', '/') + "\n").getBytes());
        }
        super.setup();

        slowPartner = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        slowPartner.createContext("/as2", exchange -> {
            try (InputStream in = exchange.getRequestBody()) {
                in.readAllBytes();
            }
            requestsReceived.incrementAndGet();
            try {
                Thread.sleep(PARTNER_RESPONSE_DELAY_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                os.flush();
            }
        });
        slowPartner.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        slowPartner.start();
        slowPartnerUrl = "http://127.0.0.1:" + slowPartner.getAddress().getPort() + "/as2";
    }

    @AfterAll
    public void tearDown() throws Exception {
        if (slowPartner != null) {
            slowPartner.stop(0);
        }
        super.tearDown();
    }

    /* -------------------------------------------------------------- the module's own settings */

    @Test
    public void withNothingSetBothTimeoutsAreSixtySeconds() throws Exception {
        Map<String, Object> options = sender(new HashMap<String, String>()).getHttpOptions();

        assertEquals("60000", options.get(HTTPUtil.PARAM_SOCKET_TIMEOUT));
        assertEquals("60000", options.get(HTTPUtil.PARAM_CONNECT_TIMEOUT));
    }

    @Test
    public void readTimeoutIsHonouredAsTheSocketTimeout() throws Exception {
        // What the shipped config.xml and the HowTo set, which was read and then never applied
        Map<String, Object> options = sender(params(HttpSenderModule.PARAM_READ_TIMEOUT, "120000")).getHttpOptions();

        assertEquals("120000", options.get(HTTPUtil.PARAM_SOCKET_TIMEOUT));
    }

    @Test
    public void socketTimeoutWinsOverReadTimeoutWhenBothAreSet() throws Exception {
        Map<String, String> params = params(HttpSenderModule.PARAM_READ_TIMEOUT, "120000");
        params.put(HttpSenderModule.PARAM_SOCKET_TIMEOUT, "300000");

        assertEquals("300000", sender(params).getHttpOptions().get(HTTPUtil.PARAM_SOCKET_TIMEOUT));
    }

    /* ---------------------------------------------------------------- per partnership overrides */

    @Test
    public void aPartnershipCanSetItsOwnTimeouts() throws Exception {
        Partnership partnership = new Partnership();
        partnership.setAttribute(HttpSenderModule.PARAM_SOCKET_TIMEOUT, "900000");
        partnership.setAttribute(HttpSenderModule.PARAM_CONNECT_TIMEOUT, "15000");

        Map<String, Object> options = sender(params(HttpSenderModule.PARAM_SOCKET_TIMEOUT, "60000")).getHttpOptions(partnership);

        assertEquals("900000", options.get(HTTPUtil.PARAM_SOCKET_TIMEOUT));
        assertEquals("15000", options.get(HTTPUtil.PARAM_CONNECT_TIMEOUT));
    }

    @Test
    public void aPartnershipThatSetsNothingUsesTheModuleSettings() throws Exception {
        Map<String, Object> options = sender(params(HttpSenderModule.PARAM_SOCKET_TIMEOUT, "200000")).getHttpOptions(new Partnership());

        assertEquals("200000", options.get(HTTPUtil.PARAM_SOCKET_TIMEOUT));
        assertEquals("60000", options.get(HTTPUtil.PARAM_CONNECT_TIMEOUT));
    }

    @Test
    public void anInvalidPartnershipValueIsIgnoredRatherThanFailingEverySend() throws Exception {
        Partnership partnership = new Partnership();
        partnership.setName("PartnerA-typo");
        partnership.setAttribute(HttpSenderModule.PARAM_SOCKET_TIMEOUT, "5 minutes");
        partnership.setAttribute(HttpSenderModule.PARAM_CONNECT_TIMEOUT, "-1");

        Map<String, Object> options = sender(params(HttpSenderModule.PARAM_SOCKET_TIMEOUT, "200000")).getHttpOptions(partnership);

        assertEquals("200000", options.get(HTTPUtil.PARAM_SOCKET_TIMEOUT));
        assertEquals("60000", options.get(HTTPUtil.PARAM_CONNECT_TIMEOUT));
    }

    /* --------------------------------------------------------------------- applied to a real send */

    @Test
    public void thePartnershipSocketTimeoutIsWhatEndsAWaitForASlowPartner() throws Exception {
        long elapsed = timeSendToSlowPartner("400");

        assertTrue(elapsed < PARTNER_RESPONSE_DELAY_MILLIS - 500,
                "a 400ms partnership socket timeout should end the send before the partner answers, took " + elapsed + "ms");
    }

    @Test
    public void withoutAnOverrideTheSendWaitsForTheSlowPartner() throws Exception {
        // The control for the test above: the module default comfortably outlasts the partner
        long elapsed = timeSendToSlowPartner(null);

        assertTrue(elapsed >= PARTNER_RESPONSE_DELAY_MILLIS - 100,
                "with the 60 second default the send should wait for the partner's answer, took " + elapsed + "ms");
    }

    /* ------------------------------------------------------------------------------------- fixtures */

    private long timeSendToSlowPartner(String partnershipSocketTimeout) throws Exception {
        Message msg = getSimpleTestMsg();
        addSendPayloadStuffToMsg("timeout-test.txt", msg);
        msg.getPartnership().setAttribute(Partnership.PA_AS2_URL, slowPartnerUrl);
        // What a directory poller sets up before handing a file to the sender
        msg.setAttribute(FileAttribute.MA_PENDINGFILE, new File(configDir, "pending-" + System.nanoTime()).getAbsolutePath());
        if (partnershipSocketTimeout != null) {
            msg.getPartnership().setAttribute(HttpSenderModule.PARAM_SOCKET_TIMEOUT, partnershipSocketTimeout);
        }
        int before = requestsReceived.get();
        AS2SenderModule sender = sender(new HashMap<String, String>());

        long started = System.nanoTime();
        try {
            Map<String, Object> options = new HashMap<String, Object>();
            // No retries, so a failed send is abandoned rather than queued for the resender
            options.put(ResenderModule.OPTION_RETRIES, "0");
            sender.handle(SenderModule.DO_SEND, msg, options);
        } catch (Exception e) {
            // A timed out or unusable response is handed to the resender or reported; only the time matters
        }
        long elapsed = (System.nanoTime() - started) / 1_000_000;

        assertEquals(before + 1, requestsReceived.get(), "the message should have reached the partner");
        return elapsed;
    }

    private AS2SenderModule sender(Map<String, String> moduleParams) {
        AS2SenderModule sender = Mockito.spy(new AS2SenderModule());
        Mockito.doReturn(session).when(sender).getSession();
        sender.getParameters().putAll(moduleParams);
        return sender;
    }

    private Map<String, String> params(String name, String value) {
        Map<String, String> params = new HashMap<String, String>();
        params.put(name, value);
        return params;
    }
}
