package org.openas2.cert;

import org.openas2.OpenAS2Exception;

import java.security.cert.X509Certificate;

public class KeyNotFoundException extends OpenAS2Exception {

    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public KeyNotFoundException(X509Certificate cert, String alias) {
        super("Certificate: " + cert + ", Alias: " + alias);
    }

    public KeyNotFoundException(X509Certificate cert, String alias, Throwable cause) {
        super("Certificate: " + cert + ", Alias: " + alias);
        initCause(cause);
    }
}
