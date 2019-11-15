package com.greicodex.openas2.plugins.mq;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.mail.MessagingException;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.message.AS2Message;
import org.openas2.message.Message;
import org.openas2.processor.BaseActiveModule;
import org.openas2.processor.msgtracking.TrackingModule;
import org.openas2.processor.resender.ResenderModule;
import org.openas2.processor.storage.StorageModule;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.ComponentNotFoundException;
import org.openas2.message.MessageMDN;
import org.openas2.partner.Partnership;
import org.openas2.processor.sender.SenderModule;

/**
 *
 * @author javier
 */
public class MQConnector extends BaseActiveModule implements ResenderModule, TrackingModule, StorageModule, ConsumerCallback {

    public static final String PARAM_MQ_ADAPTER = "adapter";
    public static final String PARAM_MQ_MSG_PRODUCER_TOPIC = "msg_topic";
    public static final String PARAM_MQ_MSG_CONSUMER_QUEUE = "msg_queue";
    public static final String PARAM_MQ_EVT_PRODUCER_TOPIC = "evt_topic";

    protected String mqAdapterFactoryClass;
    protected int resendRetries;
    protected String messageReceivedTopic;
    protected String messageSendQueue;
    protected String eventTopic;

    MessageBrokerAdapter broker;
    MessageBuilderModule builder;

    private Log logger = LogFactory.getLog(MQConnector.class.getSimpleName());

    protected AS2Message createMessage() {
        return new AS2Message();
    }

    @Override
    public void handle(String action, Message msg, Map<Object, Object> options) throws OpenAS2Exception {
        Map<String, String> headers = new HashMap<>();
        InputStream body = null;
        if (msg != null) {
            msg.getOptions().forEach((t, u) -> {
                String name = "options."+ ((String)t).toLowerCase();
                headers.put(name,(String)u);
            });
            msg.getAttributes().forEach((t, u) -> {
                String name = "attributes."+ ((String)t).toLowerCase();
                headers.put(name,(String)u);
            });
        }

        if (action.equalsIgnoreCase(StorageModule.DO_STORE)) {
            try {
                updateHeaders(msg, headers);
                body = msg.getData().getRawInputStream();
            } catch (MessagingException ex) {
                logger.error(ex.getMessage(), ex);
            }
            broker.sendMessage(body, headers);
        } else if (action.equalsIgnoreCase(StorageModule.DO_STOREMDN)) {
            try {
                updateHeaders(msg, headers);
                if (msg.getMDN() != null) {
                    MessageMDN mdn = msg.getMDN();
                    updateHeaders((Message) mdn, headers);
                    headers.put("CorrelationId", msg.getMessageID());
                    
                    mdn.getAttributes().forEach((t, u) -> {
                        String name = "attributes."+ ((String)t).toLowerCase();
                        headers.put(name,(String)u);
                    });
                    body = new ByteArrayInputStream(mdn.getText().getBytes());
                }
            } catch (NullPointerException ex) {
                logger.error(ex.getMessage(), ex);
            }
            broker.sendMessage(body, headers);
        } else if (action.equalsIgnoreCase(TrackingModule.DO_TRACK_MSG)) {
            body = new ByteArrayInputStream(options.toString().getBytes());
            broker.sendEvent(body, headers);
        }

        if (action != null) {
            logger.info(action);
        }
        if (msg != null) {
            logger.info(msg);
        }
        if (options != null) {
            logger.info(options);
        }
    }

    private void updateHeaders(Message msg, Map<String,String> headers) {
        headers.put("message.id",msg.getMessageID());
        headers.put("message.class", msg.getClass().getSimpleName());
        headers.put("sender.as2_id", msg.getPartnership().getSenderID(Partnership.PID_AS2));
        headers.put("receiver.as2_id", msg.getPartnership().getReceiverID(Partnership.PID_AS2));
    }
    @Override
    public boolean canHandle(String action, Message msg, Map<Object, Object> options) {
        if (action.equalsIgnoreCase(StorageModule.DO_STORE)) {
            return true;
        } else if (action.equalsIgnoreCase(StorageModule.DO_STOREMDN)) {
            return true;
        } else if (action.equalsIgnoreCase(ResenderModule.DO_RESEND)) {
            return false;
        } else if (action.equalsIgnoreCase(ResenderModule.DO_RESENDMDN)) {
            return false;
        } else if (action.equalsIgnoreCase(TrackingModule.DO_TRACK_MSG)) {
            return true;
        } else {
            return super.canHandle(action, msg, options);
        }
    }

    @Override
    public void destroy() throws Exception {
        super.destroy();

    }

    protected void initMQ(Map<String, String> parameters) throws OpenAS2Exception {

        try {
            mqAdapterFactoryClass = getParameter(MQConnector.PARAM_MQ_ADAPTER, true);
            messageReceivedTopic = getParameter(MQConnector.PARAM_MQ_MSG_PRODUCER_TOPIC, true);
            messageSendQueue = getParameter(MQConnector.PARAM_MQ_MSG_CONSUMER_QUEUE, true);
            eventTopic = getParameter(MQConnector.PARAM_MQ_EVT_PRODUCER_TOPIC, true);

            broker = (MessageBrokerAdapter) Class.forName(mqAdapterFactoryClass).newInstance();
            broker.connect(parameters);
            broker.createEventMQInterface(eventTopic);
            broker.createMessageMQInterface(messageReceivedTopic, messageSendQueue, this);
        } catch (Exception ex) {
            logger.error("Unable to create message broker adapter: "+mqAdapterFactoryClass, ex);
            throw new OpenAS2Exception(ex);
        }

    }

    @Override
    public void init(Session session, Map<String, String> parameters) throws OpenAS2Exception {

        super.init(session, parameters);
        logger.info("Initializing connector");
        initMQ(parameters);

        builder = new MessageBuilderModule() {
            @Override
            protected Message createMessage() {
                return new AS2Message();
            }

            @Override
            public void doStart() throws OpenAS2Exception {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public void doStop() throws OpenAS2Exception {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }

            @Override
            public boolean healthcheck(List<String> failures) {
                throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
            }
        };
        builder.init(session, parameters);

    }

    @Override
    public void doStart() throws OpenAS2Exception {
        try {
            broker.start();
        } catch (Exception ex) {
            logger.error("Error starting up MQ listener", ex);
            throw new OpenAS2Exception(ex);
        }
    }

    @Override
    public void doStop() throws OpenAS2Exception {
        try {
            broker.stop();
        } catch (Exception ex) {
            logger.error("Error stopping up MQ listener", ex);
            throw new OpenAS2Exception(ex);
        }
    }

    @Override
    public boolean healthcheck(List<String> failures) {
        return true;
    }

    @Override
    public void onMessage(Map<String, String> headers, InputStream inputData) {
        Message message = null;
        try {
            message = builder.buildMessageMetadata(headers);
            Map<Object, Object> options = new HashMap<>();
            builder.buildMessageData(message, inputData);

            this.getSession().getProcessor().handle(SenderModule.DO_SEND, message, options);
        } catch (ComponentNotFoundException ex) {
            logger.error(ex.getMessage(), ex);
            return;
        } catch (OpenAS2Exception ex) {
            //TODO implement trackmsg failures TrackingModule.DO_TRACK_MSG
            logger.error(ex.getMessage(), ex);
            return;
        }
    }

}
