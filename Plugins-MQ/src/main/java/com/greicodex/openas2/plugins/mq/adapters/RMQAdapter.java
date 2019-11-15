package com.greicodex.openas2.plugins.mq.adapters;

import com.greicodex.openas2.plugins.mq.MQConnector;
import com.greicodex.openas2.plugins.mq.ConsumerCallback;
import com.greicodex.openas2.plugins.mq.MessageBrokerAdapter;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.BasicProperties;
import com.rabbitmq.client.BuiltinExchangeType;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.Delivery;
import com.rabbitmq.client.Consumer;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Envelope;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.jms.DeliveryMode;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 *
 * @author javier
 */
public class RMQAdapter implements  MessageBrokerAdapter {
    final static String RMQ_URI_PARAM="uri";
    final static String RMQ_VHOST_PARAM="virtualhost";
    ConnectionFactory factory;
    Connection connection;
    String eventProducer;
    Consumer messageConsumer;
    String messageQueue;
    String messageProducer;
    Channel channel;
    private Log logger = LogFactory.getLog(MQConnector.class.getSimpleName());
    
    @Override
    public void connect(Map<String, String> parameters) {
        if(!parameters.containsKey(RMQAdapter.RMQ_URI_PARAM)) {
            throw new RuntimeException(RMQAdapter.class.getSimpleName() + " requires parameter: "+RMQAdapter.RMQ_URI_PARAM);
        }
        if(!parameters.containsKey(RMQAdapter.RMQ_VHOST_PARAM)) {
            throw new RuntimeException(RMQAdapter.class.getSimpleName() + " requires parameter: "+RMQAdapter.RMQ_VHOST_PARAM);
        }
        factory = new ConnectionFactory();
        try {
            factory.setUri(parameters.get(RMQAdapter.RMQ_URI_PARAM));
            factory.setVirtualHost(parameters.get(RMQAdapter.RMQ_VHOST_PARAM));
            factory.setRequestedHeartbeat(1);
            factory.setConnectionTimeout(5000);
            factory.setAutomaticRecoveryEnabled(true);
            factory.setNetworkRecoveryInterval(5);
            factory.setTopologyRecoveryEnabled(true);
        } catch (Exception ex) {
            logger.error("Error setting uri",ex);
            throw new RuntimeException("Error setting uri",ex);
        }
        try {
            logger.info("Creating connection to uri" + parameters.get(RMQAdapter.RMQ_URI_PARAM) + parameters.get(RMQAdapter.RMQ_VHOST_PARAM));
            connection=factory.newConnection();
            connection.addShutdownListener(new ShutdownListener() {
                @Override
                public void shutdownCompleted(ShutdownSignalException sse) {
                    logger.error("Connection shutdown",sse);
                }
            });
        } catch (Exception ex) {
            logger.error("Error newConnection",ex);
            throw new RuntimeException("Error newConnection",ex);
        }
        try {
            channel=connection.createChannel();
        } catch (Exception ex) {
            logger.error("Failed createChannel",ex);
            throw new RuntimeException("Error createChannel",ex);
        }
        
    }

    @Override
    public void createEventMQInterface(String eventTopic) throws RuntimeException {
        try {
            channel.exchangeDeclare(eventTopic, BuiltinExchangeType.TOPIC,true);
            eventProducer=eventTopic;
        } catch (IOException ex) {
            Logger.getLogger(RMQAdapter.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
        
    }

    @Override
    public void createMessageMQInterface(String messageReceivedTopic, String messageSendQueue, ConsumerCallback callback) throws RuntimeException {
        try {
            channel.exchangeDeclare(messageReceivedTopic, BuiltinExchangeType.TOPIC, true);
            messageProducer = messageReceivedTopic;
        } catch (IOException ex) {
            Logger.getLogger(RMQAdapter.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
        
        try {
            channel.queueDeclare(messageSendQueue, true, false, false, null);
            messageQueue=messageSendQueue;
        } catch (IOException ex) {
            Logger.getLogger(RMQAdapter.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException(ex);
        }
        
        messageConsumer = new DefaultConsumer(channel) {
            @Override
            public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                super.handleDelivery(consumerTag, envelope, properties, body); 
                logger.info("handleDelivery "+envelope.getDeliveryTag());
                Map<String,String> params=new HashMap<>();
                properties.getHeaders().forEach((t, u) -> {
                    params.put(t, u.toString());
                });
                InputStream inputData = new ByteArrayInputStream(body);
                try {
                    callback.onMessage(params, inputData);
                    channel.basicAck(envelope.getDeliveryTag(), true);
                }catch(Throwable t) {
                    logger.error("Failed to process message", t);
                    channel.basicNack(envelope.getDeliveryTag(), true,false);
                }
                
            }
        };
        
    }

    @Override
    public void start() throws RuntimeException {
        try {
            channel.basicConsume(messageQueue, messageConsumer);
        } catch (IOException ex) {
            logger.error("Error starting", ex);
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void stop() throws RuntimeException {
        try {
            channel.abort();
        } catch (IOException ex) {
            logger.error("Aborting Channel error",ex);
            throw new RuntimeException(ex);
        }
        connection.abort();
    }

    @Override
    public void sendMessage(InputStream rawInputStream, Map<String, String> headers) {
        this.publishOnQueue(messageProducer, rawInputStream, headers);
    }
    
    protected void publishOnQueue(String exchange,InputStream rawInputStream, Map<String, String> headers) {
        String routingKey="";
        String contentEncoding=null;
        String contentType=null;
        int deliveryMode=DeliveryMode.PERSISTENT;
        int priority=0;
        Date timestamp=null;
        String correlationId=null;
        String replyTo=null;
        String expiration=null;
        String messageId=null;
        String type=null;
        String userid=null;
        String appid=null;
        String clusterid=null;
        Map<String,Object> amqHeaders = new HashMap<>();
        headers.forEach((t, u) -> {
            amqHeaders.put(t, u);
        });
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try {
            IOUtils.copyLarge(rawInputStream, result);
        } catch (IOException ex) {
            Logger.getLogger(RMQAdapter.class.getName()).log(Level.SEVERE, null, ex);
        }
        BasicProperties bp = new AMQP.BasicProperties(contentType, 
                contentEncoding, amqHeaders, deliveryMode, priority,
                correlationId, replyTo, expiration, messageId, timestamp, type, userid, appid, clusterid);
        try {
            channel.basicPublish(exchange, routingKey, (AMQP.BasicProperties) bp, result.toByteArray());
        } catch (IOException ex) {
            Logger.getLogger(RMQAdapter.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    @Override
    public void sendEvent(InputStream rawInputStream, Map<String, String> headers) {
        this.publishOnQueue(eventProducer, rawInputStream, headers);
    }
}
