package org.openas2.lib.message;

import java.io.IOException;
import java.io.InputStream;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;


public class AS1Message extends EDIINTMessage {
    public AS1Message() {
        super();
    }

    public AS1Message(MimeBodyPart data, String contentType)
        throws MessagingException {
        super(data, contentType);
    }

    public AS1Message(InputStream in) throws IOException, MessagingException {
        super(in);
    }
    
    public String getSenderIDHeader() {
        return "From";
    }
    
    public String getReceiverIDHeader() {
        return "To";
    }
    
    public void setTo(String to) {
        setHeader("To", to);
    }

    public String getTo() {
        return getHeader("To");
    }
}
