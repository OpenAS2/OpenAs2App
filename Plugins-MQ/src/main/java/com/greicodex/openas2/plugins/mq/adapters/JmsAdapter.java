/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.greicodex.openas2.plugins.mq.adapters;

import com.greicodex.openas2.plugins.mq.MQConnector;
import com.greicodex.openas2.plugins.mq.ConsumerCallback;
import com.greicodex.openas2.plugins.mq.MessageBrokerAdapter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.ExceptionListener;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author javier
 */
public class JmsAdapter implements MessageListener, ExceptionListener, MessageBrokerAdapter {

    ConnectionFactory jmsFactory;
    Connection jmsConnection;
    MessageConsumer jmsMessageConsumer;
    MessageProducer jmsMessageProducer;
    MessageProducer jmsEventProducer;
    JMSContext jmsContext;
    javax.jms.Session jmsSession;
    java.lang.Class JmsClass;
    ConsumerCallback messageCallback;
    private Log logger = LogFactory.getLog(MQConnector.class.getSimpleName());

    @Override
    public void connect(Map<String, String> parameters) {
        javax.naming.Context ctx = null;
        try {
            ctx = new InitialContext();
        } catch (NamingException ex) {
            Logger.getLogger(JmsAdapter.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException("Failed to initialize JNDI Context", ex);
        }
        try {
            jmsFactory = (ConnectionFactory) ctx.lookup(parameters.get("jms_factory_jndi"));
        } catch (NamingException ex) {
            Logger.getLogger(JmsAdapter.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException("Unable to lookup JMS connection factory from JNDI: "+parameters.get("jms_factory_jndi"), ex);
        }
        try {
            //jmsContext = jmsFactory.createContext(JMSContext.CLIENT_ACKNOWLEDGE);
            jmsConnection = jmsFactory.createConnection();
            jmsSession = jmsConnection.createSession(true, JMSContext.CLIENT_ACKNOWLEDGE);
            jmsConnection.setExceptionListener(this);
        } catch (JMSException ex) {
            logger.error("Unable to create JMS connection ", ex);
            throw new RuntimeException("Unable to create JMS connection factory", ex);
        }
    }

    @Override
    public void createMessageMQInterface(String messageReceivedTopic, String messageSendQueue, ConsumerCallback callback) throws RuntimeException {
        try {
            jmsMessageConsumer = (MessageConsumer) jmsSession.createConsumer(jmsSession.createQueue(messageSendQueue));
            jmsMessageProducer = (MessageProducer) jmsSession.createProducer(jmsSession.createTopic(messageReceivedTopic));
            
            jmsMessageConsumer.setMessageListener(this);
        } catch (JMSException ex) {
            Logger.getLogger(JmsAdapter.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void createEventMQInterface(String eventTopic) throws RuntimeException {
        try {
            jmsEventProducer = (MessageProducer) jmsSession.createProducer(jmsSession.createTopic(eventTopic));
        } catch (JMSException ex) {
            Logger.getLogger(JmsAdapter.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        try {
            jmsSession.commit();
        } finally {
            logger.debug("Messages flushed");
        }
        jmsEventProducer = null;
        jmsMessageConsumer = null;
        jmsMessageConsumer = null;
        try {
            jmsSession.close();
        } finally {
            logger.debug("JMS Session closed");
        }
        try {
            jmsConnection.close();
        } finally {
            logger.debug("JMS Connection closed");
        }
        jmsFactory = null;
    }

    @Override
    public void onMessage(javax.jms.Message message) {
        logger.debug(message);
        InputStream inputData = new InputStream() {
            @Override
            public int read() throws IOException {
                return 0;
            }
        } ;
        Map<String, String> params = new HashMap<>();
        messageCallback.onMessage(params, inputData);
        
    }

    @Override
    public void start() throws RuntimeException {
        try {
            jmsConnection.start();
            javax.jms.BytesMessage msg = jmsSession.createBytesMessage();
            msg.writeBytes("this is a test".getBytes());
            jmsMessageProducer.send(msg);
        } catch (JMSException ex) {
            Logger.getLogger(JmsAdapter.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void stop() throws RuntimeException {
        try {
            jmsConnection.stop();
        } catch (JMSException ex) {
            Logger.getLogger(JmsAdapter.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void onException(JMSException exception) {
        logger.error("MQ Adapter Error", exception);
    }

    @Override
    public void sendMessage(InputStream rawInputStream, Map<String, String> headers) {
        try {
            jmsMessageProducer.send(createJmsMessage(rawInputStream,headers));
        } catch (JMSException ex) {
            Logger.getLogger(JmsAdapter.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void sendEvent(InputStream rawInputStream, Map<String, String> headers) {
        try {
            jmsEventProducer.send(createJmsMessage(rawInputStream,headers));
        } catch (JMSException ex) {
            Logger.getLogger(JmsAdapter.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    private Message createJmsMessage(InputStream rawInputStream, Map<String, String> headers) throws JMSException {
        BytesMessage msg = jmsSession.createBytesMessage();
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try {
            IOUtils.copyLarge(rawInputStream, result);
        } catch (IOException ex) {
            Logger.getLogger(RMQAdapter.class.getName()).log(Level.SEVERE, null, ex);
        }
        msg.writeBytes(result.toByteArray());
        headers.forEach((t, u) -> {
            try {
                msg.setStringProperty(t, u);
            } catch (JMSException ex) {
                Logger.getLogger(JmsAdapter.class.getName()).log(Level.SEVERE, null, ex);
            }
        });
        return msg;
        
    }
}
