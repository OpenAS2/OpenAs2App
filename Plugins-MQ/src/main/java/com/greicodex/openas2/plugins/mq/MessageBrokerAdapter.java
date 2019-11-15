package com.greicodex.openas2.plugins.mq;

import java.io.InputStream;
import java.util.Map;

/**
 *
 * @author javier
 */
public interface MessageBrokerAdapter {

    void connect(Map<String, String> parameters);

    void createEventMQInterface(String eventTopic) throws RuntimeException;

    void createMessageMQInterface(String messageReceivedTopic, String messageSendQueue, ConsumerCallback callback) throws RuntimeException;

    void start() throws RuntimeException;

    void stop() throws RuntimeException;

    public void sendMessage(InputStream rawInputStream, Map<String, String> headers);

    public void sendEvent(InputStream rawInputStream, Map<String, String> headers);

}
