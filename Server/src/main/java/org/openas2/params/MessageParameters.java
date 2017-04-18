package org.openas2.params;

import java.util.StringTokenizer;

import javax.mail.internet.ParseException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.message.Message;


public class MessageParameters extends ParameterParser {
	public static final String KEY_SENDER = "sender";
	public static final String KEY_RECEIVER = "receiver";
	public static final String KEY_ATTRIBUTES = "attributes";
	public static final String KEY_HEADERS = "headers";
	public static final String KEY_CONTENT_FILENAME = "content-disposition";
	private Message target;

	private Log logger = LogFactory.getLog(MessageParameters.class.getSimpleName());

	public MessageParameters(Message target) {
		super();
		this.target = target;
	}

	public void setParameter(String key, String value)
		throws InvalidParameterException {
		StringTokenizer keyParts = new StringTokenizer(key, ".", false);

		if (keyParts.countTokens() != 2) {
			throw new InvalidParameterException("Invalid key format", this, key, null);
		}

		String area = keyParts.nextToken();
		String areaID = keyParts.nextToken();

		if (area.equals(KEY_SENDER)) {
			getTarget().getPartnership().setSenderID(areaID, value);
		} else if (area.equals(KEY_RECEIVER)) {
			getTarget().getPartnership().setReceiverID(areaID, value);
		} else if (area.equals(KEY_ATTRIBUTES)) {
			getTarget().setAttribute(areaID, value);
		} else if (area.equals(KEY_HEADERS)) {
			getTarget().setHeader(areaID, value);
		} else {
			throw new InvalidParameterException("Invalid area in key", this, key, null);
		}
	}

	public String getParameter(String key) throws InvalidParameterException {
		StringTokenizer keyParts = new StringTokenizer(key, ".", false);

		if (keyParts.countTokens() != 2) {
			throw new InvalidParameterException("Invalid key format", this, key, null);
		}

		String area = keyParts.nextToken();
		String areaID = keyParts.nextToken();

		if (area.equals(KEY_SENDER)) {
			return getTarget().getPartnership().getSenderID(areaID);
		} else if (area.equals(KEY_RECEIVER)) {
			return getTarget().getPartnership().getReceiverID(areaID);
		} else if (area.equals(KEY_ATTRIBUTES)) {
			return getTarget().getAttribute(areaID);
		} else if (area.equals(KEY_HEADERS)) {
			return getTarget().getHeader(areaID);
		} else if (area.equals(KEY_CONTENT_FILENAME) && areaID.equals("filename")) {
			String filename = "noContentDispositionFilename";
			String s = null;
			try
			{
				s = getTarget().extractPayloadFilename();
			} catch (ParseException e)
			{
				logger.warn("Failed to extract filename from content-disposition: " + org.openas2.logging.Log.getExceptionMsg(e), e);
			}
			if (s == null || s.length() < 1)
				s = getTarget().getPayloadFilename();
			if (s != null && s.length() > 0)
				return s;
			return filename;
		} else {
			throw new InvalidParameterException("Invalid area in key", this, key, null);
		}
	}

	public void setTarget(Message message) {
		target = message;
	}

	public Message getTarget() {
		return target;
	}
}
