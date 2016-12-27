package org.openas2.lib.cert;

import org.openas2.lib.OpenAS2Exception;

public class CertificateException extends OpenAS2Exception {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public CertificateException() {
        super();
    }

    public CertificateException(String msg) {
        super(msg);
    }

    public CertificateException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public CertificateException(Throwable cause) {
        super(cause);
    }

}
