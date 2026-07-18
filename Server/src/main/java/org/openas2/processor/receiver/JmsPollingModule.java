package org.openas2.processor.receiver;

import jakarta.jms.Connection;
import jakarta.jms.JMSException;
import jakarta.jms.MessageConsumer;
import jakarta.jms.MessageListener;
import jakarta.jms.Queue;
import jakarta.jms.Session;

import org.apache.qpid.jms.JmsConnectionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.message.AS2Message;
import org.openas2.message.Message;
import org.openas2.partner.Partnership;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * An alternative outbound intake to {@link AS2DirectoryPollingModule} that consumes work from a
 * message queue instead of scanning a directory. It is a generic AMQP 1.0 JMS consumer (Apache
 * Qpid JMS), so it works against Azure Service Bus and any other AMQP 1.0 broker via the connection
 * URI alone.
 * <p>
 * The module is a pure consumer: an external producer drops the file on shared storage and
 * publishes a queue message describing it. Each message carries, as JMS string properties, the
 * sender and receiver AS2 IDs (used to resolve the partnership) and the path of the file to send.
 * The module opens the file, then sends it through the same {@link MessageBuilderModule} pipeline
 * the directory poller uses, so signing, encryption, MDN handling and tracking are identical. Files
 * are moved to the sent/error directories exactly as the poller does.
 * <p>
 * Settlement mirrors the poller's file lifecycle and makes the broker the sole retry authority:
 * per-message internal resend is disabled, so on a successful send (synchronous MDN received, or an
 * asynchronous MDN send accepted and awaiting the callback) the message is acknowledged; on any
 * failure it is returned to the broker for redelivery, which dead-letters it after the broker's
 * configured maximum delivery count.
 */
public class JmsPollingModule extends MessageBuilderModule {

    public static final String PARAM_BROKER_URL = "broker_url";
    public static final String PARAM_QUEUE_NAME = "queue_name";
    public static final String PARAM_BROKER_USER = "broker_user";
    public static final String PARAM_BROKER_PWD = "broker_password";
    public static final String PARAM_CONSUMER_COUNT = "consumer_count";
    public static final String PARAM_SENDER_ID_PROPERTY = "sender_id_property";
    public static final String PARAM_RECEIVER_ID_PROPERTY = "receiver_id_property";
    public static final String PARAM_FILEPATH_PROPERTY = "filepath_property";
    public static final String PARAM_FILENAME_PROPERTY = "filename_property";

    // Default JMS message property names. JMS property names must be valid identifiers (no
    // hyphens), so the AS2 IDs use underscore-separated names.
    private static final String DEFAULT_SENDER_ID_PROPERTY = "sender_as2_id";
    private static final String DEFAULT_RECEIVER_ID_PROPERTY = "receiver_as2_id";
    private static final String DEFAULT_FILEPATH_PROPERTY = "filepath";
    private static final String DEFAULT_FILENAME_PROPERTY = "filename";

    private Connection connection = null;

    private String queueName;
    private String senderIdProperty;
    private String receiverIdProperty;
    private String filePathProperty;
    private String filenameProperty;

    // Carries the routing derived from the current message's properties into the shared
    // buildBaseMessage() flow. Set and cleared within a single onMessage() call on the same thread.
    private final ThreadLocal<String> currentRouting = new ThreadLocal<String>();

    private Logger logger = LoggerFactory.getLogger(JmsPollingModule.class);

    protected AS2Message createMessage() {
        return new AS2Message();
    }

    /**
     * Supplies per-message routing from the consumed queue message rather than the static
     * "defaults" module parameter the directory poller uses.
     */
    @Override
    protected String getSenderReceiverDefaults(Message msg) throws OpenAS2Exception {
        return currentRouting.get();
    }

    /**
     * Disables OpenAS2's internal file-based resend for queue-originated messages so the broker is
     * the single retry authority. Setting the attribute on the message's own partnership copy only
     * affects this send.
     */
    @Override
    protected Message processDocument(File pendingFile, Message msg) throws OpenAS2Exception, FileNotFoundException {
        msg.getPartnership().getAttributes().put(Partnership.PA_RESEND_MAX_RETRIES, "0");
        return super.processDocument(pendingFile, msg);
    }

    public void doStart() throws OpenAS2Exception {
        String brokerUrl = getParameter(PARAM_BROKER_URL, true);
        queueName = getParameter(PARAM_QUEUE_NAME, true);
        String user = getParameter(PARAM_BROKER_USER, false);
        String pwd = getParameter(PARAM_BROKER_PWD, false);
        int consumerCount = getParameterInt(PARAM_CONSUMER_COUNT, false, 1);
        senderIdProperty = getParameter(PARAM_SENDER_ID_PROPERTY, DEFAULT_SENDER_ID_PROPERTY);
        receiverIdProperty = getParameter(PARAM_RECEIVER_ID_PROPERTY, DEFAULT_RECEIVER_ID_PROPERTY);
        filePathProperty = getParameter(PARAM_FILEPATH_PROPERTY, DEFAULT_FILEPATH_PROPERTY);
        filenameProperty = getParameter(PARAM_FILENAME_PROPERTY, DEFAULT_FILENAME_PROPERTY);

        try {
            JmsConnectionFactory factory = new JmsConnectionFactory(brokerUrl);
            connection = (user != null && user.length() > 0) ? factory.createConnection(user, pwd) : factory.createConnection();
            connection.setExceptionListener(e -> logger.error("JMS connection error on queue " + queueName + ": " + e.getMessage(), e));
            for (int i = 0; i < consumerCount; i++) {
                // Transacted session so a failed send rolls back and is redelivered with an
                // incremented delivery count, letting the broker dead-letter it after its
                // configured maximum delivery count (portable across Artemis and Service Bus).
                Session session = connection.createSession(true, Session.SESSION_TRANSACTED);
                Queue queue = session.createQueue(queueName);
                MessageConsumer consumer = session.createConsumer(queue);
                consumer.setMessageListener(new QueueFileListener(session));
            }
            connection.start();
            logger.info("JMS queue consumer started on queue \"" + queueName + "\" with " + consumerCount + " consumer(s)");
        } catch (JMSException e) {
            throw new OpenAS2Exception("Failed to start JMS queue consumer on queue: " + queueName, e);
        }
    }

    public void doStop() throws OpenAS2Exception {
        try {
            if (connection != null) {
                // Closing the connection closes its sessions and consumers
                connection.close();
            }
        } catch (JMSException e) {
            logger.error("Error closing JMS connection for queue " + queueName, e);
        } finally {
            connection = null;
        }
    }

    @Override
    public boolean healthcheck(List<String> failures) {
        if (connection == null) {
            failures.add(getClass().getSimpleName() + " - JMS connection is not established for queue: " + queueName);
            return false;
        }
        return true;
    }

    private boolean isSendSuccessful(String state) {
        return Message.MSG_STATE_MSG_SENT_MDN_RECEIVED_OK.equals(state)
                || Message.MSG_STATE_MSG_SENT_AWAIT_ASYNC_MDN_RESPONSE.equals(state);
    }

    private class QueueFileListener implements MessageListener {
        private final Session session;

        QueueFileListener(Session session) {
            this.session = session;
        }

        public void onMessage(jakarta.jms.Message jmsMessage) {
            String filePath = null;
            try {
                filePath = jmsMessage.getStringProperty(filePathProperty);
                String senderId = jmsMessage.getStringProperty(senderIdProperty);
                String receiverId = jmsMessage.getStringProperty(receiverIdProperty);
                if (filePath == null || senderId == null || receiverId == null) {
                    throw new OpenAS2Exception("Queue message is missing required properties ("
                            + filePathProperty + ", " + senderIdProperty + ", " + receiverIdProperty
                            + "): filepath=" + filePath + " sender=" + senderId + " receiver=" + receiverId);
                }
                File file = new File(filePath);
                String filename = jmsMessage.getStringProperty(filenameProperty);
                if (filename == null || filename.length() == 0) {
                    filename = file.getName();
                }

                currentRouting.set("sender.as2_id=" + senderId + ", receiver.as2_id=" + receiverId);

                // Use the file-based intake so the original file is moved into the pipeline and
                // ends up in the sent or error directory, matching the directory poller.
                Message msg = processDocument(file, filename);
                if (msg == null) {
                    // Null is only returned when file-splitting kicks in, which writes the split
                    // pieces to the source directory expecting a directory poller to pick them up.
                    // That has no meaning for queue intake, so surface it rather than silently drop.
                    throw new OpenAS2Exception("File splitting is not supported by the queue consumer for file: " + filePath);
                }

                String state = (String) msg.getOption("STATE");
                if (isSendSuccessful(state)) {
                    session.commit();
                    if (logger.isInfoEnabled()) {
                        logger.info("Queue message sent successfully (state=" + state + ") for file: " + filePath + msg.getLogMsgID());
                    }
                } else {
                    // The file has already been moved to the error directory by the pipeline.
                    // Roll back so the message is redelivered and eventually dead-lettered.
                    logger.error("Send failed (state=" + state + ") for queue file: " + filePath
                            + " - rolling back for redelivery/dead-lettering");
                    session.rollback();
                }
            } catch (Exception e) {
                logger.error("Failed to process queue message for file: " + filePath, e);
                try {
                    session.rollback();
                } catch (JMSException je) {
                    logger.error("Failed to roll back after processing error for file: " + filePath, je);
                }
            } finally {
                currentRouting.remove();
            }
        }
    }
}
