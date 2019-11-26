package org.openas2.processor.receiver;

import java.net.Socket;


public interface NetModuleHandler {
    void handle(NetModule owner, Socket s);
}
