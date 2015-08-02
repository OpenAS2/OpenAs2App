package org.openas2.params;

import java.io.File;
import java.util.StringTokenizer;

import javax.mail.internet.ContentDisposition;

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
			String s = target.getContentDisposition();
			if (s == null || s.length() < 1)
				return filename;
			try {
				if (logger.isDebugEnabled())
					logger.debug("Attempting filename extraction from Content-disposition: " + s);
				// TODO: This should be a case insensitive lookup per RFC6266
                String tmpFilename = null;

				ContentDisposition cd = new ContentDisposition(s);
				tmpFilename = cd.getParameter("filename");
				
				if (tmpFilename == null || tmpFilename.length() < 1)
				{
					/* Try to extract manually */
					int n = s.indexOf("filename=");
					if (n > -1)
					{
						tmpFilename = s.substring(n);
						tmpFilename = tmpFilename.replaceFirst("filename=", "");

						int n1 = tmpFilename.indexOf(",");
						if (n1 > -1)
							s = s.substring(0, n1 - 1);
						tmpFilename = tmpFilename.replaceAll("\"", "");
						s = s.trim();
					}
					else
					{
						/* Try just using file separator */
						int pos = s.lastIndexOf(File.separator);
						if (pos >= 0)
							tmpFilename = s.substring(pos + 1);
					}
                }

                if (tmpFilename != null && tmpFilename.length() > 0)
                {
    				if (logger.isDebugEnabled())
    					logger.debug("Filename extracted from Content-disposition: " + tmpFilename);
                	return tmpFilename;
                }

			} catch (Exception e) {
				e.printStackTrace();
			}
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
