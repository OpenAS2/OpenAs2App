package org.openas2.support;

import java.net.Socket;
import org.openas2.processor.receiver.NetModule;


public interface NetModuleHandler {
    public void handle(NetModule owner, Socket s);
}
