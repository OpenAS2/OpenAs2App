package org.openas2.processor.receiver;

import org.openas2.OpenAS2Exception;

import java.net.InetAddress;


public class NetException extends OpenAS2Exception {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public NetException() {
        super();
    }

    public NetException(String msg) {
        super(msg);
    }

    public NetException(InetAddress address, int port, Throwable cause) {
        super("Address = " + address + " port = " + port);
        initCause(cause);
    }
}
