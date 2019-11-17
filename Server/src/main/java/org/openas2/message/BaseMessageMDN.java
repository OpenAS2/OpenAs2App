package org.openas2.message;

import org.openas2.params.InvalidParameterException;
import org.openas2.partner.Partnership;

import javax.annotation.Nonnull;
import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;


public abstract class BaseMessageMDN implements MessageMDN {
    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private DataHistory history;
    private InternetHeaders headers;
    private Partnership partnership;
    private Map<String, String> attributes;
    @Nonnull
    private Message message;
    private MimeBodyPart data;
    private String text;

    public BaseMessageMDN(@Nonnull Message msg) {
        super();
        this.message = msg;
        msg.setMDN(this);
    }

    public void setAttribute(String key, String value) {
        getAttributes().put(key, value);
    }

    public String getAttribute(String key) {
        return getAttributes().get(key);
    }

    public Map<String, String> getAttributes() {
        if (attributes == null) {
            attributes = new HashMap<String, String>();
        }

        return attributes;
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public MimeBodyPart getData() {
        return data;
    }

    public void setData(MimeBodyPart data) {
        this.data = data;
    }

    public void setHeader(String key, String value) {
        getHeaders().setHeader(key, value);
    }

    public String getHeader(String key) {
        return getHeader(key, ", ");
    }

    public String getHeader(String key, String delimiter) {
        return getHeaders().getHeader(key, delimiter);
    }

    public InternetHeaders getHeaders() {
        if (headers == null) {
            headers = new InternetHeaders();
        }

        return headers;
    }

    public void setHeaders(InternetHeaders headers) {
        this.headers = headers;
    }

    public void copyHeaders(InternetHeaders srcHeaders) {
        Enumeration<Header> headerEn = srcHeaders.getAllHeaders();
        while (headerEn.hasMoreElements()) {
            Header header = headerEn.nextElement();
            setHeader(header.getName(), header.getValue());
        }
    }

    @Nonnull
    public Message getMessage() {
        return message;
    }

    @Override
    public void setMessage(@Nonnull Message message) {
        this.message = message;
    }

    public String getMessageID() {
        return getHeader("Message-ID");
    }

    public void setMessageID(String messageID) {
        setHeader("Message-ID", messageID);
    }

    public Partnership getPartnership() {
        if (partnership == null) {
            partnership = new Partnership();
        }

        return partnership;
    }

    public void setPartnership(Partnership partnership) {
        this.partnership = partnership;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public void addHeader(String key, String value) {
        getHeaders().addHeader(key, value);
    }

    public abstract String generateMessageID() throws InvalidParameterException;

    public DataHistory getHistory() {
        if (history == null) {
            history = new DataHistory();
        }

        return history;
    }

    public void setHistory(DataHistory history) {
        this.history = history;
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("MDN From:").append(getPartnership().getReceiverIDs());
        buf.append("To:").append(getPartnership().getSenderIDs());

        Enumeration<Header> headerEn = getHeaders().getAllHeaders();
        buf.append(System.getProperty("line.separator") + "Headers:{");

        while (headerEn.hasMoreElements()) {
            Header header = headerEn.nextElement();
            buf.append(header.getName()).append("=").append(header.getValue());

            if (headerEn.hasMoreElements()) {
                buf.append(", ");
            }
        }

        buf.append("}");
        buf.append(System.getProperty("line.separator") + "Attributes:").append(getAttributes());
        buf.append(System.getProperty("line.separator") + "Text: " + System.getProperty("line.separator"));
        buf.append(getText()).append(System.getProperty("line.separator"));

        return buf.toString();
    }

    public void updateMessageID() throws InvalidParameterException {
        setMessageID(generateMessageID());
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        // read in partnership
        partnership = (Partnership) in.readObject();

        // read in attributes
        attributes = (Map<String, String>) in.readObject();

        // read in text
        text = (String) in.readObject();

        try {
            // read in message headers
            headers = new InternetHeaders(in);

            // read in mime body
            if (in.read() == 1) {
                data = new MimeBodyPart(in);
            } else {
                data = null;
            }
        } catch (MessagingException me) {
            throw new IOException("Messaging exception: " + me.getMessage());
        }
    }

    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        // write partnership info
        out.writeObject(partnership);

        // write attributes
        out.writeObject(attributes);

        // write text
        out.writeObject(text);

        // write message headers
        Enumeration<String> en = headers.getAllHeaderLines();

        while (en.hasMoreElements()) {
            out.writeBytes(en.nextElement() + "\r\n");
        }

        out.writeBytes("\r\n");

        // write the mime body
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            if (data != null) {
                baos.write(1);
                data.writeTo(baos);
            } else {
                baos.write(0);
            }
        } catch (MessagingException e) {
            throw new IOException("Messaging exception: " + e.getMessage());
        }

        out.write(baos.toByteArray());
        baos.close();
    }
}
