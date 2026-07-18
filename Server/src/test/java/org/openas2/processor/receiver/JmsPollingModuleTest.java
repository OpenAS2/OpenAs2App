package org.openas2.processor.receiver;

import jakarta.jms.Connection;
import jakarta.jms.MessageProducer;
import jakarta.jms.Queue;
import jakarta.jms.Session;

import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.core.settings.impl.AddressSettings;
import org.apache.qpid.jms.JmsConnectionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.io.TempDir;
import org.openas2.OpenAS2Exception;
import org.openas2.message.AS2Message;
import org.openas2.message.Message;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises JmsPollingModule against a real embedded AMQP 1.0 broker (ActiveMQ Artemis) via the
 * Qpid JMS client. Verifies that a published queue message is consumed, routed using its
 * properties, and settled correctly: acknowledged on a successful send, and returned to the broker
 * for redelivery and dead-lettering on failure. The actual AS2 send is stubbed by overriding
 * processDocument so the test isolates the queue-consumer and settlement behaviour.
 */
@TestInstance(Lifecycle.PER_CLASS)
public class JmsPollingModuleTest {

    private static final String DLQ = "DLQ";
    private static final int MAX_DELIVERY_ATTEMPTS = 2;

    private EmbeddedActiveMQ broker;
    private String brokerUrl;

    @TempDir
    Path tempDir;

    private CapturingJmsPollingModule module;
    // A unique queue per test so a consumer still closing from a prior test cannot interfere
    private final AtomicInteger queueSeq = new AtomicInteger();
    private String queue;

    @BeforeAll
    public void startBroker() throws Exception {
        int port;
        try (ServerSocket s = new ServerSocket(0)) {
            port = s.getLocalPort();
        }
        brokerUrl = "amqp://localhost:" + port;

        Configuration config = new ConfigurationImpl()
                .setPersistenceEnabled(false)
                .setSecurityEnabled(false)
                .addAcceptorConfiguration("amqp", "tcp://localhost:" + port + "?protocols=AMQP");

        // Redeliver failed messages a bounded number of times, then dead-letter them
        AddressSettings settings = new AddressSettings()
                .setMaxDeliveryAttempts(MAX_DELIVERY_ATTEMPTS)
                .setDeadLetterAddress(SimpleString.of(DLQ))
                .setAutoCreateQueues(true)
                .setAutoCreateAddresses(true);
        config.addAddressSetting("#", settings);

        broker = new EmbeddedActiveMQ();
        broker.setConfiguration(config);
        boolean active = false;
        try {
            broker.start();
            // On some JREs (e.g. Java 24+, where Artemis calls the removed Subject.getSubject())
            // initialisation fails and is logged without throwing, leaving the server inactive and
            // its acceptor unbound. Poll for the server to actually become active.
            for (int i = 0; i < 30 && !active; i++) {
                active = broker.getActiveMQServer() != null && broker.getActiveMQServer().isActive();
                if (!active) {
                    Thread.sleep(100);
                }
            }
        } catch (Throwable t) {
            // fall through: handled by the assumption below
        }
        // Skip (rather than fail) when the test broker cannot run on this JRE. This is a limitation
        // of the embedded Artemis test broker, not of the module under test, which is JRE-agnostic.
        Assumptions.assumeTrue(active, "Embedded AMQP broker could not start on this JRE (test broker limitation)");
    }

    @AfterAll
    public void stopBroker() throws Exception {
        if (broker != null) {
            try {
                broker.stop();
            } catch (Exception ignored) {
                // broker may not have started on this JRE
            }
        }
    }

    @BeforeEach
    public void perTestSetup() throws Exception {
        queue = "outbound-as2-" + queueSeq.incrementAndGet();
        // Ensure each test starts with an empty DLQ
        receiveFrom(DLQ, 200);
    }

    private void startModule(String successOrFailState) throws Exception {
        module = new CapturingJmsPollingModule();
        module.stateToReturn = successOrFailState;
        Map<String, String> params = new HashMap<String, String>();
        params.put(JmsPollingModule.PARAM_BROKER_URL, brokerUrl);
        params.put(JmsPollingModule.PARAM_QUEUE_NAME, queue);
        module.init(null, params);
        module.start();
    }

    private void stopModule() throws Exception {
        if (module != null) {
            module.stop();
            module = null;
        }
    }

    private Path publishMessage(String sender, String receiver, String content) throws Exception {
        Path file = Files.createTempFile(tempDir, "payload", ".edi");
        Files.write(file, content.getBytes());
        JmsConnectionFactory factory = new JmsConnectionFactory(brokerUrl);
        try (Connection conn = factory.createConnection()) {
            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue jmsQueue = session.createQueue(queue);
            MessageProducer producer = session.createProducer(jmsQueue);
            jakarta.jms.Message m = session.createMessage();
            m.setStringProperty("sender_as2_id", sender);
            m.setStringProperty("receiver_as2_id", receiver);
            m.setStringProperty("filepath", file.toString());
            m.setStringProperty("filename", file.getFileName().toString());
            producer.send(m);
        }
        return file;
    }

    private jakarta.jms.Message receiveFrom(String queueName, long timeoutMs) throws Exception {
        JmsConnectionFactory factory = new JmsConnectionFactory(brokerUrl);
        try (Connection conn = factory.createConnection()) {
            conn.start();
            Session session = conn.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue(queueName);
            return session.createConsumer(queue).receive(timeoutMs);
        }
    }

    private void awaitProcessCount(int expected, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (module.processCount.get() >= expected) {
                return;
            }
            Thread.sleep(50);
        }
    }

    @Test
    public void successfulSendAcknowledgesAndRoutesByProperties() throws Exception {
        startModule(Message.MSG_STATE_MSG_SENT_MDN_RECEIVED_OK);
        try {
            Path file = publishMessage("MyCompany_OID", "PartnerA_OID", "EDI payload body");
            awaitProcessCount(1, 5000);

            assertEquals(1, module.processCount.get(), "message should be consumed exactly once");
            assertEquals("sender.as2_id=MyCompany_OID, receiver.as2_id=PartnerA_OID", module.capturedRouting);
            assertEquals(file.getFileName().toString(), module.capturedFilename);
            assertEquals("EDI payload body", new String(module.capturedContent));

            // Acknowledged, so it must not be redelivered or dead-lettered
            Thread.sleep(300);
            assertEquals(1, module.processCount.get(), "acknowledged message must not be redelivered");
            assertNull(receiveFrom(DLQ, 500), "successful message must not be dead-lettered");
        } finally {
            stopModule();
        }
    }

    @Test
    public void asyncMdnAwaitStateIsTreatedAsSuccess() throws Exception {
        startModule(Message.MSG_STATE_MSG_SENT_AWAIT_ASYNC_MDN_RESPONSE);
        try {
            publishMessage("MyCompany_OID", "PartnerA_OID", "async body");
            awaitProcessCount(1, 5000);

            Thread.sleep(300);
            assertEquals(1, module.processCount.get(), "async-await send must be acknowledged, not redelivered");
            assertNull(receiveFrom(DLQ, 500), "async-await send must not be dead-lettered");
        } finally {
            stopModule();
        }
    }

    @Test
    public void failedSendIsRedeliveredThenDeadLettered() throws Exception {
        startModule(Message.MSG_STATE_SEND_FAIL);
        try {
            publishMessage("MyCompany_OID", "PartnerA_OID", "will fail");
            // Redelivered up to the broker's max delivery attempts, then routed to the DLQ
            awaitProcessCount(MAX_DELIVERY_ATTEMPTS, 8000);

            assertEquals(MAX_DELIVERY_ATTEMPTS, module.processCount.get(),
                    "failed message should be delivered exactly max-delivery-attempts times");
            jakarta.jms.Message dead = receiveFrom(DLQ, 3000);
            assertNotNull(dead, "failed message should end up on the dead-letter queue");
            assertEquals("MyCompany_OID", dead.getStringProperty("sender_as2_id"));
        } finally {
            stopModule();
        }
    }

    /**
     * Test module that stubs the AS2 send: it records what was consumed and returns a message with
     * a configurable final STATE so the settlement logic can be exercised deterministically.
     */
    private static class CapturingJmsPollingModule extends JmsPollingModule {
        volatile String stateToReturn;
        volatile String capturedRouting;
        volatile String capturedFilename;
        volatile byte[] capturedContent;
        final AtomicInteger processCount = new AtomicInteger();

        @Override
        protected Message processDocument(File fileToSend, String filename) throws OpenAS2Exception {
            processCount.incrementAndGet();
            capturedFilename = filename;
            capturedRouting = getSenderReceiverDefaults(null);
            try {
                capturedContent = Files.readAllBytes(fileToSend.toPath());
            } catch (IOException e) {
                throw new OpenAS2Exception(e);
            }
            AS2Message msg = new AS2Message();
            msg.setOption("STATE", stateToReturn);
            return msg;
        }
    }
}
