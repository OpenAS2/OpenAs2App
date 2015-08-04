package org.openas2.lib.message;

import java.io.IOException;
import java.io.InputStream;

import javax.activation.CommandMap;
import javax.activation.MailcapCommandMap;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

public abstract class EDIINTMessageMDN extends EDIINTMessage {
	static {
		// make sure MDN content types are added to JavaMail
		MailcapCommandMap mc = (MailcapCommandMap) CommandMap.getDefaultCommandMap();
	    mc.addMailcap(
	        "message/disposition-notification;; x-java-content-handler=org.openas2.lib.util.javamail.DispositionDataContentHandler");
	    CommandMap.setDefaultCommandMap(mc);	    
	}
	
    public EDIINTMessageMDN() {
        super();
    }

    public EDIINTMessageMDN(MimeBodyPart data, String contentType)
        throws MessagingException {
        super(data, contentType);
    }

    public EDIINTMessageMDN(InputStream in)
        throws IOException, MessagingException {
        super(in);
    }
    
    public EDIINTMessageMDN createMDN() {
    	return null;
    }
    
    public abstract MDNData getMDNData();
    
    public void setData(MimeBodyPart data) throws MessagingException {
        super.setData(data);
        MDNData mdnData = getMDNData();
        mdnData.update(data);
        mdnData.setDirty(false);
    }

    public MimeBodyPart getData() throws MessagingException {
    	MDNData mdnData = getMDNData();
    	if (mdnData.isDirty()) {
    		MimeBodyPart report = mdnData.getData();
    		super.setData(report);
    		mdnData.setDirty(false);
    	}
        return super.getData();
    }
}
