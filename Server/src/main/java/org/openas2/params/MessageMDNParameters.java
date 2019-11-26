package org.openas2.params;

import org.openas2.message.MessageMDN;

import java.util.StringTokenizer;


public class MessageMDNParameters extends ParameterParser {
    public static final String KEY_MESSAGE = "msg";
    public static final String KEY_SENDER = "sender";
    public static final String KEY_RECEIVER = "receiver";
    public static final String KEY_TEXT = "text";
    public static final String KEY_ATTRIBUTES = "attributes";
    public static final String KEY_HEADERS = "headers";
    private MessageMDN target;

    public MessageMDNParameters(MessageMDN target) {
        super();
        this.target = target;
    }

    public void setParameter(String key, String value) throws InvalidParameterException {
        MessageMDN target = getTarget();
        StringTokenizer keyParts = new StringTokenizer(key, ".", false);

        if (keyParts.countTokens() < 2) {
            throw new InvalidParameterException("Invalid key format", this, "key", key);
        }

        String area = keyParts.nextToken();

        if (area.equals(KEY_MESSAGE)) {
            if (keyParts.countTokens() < 3) {
                throw new InvalidParameterException("Invalid key format", this, "key", key);
            }

            String messageKey = keyParts.nextToken() + "." + keyParts.nextToken();

            if (target.getMessage() == null) {
                throw new InvalidParameterException("MDN has no message", this, "key", key);
            }

            new MessageParameters(target.getMessage()).setParameter(messageKey, value);
        } else {
            String areaID = keyParts.nextToken();

            if (area.equals(KEY_TEXT)) {
                target.setText(value);
            } else if (area.equals(KEY_ATTRIBUTES)) {
                target.setAttribute(areaID, value);
            } else if (area.equals(KEY_HEADERS)) {
                target.setHeader(areaID, value);
            } else {
                throw new InvalidParameterException("Invalid area in key", this, "key", key);
            }
        }
    }

    public String getParameter(String key) throws InvalidParameterException {
        MessageMDN target = getTarget();
        StringTokenizer keyParts = new StringTokenizer(key, ".", false);

        if (keyParts.countTokens() > 2) {
            keyParts.nextToken();

            String msgKey = keyParts.nextToken() + "." + keyParts.nextToken();

            return new MessageParameters(target.getMessage()).getParameter(msgKey);
        }

        if (keyParts.countTokens() < 2) {
            throw new InvalidParameterException("Invalid key format", this, "key", key);
        }

        String area = keyParts.nextToken();
        String areaID = keyParts.nextToken();

        if (area.equals(KEY_SENDER)) {
            return getTarget().getPartnership().getSenderID(areaID);
        } else if (area.equals(KEY_RECEIVER)) {
            return getTarget().getPartnership().getReceiverID(areaID);
        } else if (area.equals(KEY_TEXT)) {
            return target.getText();
        } else if (area.equals(KEY_ATTRIBUTES)) {
            return target.getAttribute(areaID);
        } else if (area.equals(KEY_HEADERS)) {
            return target.getHeader(areaID);
        } else {
            throw new InvalidParameterException("Invalid area in key", this, "key", key);
        }
    }

    public void setTarget(MessageMDN messageMDN) {
        target = messageMDN;
    }

    public MessageMDN getTarget() {
        return target;
    }
}
