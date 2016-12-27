package org.openas2.lib.message;

import org.openas2.lib.OpenAS2Exception;

public class DispositionException extends OpenAS2Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	public DispositionException() {
		super();
	}

	public DispositionException(String msg) {
		super(msg);
	}

	public DispositionException(String msg, Throwable cause) {
		super(msg, cause);
	}

	public DispositionException(Throwable cause) {
		super(cause);
	}

}