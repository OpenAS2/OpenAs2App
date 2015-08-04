
package org.openas2.partner;


public interface AS2Partnership {
	public static final String PID_AS2 = "as2_id"; // AS2 ID
	public static final String PA_AS2_URL = "as2_url"; // URL destination for AS2 transactions 
	public static final String PA_AS2_MDN_TO = "as2_mdn_to"; // Fill in to request an MDN for a transaction
	public static final String PA_AS2_MDN_OPTIONS = "as2_mdn_options"; // Requested options for returned MDN
	public static final String PA_AS2_RECEIPT_OPTION = "as2_receipt_option"; // URL destination for an async MDN
	public static final String PA_MESSAGEID = "messageid";  // format to use for message-id if not default
}
