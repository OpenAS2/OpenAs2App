package org.openas2.params;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openas2.message.Message;
import org.openas2.util.Properties;

import jakarta.mail.internet.ParseException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.StringTokenizer;

public class MessageParameters extends ParameterParser {
    public static final String KEY_SENDER = "sender";
    public static final String KEY_RECEIVER = "receiver";
    public static final String KEY_ATTRIBUTES = "attributes";
    public static final String KEY_HEADERS = "headers";
    public static final String KEY_CONTENT_FILENAME = "content-disposition";
    public static final String KEY_HASH = "hash";
    private Message target;

    private Logger logger = LoggerFactory.getLogger(MessageParameters.class);

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
                logger.warn("Failed to extract filename from content-disposition: " + org.openas2.util.Logging.getExceptionMsg(e), e);
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
        } else if (area.equals(KEY_HASH)) {
            return computeContentHash(areaID);
        } else {
            throw new InvalidParameterException("Invalid area in key", this, key, null);
        }
    }

    /**
     * Computes a hex digest of the message payload for use in a filename, e.g. to dedup identical
     * content. The spec is the hash algorithm (sha256, sha512, sha1 or md5) optionally followed by
     * an underscore and a truncation length, e.g. "sha256_16" for the first 16 hex characters.
     */
    private String computeContentHash(String spec) throws InvalidParameterException {
        String algorithm = spec;
        int length = -1;
        int underscore = spec.indexOf('_');
        if (underscore > 0) {
            algorithm = spec.substring(0, underscore);
            try {
                length = Integer.parseInt(spec.substring(underscore + 1));
            } catch (NumberFormatException e) {
                throw new InvalidParameterException("Invalid hash truncation length", this, spec, null);
            }
        }
        String jdkAlgorithm;
        switch (algorithm.toLowerCase()) {
        case "md5":
            jdkAlgorithm = "MD5";
            break;
        case "sha1":
            jdkAlgorithm = "SHA-1";
            break;
        case "sha256":
            jdkAlgorithm = "SHA-256";
            break;
        case "sha512":
            jdkAlgorithm = "SHA-512";
            break;
        default:
            throw new InvalidParameterException("Unsupported hash algorithm (use md5, sha1, sha256 or sha512)", this, spec, null);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance(jdkAlgorithm);
            try (InputStream in = getTarget().getData().getInputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            String result = hex.toString();
            return (length > 0 && length < result.length()) ? result.substring(0, length) : result;
        } catch (Exception e) {
            throw new InvalidParameterException("Failed to compute content hash: " + org.openas2.util.Logging.getExceptionMsg(e), this, spec, null);
        }
    }

    public void setTarget(Message message) {
        target = message;
    }

    public Message getTarget() {
        return target;
    }
}
