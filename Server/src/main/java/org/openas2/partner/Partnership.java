package org.openas2.partner;

import org.openas2.OpenAS2Exception;
import org.openas2.cert.CertificateNotFoundException;
import org.openas2.util.FileUtil;
import org.openas2.util.Properties;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;


public class Partnership implements Serializable {

    /* Partnership configuration nodes */
    public static final String PNODE_PARTNER = "partner"; //The node that encapsulates a single partner definition
    public static final String PNODE_PARTNERSHIP = "partnership"; //The node that encapsulates a single partnership definition

    /* identifier to define if context is sending or receiving */
    public static final String PTYPE_SENDER = "sender"; // Sender partner type
    public static final String PTYPE_RECEIVER = "receiver"; // Receiver partner type

    /* Partnership configuration nodes */
    public static final String PCFG_POLLER = "pollerConfig"; // Directory poller config node
    public static final String PCFG_SENDER = PTYPE_SENDER; // Sender config node
    public static final String PCFG_RECEIVER = PTYPE_RECEIVER; // Receiver config node

    /* partner definition attributes */
    public static final String PID_NAME = "name"; // Partner name
    public static final String PID_AS2 = "as2_id"; // AS2 ID
    public static final String PID_X509_ALIAS = "x509_alias"; // Alias to an X509 Certificate
    public static final String PID_X509_ALIAS_FALLBACK = "x509_alias_fallback"; // Fallback alias to an X509 Certificate
    public static final String PID_EMAIL = "email"; // Email address

    /* partnership definition attributes */
    public static final String PA_SUBJECT = "subject"; // Subject sent in messages    
    public static final String PA_CONTENT_TYPE = "content_type"; // optional content type for mime parts
    public static final String PA_USE_DYNAMIC_CONTENT_TYPE_MAPPING = "use_dynamic_content_type_mapping"; // use file extension to Content-Type mapping
    public static final String PA_CONTENT_TYPE_MAPPING_FILE = "content_type_mapping_file"; // file containing file extension to Content-Type mapping
    public static final String PA_CONTENT_TRANSFER_ENCODING = "content_transfer_encoding"; // optional content transfer enc value
    public static final String PA_SET_CONTENT_TRANSFER_ENCODING_HTTP = "set_content_transfer_encoding_http_header"; // See as an HTTP header
    public static final String PA_REMOVE_PROTECTION_ATTRIB = "remove_cms_algorithm_protection_attrib"; // Some AS2 systems do not support the attribute
    public static final String PA_SET_CONTENT_TRANSFER_ENCODING_OMBP = "set_content_transfer_encoding_on_outer_mime_bodypart"; // optional content transfer enc value
    public static final String PA_RESEND_REQUIRES_NEW_MESSAGE_ID = "resend_requires_new_message_id"; // list of name/value pairs for setting custom mime headers
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
    public static final String PA_HTTP_NO_CHUNKED_MAX_SIZE = "no_chunked_max_size"; // Disables chunked HTTP transfer when file size is set larger than the value in this param
    public static final String PA_HTTP_PREVENT_CHUNKING = "prevent_chunking"; // Will try to force the send without using chunked HTTP transfer
    public static final String PA_STORE_RECEIVED_FILE_TO = "store_received_file_to"; // Allows overriding the MessageFileModule "filename" parameter per partnership
    public static final String PA_REJECT_UNSIGNED_MESSAGES = "reject_unsigned_messages"; // Reject any messages that are sent to the partnership unisgned
    public static final String PA_SPLIT_FILE_THRESHOLD_SIZE_IN_BYTES = "split_file_threshold_size_in_bytes";
    public static final String PA_SPLIT_FILE_CONTAINS_HEADER_ROW = "split_file_contains_header_row";
    public static final String PA_SPLIT_FILE_NAME_PREFIX = "split_file_name_prefix";
    // A hopefully temporary key to maintain backwards compatibility
    public static final String USE_NEW_CERTIFICATE_LOOKUP_MODE = "use_new_certificate_lookup_mode";

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
    private java.util.Properties overrideContentTypeFromFileExtensionMap = null;
    private java.util.Properties contentTypeFromFileExtensionMap = null;
    private boolean useDynamicContentTypeLookup = false;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setAttribute(String id, String value) {
        getAttributes().put(id, value);
    }

    /** Gets the value of the attribute for the provided key
     * @param id
     * @return Returns the value to which the specified key is mapped, or null if this map contains no mapping for the key.
     */
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

     public boolean isUseDynamicContentTypeLookup() {
        return useDynamicContentTypeLookup;
    }

    /** This method is called if the partnership is configured to use dynamic mappings.
     *  It will check that there are either system or partnership specific mappings available
     *  load them into a partnership mapping cache.
     * @param useDynamicContentTypeLookup - if true then enable dynamic mapping
     * @throws OpenAS2Exception
     * @throws IOException
     */
    public void setUseDynamicContentTypeLookup(boolean useDynamicContentTypeLookup) throws OpenAS2Exception, IOException {
        if (useDynamicContentTypeLookup) {
            // Make sure there is a lookup available
            // If there is a partnership specific override then make the partnership use 
            // that otherwise point it at the system mapping if available
            String contentTypeMapFilename = getAttribute(Partnership.PA_CONTENT_TYPE_MAPPING_FILE);
            if (contentTypeMapFilename != null) {
                if (Properties.getContentTypeMap() != null) {
                    // Copy the system level mapping in first then override/add the custom mappings
                    overrideContentTypeFromFileExtensionMap = new java.util.Properties();
                    overrideContentTypeFromFileExtensionMap.putAll(Properties.getContentTypeMap());
                      overrideContentTypeFromFileExtensionMap.putAll(FileUtil.loadProperties(contentTypeMapFilename));
                  } else {
                    // Get the override map
                    setOverrideContentTypeFromFileExtension(FileUtil.loadProperties(contentTypeMapFilename));
                  }
                // Configure this partnership to use the override lookup
                contentTypeFromFileExtensionMap = overrideContentTypeFromFileExtensionMap;
            } else {
                // Set the partnership to use the global map
                contentTypeFromFileExtensionMap = Properties.getContentTypeMap();
            }
            // If there is no map to do the lookup throw an excpetion
            if (this.contentTypeFromFileExtensionMap == null) {
                throw new OpenAS2Exception("Trying to use Content-Type mapping functionality but no mappings loaded.");
            }
        }
        this.useDynamicContentTypeLookup = useDynamicContentTypeLookup;
    }

    public String getContentTypeFromFileExtension(String key) {
        if (contentTypeFromFileExtensionMap == null) {
            return null;
        }
        return (String) contentTypeFromFileExtensionMap.get(key);
     }

     public void setOverrideContentTypeFromFileExtension(java.util.Properties contentTypeFromFileExtension) {
         this.overrideContentTypeFromFileExtensionMap = contentTypeFromFileExtension;
     }

   public String getAlias(String partnershipType) throws OpenAS2Exception {
        String alias = null;

        if (partnershipType == PTYPE_RECEIVER) {
            alias = getReceiverID(Partnership.PID_X509_ALIAS);
        } else if (partnershipType == PTYPE_SENDER) {
            alias = getSenderID(Partnership.PID_X509_ALIAS);
        }

        if (alias == null) {
            throw new CertificateNotFoundException(
                 "Lookup failed for X509 alias for AS2 ID: " + getReceiverID(Partnership.PID_AS2 + " :: Partnership type: " + partnershipType),
                 null
            );
        }

        return alias;
    }

    public String getAliasFallback(String partnershipType) throws OpenAS2Exception {
        String alias = null;

        if (partnershipType == PTYPE_RECEIVER) {
            alias = getReceiverID(Partnership.PID_X509_ALIAS_FALLBACK);
        } else if (partnershipType == PTYPE_SENDER) {
            alias = getSenderID(Partnership.PID_X509_ALIAS_FALLBACK);
        }
        // The fallback is not guaranteed to be there so return null if not set
        return alias;
    }

    public boolean isRejectUnsignedMessages() throws OpenAS2Exception {
        return getAttributeOrProperty(Partnership.PA_REJECT_UNSIGNED_MESSAGES, "false").equals("true");
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

    public long getNoChunkedMaxSize() {
        long max = 0L;
        try {
            max = Long.valueOf(getAttribute(Partnership.PA_HTTP_NO_CHUNKED_MAX_SIZE));
        } catch (Exception ignored) {
        }
        return max;
    }

    public boolean isPreventChunking(boolean defaultPreference) {
        String preventChunking = getAttribute(Partnership.PA_HTTP_PREVENT_CHUNKING);
        return preventChunking == null?defaultPreference:"true".equalsIgnoreCase(preventChunking);
    }
}
