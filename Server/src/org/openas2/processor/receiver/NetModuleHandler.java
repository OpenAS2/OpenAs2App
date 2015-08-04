package org.openas2.processor.receiver;

import java.net.Socket;


public interface NetModuleHandler {
    public void handle(NetModule owner, Socket s);
}
