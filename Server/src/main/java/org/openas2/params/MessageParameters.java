package org.openas2.params;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.message.Message;
import org.openas2.util.Properties;

import javax.mail.internet.ParseException;
import java.util.StringTokenizer;

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

    public void setParameter(String key, String value) throws InvalidParameterException {
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
            String s = null;
            try {
                s = getTarget().extractPayloadFilename();
            } catch (ParseException e) {
                logger.warn("Failed to extract filename from content-disposition: " + org.openas2.logging.Log.getExceptionMsg(e), e);
            }
            if (s == null || s.length() < 1) {
                s = getTarget().getPayloadFilename();
            }
            if (s != null && s.length() > 0) {
                return s;
            }
            // If it gets to here then the sender did not send a filename so...
            String filename = Properties.getProperty(Properties.AS2_RX_MESSAGE_FILENAME_FALLBACK, null);
            if (filename == null) {
                return getTarget().getMessageID();
            } else {
                CompositeParameters parser = new CompositeParameters(false).add("date", new DateParameters()).add("msg", new MessageParameters(getTarget())).add("rand", new RandomParameters());
                return ParameterParser.parse(filename, parser);
            }
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
