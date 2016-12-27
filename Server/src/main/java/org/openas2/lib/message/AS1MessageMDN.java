package org.openas2.lib.message;

import java.io.IOException;
import java.io.InputStream;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;


public class AS1MessageMDN extends EDIINTMessageMDN {
	private AS1MDNData mdnData;
	
    public AS1MessageMDN() {
        super();
    }

    public AS1MessageMDN(MimeBodyPart data, String contentType)
        throws MessagingException {
        super(data, contentType);
    }

    public AS1MessageMDN(InputStream in) throws IOException, MessagingException {
        super(in);
    }
    
    public String getSenderIDHeader() {
        return "From";
    }
    
    public String getReceiverIDHeader() {
        return "To";
    }
    
    public MDNData getMDNData() {
    	if (mdnData == null) {
    		mdnData = new AS1MDNData(this);
    	}
    	return mdnData;
    }
    
    public void setTo(String to) {
        setHeader("To", to);
    }

    public String getTo() {
        return getHeader("To");
    }
}
