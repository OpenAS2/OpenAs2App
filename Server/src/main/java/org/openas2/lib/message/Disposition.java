package org.openas2.lib.message;

import java.util.NoSuchElementException;
import java.util.StringTokenizer;

public class Disposition {
	// The MDN has been automatically generated
	public static final String DISP_AUTOMATIC_ACTION = "automatic-action/MDN-sent-automatically; ";
	// The message has been processed successfully
	public static final String DISP_PROCESSED = DISP_AUTOMATIC_ACTION + "processed";
	// An error occurred during message processing
	public static final String DISP_PROCESSED_ERROR = DISP_PROCESSED + "/Error: ";
	// An unexpected error occurred during processing
	public static final String DISP_UNEXPECTED_ERROR = DISP_PROCESSED_ERROR + "unexpected-processing-error";
	// The message sender or receiver could not be authenticated
	public static final String DISP_AUTHENTICATION_FAILED = DISP_PROCESSED_ERROR + "authentication-failed";
	// The message could not be decrypted
	public static final String DISP_DECRYPTION_FAILED = DISP_PROCESSED_ERROR + "decryption-failed";
	// The message signature could not be verified
	public static final String DISP_SIGNATURE_FAILED = DISP_PROCESSED_ERROR + "integrity-check-failed";
	
	private String action;
	private String mdnAction;
	private String status;
	private String statusDescription;
	private String statusModifier;

	public Disposition(String disposition) throws DispositionException {
		super();

		if (disposition != null) {
			parseDisposition(disposition);
		}
	}

	public Disposition(String action, String mdnAction, String status,
			String statusModifier, String statusDescription) {
		super();
		this.action = action;
		this.mdnAction = mdnAction;
		this.status = status;
		this.statusModifier = statusModifier;
		this.statusDescription = statusDescription;
	}

	public Disposition(String action, String mdnAction, String status) {
		super();
		this.action = action;
		this.mdnAction = mdnAction;
		this.status = status;
		this.statusModifier = null;
		this.statusDescription = null;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getAction() {
		return action;
	}

	public void setMdnAction(String mdnAction) {
		this.mdnAction = mdnAction;
	}

	public String getMdnAction() {
		return mdnAction;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getStatus() {
		return status;
	}

	public void setStatusDescription(String statusDescription) {
		this.statusDescription = statusDescription;
	}

	public String getStatusDescription() {
		return statusDescription;
	}

	public void setStatusModifier(String statusModifier) {
		this.statusModifier = statusModifier;
	}

	public String getStatusModifier() {
		return statusModifier;
	}

	protected String makeDisposition() {
		if ((getAction() == null) && (getMdnAction() == null)
				&& (getStatus() == null) && (getStatusModifier() == null)
				&& (getStatusDescription() == null)) {
			return new String("");
		}

		StringBuffer dispBuf = new StringBuffer();
		dispBuf.append(getAction()).append("/").append(getMdnAction());
		dispBuf.append("; ").append(getStatus());

		if (getStatusModifier() != null) {
			dispBuf.append("/").append(getStatusModifier()).append(": ");

			if (getStatusDescription() != null) {
				dispBuf.append(getStatusDescription());
			}
		}

		return dispBuf.toString();
	}

	protected void parseDisposition(String disposition) throws DispositionException {
		StringTokenizer dispTokens = new StringTokenizer(disposition, "/;:",
				false);

		try {
			setAction(dispTokens.nextToken().toLowerCase());
			setMdnAction(dispTokens.nextToken().toLowerCase());
			setStatus(dispTokens.nextToken().trim().toLowerCase());
			setStatusModifier(null);
			setStatusDescription(null);

			if (dispTokens.hasMoreTokens()) {
				setStatusModifier(dispTokens.nextToken().toLowerCase());

				if (dispTokens.hasMoreTokens()) {
					setStatusDescription(dispTokens.nextToken().trim()
							.toLowerCase());
				}
			}
		} catch (NoSuchElementException nsee) {
			throw new DispositionException("Invalid disposition format: "
					+ disposition);
		}
	}

	public String toString() {
		return makeDisposition();
	}

	public boolean isWarning() {
		String statusMod = getStatusModifier();

		return ((statusMod != null) && statusMod.equalsIgnoreCase("warning"));
	}

	public boolean isError() {
		String status = getStatus();

		if ((status != null) && !status.equalsIgnoreCase("processed")) {
			return true;
		}

		String statusMod = getStatusModifier();

		return ((statusMod != null) && statusMod.equalsIgnoreCase("error"));
	}
}