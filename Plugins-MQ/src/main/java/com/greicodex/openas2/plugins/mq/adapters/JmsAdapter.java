/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.greicodex.openas2.plugins.mq.adapters;

import com.greicodex.openas2.plugins.mq.MQConnector;
import com.greicodex.openas2.plugins.mq.ConsumerCallback;
import com.greicodex.openas2.plugins.mq.MessageBrokerAdapter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.ExceptionListener;
import javax.jms.JMSContext;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
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

    public JmsAdapter(String jmsFactoryClass) throws ClassNotFoundException {
        try {

            JmsClass = Class.forName(jmsFactoryClass);

        } catch (ClassNotFoundException | IllegalArgumentException ex) {
            logger.error("Unable to create JMS connection factory", ex);
            throw ex;
        }

    }

    @Override
    public void connect(Map<String, String> parameters) {
        java.lang.reflect.Method[] list = JmsClass.getDeclaredMethods();
        logger.info("Creating instance of " + JmsClass.getName());
        try {
            jmsFactory = (ConnectionFactory) JmsClass.newInstance();
        } catch (InstantiationException ex) {
            logger.error("Error creating JMS instance", ex);
            throw new RuntimeException("Error creating JMS instance", ex);
        } catch (IllegalAccessException ex) {
            logger.error("Error creating JMS instance", ex);
            throw new RuntimeException("Error creating JMS instance", ex);
        }
        logger.info("Configuring instance " + JmsClass.getName());
        for (java.lang.reflect.Method m : list) {
            if (!(m.getName().substring(0, 3).equalsIgnoreCase("set")
                    && m.getParameterCount() == 1
                    && m.getParameterTypes()[0].equals(String.class))) {
                continue;
            }
            String paramName = m.getName().substring(3).toLowerCase();
            logger.info("Checking config " + paramName);
            if (!parameters.containsKey(paramName)) {
                continue;
            }
            String paramValue = parameters.get(paramName);
            logger.info("Setting config " + paramName + "=" + paramValue);
            try {
                m.invoke(jmsFactory, paramValue);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
                logger.error("Error configuring JMS instance", ex);
            }
        }
        try {
            //jmsContext = jmsFactory.createContext(JMSContext.CLIENT_ACKNOWLEDGE);
            jmsConnection = jmsFactory.createConnection();
            jmsSession = jmsConnection.createSession(true, JMSContext.CLIENT_ACKNOWLEDGE);
            jmsConnection.setExceptionListener(this);
        } catch (JMSException ex) {
            logger.error("Unable to create JMS connection factory", ex);
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
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public void sendEvent(InputStream rawInputStream, Map<String, String> headers) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
}
