package org.openas2.cert;

import org.openas2.OpenAS2Exception;

public class CertificateExistsException extends OpenAS2Exception {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public CertificateExistsException(String alias) {
        super(alias);
    }
}
