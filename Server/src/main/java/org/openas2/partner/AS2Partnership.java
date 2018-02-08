package org.openas2.partner;

public interface AS2Partnership {

	public static final String PID_AS2 = "as2_id"; // AS2 ID
	public static final String PA_AS2_URL = "as2_url"; // URL destination for AS2 transactions 
	public static final String PA_AS2_MDN_TO = "as2_mdn_to"; // Fill in to request an MDN for a transaction
	public static final String PA_AS2_MDN_OPTIONS = "as2_mdn_options"; // Requested options for returned MDN
	public static final String PA_AS2_RECEIPT_OPTION = "as2_receipt_option"; // URL destination for an async MDN
	public static final String PA_MESSAGEID = "messageid";  // format to use for message-id if not default
	public static final String PA_RESEND_MAX_RETRIES = "resend_max_retries";  // format to use for message-id if not default
	public static final String PA_CUSTOM_MIME_HEADERS = "custom_mime_headers"; // list of nme/value pairs for setting custom mime headers
	public static final String PA_PREVENT_CANONICALIZATION_FOR_MIC = "prevent_canonicalization_for_mic";
	public static final String PA_ADD_CUSTOM_MIME_HEADERS_TO_HTTP = "add_custom_mime_headers_to_http"; // Add the custom mime headers (if any) to HTTP header if "true"
	public static final String PA_CUSTOM_MIME_HEADER_NAMES_FROM_FILENAME = "custom_mime_header_names_from_filename"; // List of header names to be set from parsed filename
	public static final String PA_CUSTOM_MIME_HEADER_NAME_DELIMITERS_IN_FILENAME = "custom_mime_header_name_delimiters_in_filename"; // Delimiters to split filename into values
	public static final String PA_CUSTOM_MIME_HEADER_NAMES_REGEX_ON_FILENAME = "custom_mime_header_names_regex_on_filename"; // Regex to split filename into values

	public static final String PA_ATTRIB_NAMES_FROM_FILENAME = "attribute_names_from_filename"; // List of attribute names to be set from parsed filename
	public static final String PA_ATTRIB_VALUES_REGEX_ON_FILENAME = "attribute_values_regex_on_filename"; // Regex to split filename into values
}
