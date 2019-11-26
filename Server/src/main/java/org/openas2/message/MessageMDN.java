package org.openas2.message;

import org.openas2.params.InvalidParameterException;
import org.openas2.partner.Partnership;

import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import java.io.Serializable;
import java.util.Map;


public interface MessageMDN extends Serializable {
    void setAttribute(String key, String value);

    String getAttribute(String key);

    void setAttributes(Map<String, String> attributes);

    Map<String, String> getAttributes();

    void setData(MimeBodyPart data);

    MimeBodyPart getData();

    void setHeader(String key, String value);

    String getHeader(String key);

    String getHeader(String key, String delimiter);

    void setHeaders(InternetHeaders headers);

    void copyHeaders(InternetHeaders srcHeaders);

    InternetHeaders getHeaders();

    void setHistory(DataHistory history);

    DataHistory getHistory();

    void setMessage(Message message);

    Message getMessage();

    void setMessageID(String messageID);

    String getMessageID();

    void setPartnership(Partnership partnership);

    Partnership getPartnership();

    void setText(String text);

    String getText();

    void addHeader(String key, String value);

    String generateMessageID() throws InvalidParameterException;

    void updateMessageID() throws InvalidParameterException;
}
