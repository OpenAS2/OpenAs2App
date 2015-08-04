package org.openas2.lib.message;

import java.io.IOException;
import java.io.InputStream;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.openas2.lib.Info;

public class AS2MessageMDN extends EDIINTMessageMDN {
	private AS2MDNData mdnData;
	
    public AS2MessageMDN() {
        super();
    }

    public AS2MessageMDN(MimeBodyPart data, String contentType)
        throws MessagingException {
        super(data, contentType);
    }

    public AS2MessageMDN(InputStream in) throws IOException, MessagingException {
        super(in);
    }
    
    public String getSenderIDHeader() {
        return "AS2-From";
    }
    
    public String getReceiverIDHeader() {
        return "AS2-To";
    }
    
    public MDNData getMDNData() {
    	if (mdnData == null) {
    		mdnData = new AS2MDNData(this);
    	}
    	return mdnData;
    }
    
    public void setAS2From(String from) {
        setHeader("AS2-From", from);
    }

    public String getAS2From() {
        return getHeader("AS2-From");
    }

    public void setAS2To(String to) {
        setHeader("AS2-To", to);
    }

    public String getAS2To() {
        return getHeader("AS2-To");
    }
    
    public void setAS2Version(String version) {
        setHeader("AS2-Version", version);
    }

    public String getAS2Version() {
        return getHeader("AS2-Version");
    }
    
    public void setDefaults() {
        super.setDefaults();
        setAS2Version("1.1");
        setServer(Info.NAME_VERSION);
    }
    
    public void setServer(String server) {
        setHeader("Server", server);
    }

    public String getServer() {
        return getHeader("Server");
    }
}
