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

    public String generateMessageID() throws InvalidParameterException {
    	CompositeParameters params = 
    		new CompositeParameters(false).
    			add("date", new DateParameters()).
    			add("msg", new MessageParameters(this)).
    			add("rand", new RandomParameters());
        
    	String idFormat = getPartnership().getAttribute(AS2Partnership.PA_MESSAGEID);
    	if (idFormat == null) {
    		idFormat = "OPENAS2-$date.ddMMyyyyHHmmssZ$-$rand.1234$@$msg.sender.as2_id$_$msg.receiver.as2_id$";
    	}
  		return ParameterParser.parse(idFormat, params);
    }

    public boolean isRequestingMDN() {
    	// Per the AS2 protocol sending an MDN response should be determined by the header in the received message not by config
    	// TODO: Protocol specifies only the "Disposition-Notification-To" indicates MDN request so why is it checking the other one?!?!
        return ((getHeader("Disposition-Notification-To") != null) || (getHeader("Disposition-Notification-Options") != null));
    }

    public boolean isConfiguredForMDN() {
    	Partnership p = getPartnership();
        return ((p.getAttribute(AS2Partnership.PA_AS2_MDN_TO) != null) 
        		&& (p.getAttribute(AS2Partnership.PA_AS2_MDN_OPTIONS) != null));

    }

    public boolean isRequestingAsynchMDN() {
    	// Per the AS2 protocol, sending an ASYNC MDN response should be determined by the header in the received message not by config
        return (getHeader("Receipt-Delivery-Option") != null);
    }
    
    public boolean isConfiguredForAsynchMDN() {
    	Partnership p = getPartnership();
        return (p.getAttribute(AS2Partnership.PA_AS2_RECEIPT_OPTION) != null);
    }
    
    public String getAsyncMDNurl() {
    	return getHeader("Receipt-Delivery-Option");
    }

}