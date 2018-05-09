package org.openas2.message;

import java.io.Serializable;
import java.util.Map;

import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;

import org.openas2.params.InvalidParameterException;
import org.openas2.partner.Partnership;


public interface MessageMDN extends Serializable {
    public void setAttribute(String key, String value);

    public String getAttribute(String key);

    public void setAttributes(Map<String, String> attributes);

    public Map<String, String> getAttributes();

    public void setData(MimeBodyPart data);

    public MimeBodyPart getData();

    public void setHeader(String key, String value);

    public String getHeader(String key);

    public String getHeader(String key, String delimiter);

    public void setHeaders(InternetHeaders headers);

    public void copyHeaders(InternetHeaders srcHeaders);
    
    public InternetHeaders getHeaders();

    public void setHistory(DataHistory history);

    public DataHistory getHistory();

    public void setMessage(Message message);

    public Message getMessage();
    
    public void setMessageID(String messageID);

    public String getMessageID();
    
    public void setPartnership(Partnership partnership);

    public Partnership getPartnership();

    public void setText(String text);

    public String getText();
    
    public void addHeader(String key, String value);

    public String generateMessageID() throws InvalidParameterException;

    public void updateMessageID() throws InvalidParameterException;
}
