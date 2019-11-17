package org.openas2.partner;

import org.openas2.util.Properties;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;


public class Partnership implements Serializable {

    /* identifier to define if context is sending or receiving */
    public static final String PTYPE_SENDER = "sender"; // Sender partner type
    public static final String PTYPE_RECEIVER = "receiver"; // Receiver partner type

    /* partner definition attributes */
    public static final String PID_AS2 = "as2_id"; // AS2 ID
    public static final String PID_X509_ALIAS = "x509_alias"; // Alias to an X509 Certificate
    public static final String PID_EMAIL = "email"; // Email address

    /* partnership definition attributes */
    public static final String PA_SUBJECT = "subject"; // Subject sent in messages    
    public static final String PA_CONTENT_TYPE = "content_type"; // optional content type for mime parts
    public static final String PA_CONTENT_TRANSFER_ENCODING = "content_transfer_encoding"; // optional content transfer enc value
    public static final String PA_SET_CONTENT_TRANSFER_ENCODING_HTTP = "set_content_transfer_encoding_http_header"; // See as an HTTP header
    public static final String PA_REMOVE_PROTECTION_ATTRIB = "remove_cms_algorithm_protection_attrib"; // Some AS2 systems do not support the attribute
    public static final String PA_SET_CONTENT_TRANSFER_ENCODING_OMBP = "set_content_transfer_encoding_on_outer_mime_bodypart"; // optional content transfer enc value
    public static final String PA_RESEND_REQUIRES_NEW_MESSAGE_ID = "resend_requires_new_message_id"; // list of nme/value pairs for setting custom mime headers
    public static final String PA_COMPRESSION_TYPE = "compression";
    public static final String PA_SIGNATURE_ALGORITHM = "sign";
    public static final String PA_ENCRYPTION_ALGORITHM = "encrypt";
    public static final String PA_AS2_URL = "as2_url"; // URL destination for AS2 transactions
    public static final String PA_AS2_MDN_TO = "as2_mdn_to"; // Fill in to request an MDN for a transaction
    public static final String PA_AS2_MDN_OPTIONS = "as2_mdn_options"; // Requested options for returned MDN
    public static final String PA_AS2_RECEIPT_OPTION = "as2_receipt_option"; // URL destination for an async MDN
    public static final String PA_RESEND_MAX_RETRIES = "resend_max_retries";  // format to use for message-id if not default
    public static final String PA_CUSTOM_MIME_HEADERS = "custom_mime_headers"; // list of nme/value pairs for setting custom mime headers
    public static final String PA_ADD_CUSTOM_MIME_HEADERS_TO_HTTP = "add_custom_mime_headers_to_http"; // Add the custom mime headers (if any) to HTTP header if "true"
    public static final String PA_CUSTOM_MIME_HEADER_NAMES_FROM_FILENAME = "custom_mime_header_names_from_filename"; // List of header names to be set from parsed filename
    public static final String PA_CUSTOM_MIME_HEADER_NAME_DELIMITERS_IN_FILENAME = "custom_mime_header_name_delimiters_in_filename"; // Delimiters to split filename into values
    public static final String PA_CUSTOM_MIME_HEADER_NAMES_REGEX_ON_FILENAME = "custom_mime_header_names_regex_on_filename"; // Regex to split filename into values
    public static final String PAIB_NAMES_FROM_FILENAME = "attribute_names_from_filename"; // List of attribute names to be set from parsed filename
    public static final String PAIB_VALUES_REGEX_ON_FILENAME = "attribute_values_regex_on_filename"; // Regex to split filename into values
    public static final String PA_HTTP_NO_CHUNKED_MAX_SIZE = "no_chunked_max_size"; // Disables chunked HTTP transfer when file size is set larger as 0

    /*
     * If set and an error occurs while processing a document, an error MDN will not be sent. This
     * flag was made because some AS2 products don't provide email or some other external notification
     * when an error MDN is received.
     */
    public static final String PA_BLOCK_ERROR_MDN = "blockerrormdn";


    private static final long serialVersionUID = -8365608387462470629L;
    private Map<String, String> attributes;
    private Map<String, Object> receiverIDs;
    private Map<String, Object> senderIDs;
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAttribute(String id, String value) {
        getAttributes().put(id, value);
    }

    public String getAttribute(String id) {
        return getAttributes().get(id);
    }

    public Map<String, String> getAttributes() {
        if (attributes == null) {
            attributes = new HashMap<String, String>();
        }

        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public String getAttributeOrProperty(String id, String defaultValue) {
        String val = getAttributes().get(id);
        if (val == null || val.length() < 1) {
            val = Properties.getProperty(id, defaultValue);
        }
        return val;
    }

    public void setReceiverID(String id, String value) {
        getReceiverIDs().put(id, value);
    }

    public String getReceiverID(String id) {
        return (String) getReceiverIDs().get(id);
    }

    public Map<String, Object> getReceiverIDs() {
        if (receiverIDs == null) {
            receiverIDs = new HashMap<String, Object>();
        }

        return receiverIDs;
    }

    public void setReceiverIDs(Map<String, Object> receiverIDs) {
        this.receiverIDs = receiverIDs;
    }

    public void setSenderID(String id, String value) {
        getSenderIDs().put(id, value);
    }

    public String getSenderID(String id) {
        return (String) getSenderIDs().get(id);
    }

    public Map<String, Object> getSenderIDs() {
        if (senderIDs == null) {
            senderIDs = new HashMap<String, Object>();
        }

        return senderIDs;
    }

    public void setSenderIDs(Map<String, Object> senderIDs) {
        this.senderIDs = senderIDs;
    }

    public boolean matches(Partnership partnership) {
        Map<String, Object> senderIDs = partnership.getSenderIDs();
        Map<String, Object> receiverIDs = partnership.getReceiverIDs();

        if (compareIDs(senderIDs, getSenderIDs())) {
            return true;
        } else {
            return compareIDs(receiverIDs, getReceiverIDs());
        }

    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("Partnership " + getName());
        buf.append(" Sender IDs = ").append(getSenderIDs());
        buf.append(" Receiver IDs = ").append(getReceiverIDs());
        buf.append(" Attributes = ").append(getAttributes());

        return buf.toString();
    }

    protected boolean compareIDs(Map<String, Object> ids, Map<String, Object> compareTo) {
        Set<Entry<String, Object>> idSet = ids.entrySet();
        Iterator<Entry<String, Object>> it = idSet.iterator();

        if (!it.hasNext()) {
            return false;
        }

        Map.Entry<String, Object> currentId;
        Object currentValue;
        Object compareValue;

        while (it.hasNext()) {
            currentId = it.next();
            currentValue = currentId.getValue();
            compareValue = compareTo.get(currentId.getKey());

            if ((currentValue != null) && (compareValue == null)) {
                return false;
            } else if ((currentValue == null) && (compareValue != null)) {
                return false;
            } else if (!currentValue.equals(compareValue)) {
                return false;
            }
        }

        return true;
    }

    public void copy(Partnership partnership) {
        if (partnership.getName() != null) {
            setName(partnership.getName());
        }
        getSenderIDs().putAll(partnership.getSenderIDs());
        getReceiverIDs().putAll(partnership.getReceiverIDs());
        getAttributes().putAll(partnership.getAttributes());
    }

    public boolean isAsyncMDN() {
        String receiptOptions = getAttribute(Partnership.PA_AS2_RECEIPT_OPTION);
        return (receiptOptions != null && receiptOptions.length() > 0);
    }

    public boolean isSetTransferEncodingOnInitialBodyPart() {
        // The default must be true and allow it to be disabled by explicit config
        String setTxfrEncoding = getAttribute("set_transfer_encoding_on_inital_body_part");
        return (setTxfrEncoding == null || "true".equals(setTxfrEncoding));
    }

    public boolean isPreventCanonicalization() {
        String preventCanonicalization = getAttribute("prevent_canonicalization_for_mic");
        return (preventCanonicalization != null && "true".equals(preventCanonicalization));
    }

    public boolean isRenameDigestToOldName() {
        String removeDash = getAttribute("rename_digest_to_old_name");
        return (removeDash != null && "true".equals(removeDash));
    }

    public boolean isRemoveCmsAlgorithmProtectionAttr() {
        return "true".equalsIgnoreCase(getAttribute(Partnership.PA_REMOVE_PROTECTION_ATTRIB));
    }

    public boolean isNoChunkedTransfer() {
        return (getNoChunkedMaxSize() > 0L);
    }

    public long getNoChunkedMaxSize() {
        long max = 0L;
        try {
            max = Long.valueOf(getAttribute(Partnership.PA_HTTP_NO_CHUNKED_MAX_SIZE));
        } catch (Exception ignored) {
        }
        return max;
    }

}
