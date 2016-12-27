package org.openas2.lib.partner;

import org.openas2.lib.OpenAS2Exception;


public class PartnerException extends OpenAS2Exception {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public PartnerException() {
        super();
    }

    public PartnerException(String msg) {
        super(msg);
    }

    public PartnerException(String msg, Throwable cause) {
        super(msg, cause);
    }

    public PartnerException(Throwable cause) {
        super(cause);
    }

}
