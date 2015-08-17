package org.openas2.message;

import org.openas2.params.CompositeParameters;
import org.openas2.params.DateParameters;
import org.openas2.params.InvalidParameterException;
import org.openas2.params.MessageParameters;
import org.openas2.params.ParameterParser;
import org.openas2.params.RandomParameters;
import org.openas2.partner.AS2Partnership;
import org.openas2.partner.Partnership;


public class AS2Message extends BaseMessage implements Message {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public static final String PROTOCOL_AS2 = "as2";
	
    public String getProtocol() {
        return PROTOCOL_AS2;
    }

    public String generateMessageID() {
    	CompositeParameters params = 
    		new CompositeParameters(false).
    			add("date", new DateParameters()).
    			add("msg", new MessageParameters(this)).
    			add("rand", new RandomParameters());
        
    	String idFormat = getPartnership().getAttribute(AS2Partnership.PA_MESSAGEID);
    	if (idFormat == null) {
    		idFormat = "OPENAS2-$date.ddMMyyyyHHmmssZ$-$rand.1234$@$msg.sender.as2_id$_$msg.receiver.as2_id$";
    	}
    	StringBuffer messageId = new StringBuffer();
    	messageId.append("<");
    	try {
    		messageId.append(ParameterParser.parse(idFormat, params));
    	}
    	catch (InvalidParameterException e) {
    		messageId.append(idFormat); 	// useless, but what to do?
    	}
    	messageId.append(">");
        return messageId.toString();
    }

    public boolean isRequestingMDN() {
        Partnership p = getPartnership();
        boolean requesting = ((p.getAttribute(AS2Partnership.PA_AS2_MDN_TO) != null) || (p
                .getAttribute(AS2Partnership.PA_AS2_MDN_OPTIONS) != null));
        boolean requested = ((getHeader("Disposition-Notification-To") != null) || (getHeader("Disposition-Notification-Options") != null));

        return requesting || requested;
    }
    public boolean isRequestingAsynchMDN() {
        Partnership p = getPartnership();
        boolean requesting = ((p.getAttribute(AS2Partnership.PA_AS2_MDN_TO) != null || 
        		p.getAttribute(AS2Partnership.PA_AS2_MDN_OPTIONS) != null)
        		&& p.getAttribute(AS2Partnership.PA_AS2_RECEIPT_OPTION) != null);
        boolean requested = ((getHeader("Disposition-Notification-To") != null || 
        		              (getHeader("Disposition-Notification-Options") != null))
        		    && (getHeader("Receipt-Delivery-Option") != null));

        return requesting || requested;
    }
    
    public String getAsyncMDNurl() {
    	return getHeader("Receipt-Delivery-Option");
    }

}