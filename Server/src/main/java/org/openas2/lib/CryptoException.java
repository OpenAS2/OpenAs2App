package org.openas2.lib;

public class CryptoException extends OpenAS2Exception {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public CryptoException() {
        super();
    }

    public CryptoException(String msg) {
        super(msg);
    }

    public CryptoException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public CryptoException(Throwable cause) {
        super(cause);
    }
}
