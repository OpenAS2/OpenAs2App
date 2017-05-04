package org.openas2.message;

import org.openas2.OpenAS2Exception;


public class InvalidMessageException extends OpenAS2Exception {

	public InvalidMessageException(String msg) {
		super(msg);
	}
}
