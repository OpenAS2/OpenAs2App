
package org.openas2.partner;

public interface ASXPartnership {
	public static final String PA_MDN_SUBJECT = "mdnsubject"; // Subject sent in MDN messages
	/*
	 * If set and an error occurs while processing a document, an error MDN will not be sent. This
	 * flag was made because some AS2 products don't provide email or some other external notification
	 * when an error MDN is received. 
	 */
	public static final String PA_BLOCK_ERROR_MDN = "blockerrormdn";   
}
