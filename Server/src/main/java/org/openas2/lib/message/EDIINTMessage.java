package org.openas2.lib.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Enumeration;

import javax.mail.MessagingException;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;

import org.apache.commons.lang3.SystemUtils;
import org.openas2.lib.util.MimeUtil;


public abstract class EDIINTMessage {
    private InternetHeaders headers;
    private MimeBodyPart data;

    public EDIINTMessage() {
        super();
    }

    public EDIINTMessage(MimeBodyPart data, String contentType)
        throws MessagingException {
        super();
        setData(data);
        setContentType(contentType);
    }

    public EDIINTMessage(InputStream in) throws IOException, MessagingException {
        super();
        setHeaders(MimeUtil.readHeaders(in));
        setData(MimeUtil.readMimeBodyPart(in, getHeaders()));
    }
    
    public void setContentType(String contentType) {
        setHeader("Content-Type", contentType);
    }

    // this is always used as the content-type for getData(), not getData().getContentType. This is due to issues with
    // the way JavaMail's MimeBodyPart content-type works
    public String getContentType() {
        return getHeader("Content-Type");
    }

    // content-type must always be manually set with setContentType, so:
    // setData(data);
    // setContentType(data.getContentType());
    public void setData(MimeBodyPart data) throws MessagingException {
        this.data = data;
    }

    public MimeBodyPart getData() throws MessagingException {
        return data;
    }

    public void setDate(String date) {
        setHeader("Date", date);
    }

    public String getDate() {
        return getHeader("Date");
    }

    public void setDefaults() {
        SimpleDateFormat sdf = new SimpleDateFormat("EEE, MM MMM yyyy HH:mm:ss z");
        setDate(sdf.format(new Date()));
        setMessageID(generateMessageID());
        setMimeVersion("1.0");
    }

    public void setFrom(String from) {
        setHeader("From", from);
    }

    public String getFrom() {
        return getHeader("From");
    }

    public void setHeader(String key, String value) {
        if (value == null) {
            getHeaders().removeHeader(key);
        } else {
            getHeaders().setHeader(key, value);
        }
    }

    public String getHeader(String key) {
        return getHeader(key, ", ");
    }

    public String getHeader(String key, String delimiter) {
        return MimeUtil.getHeader(getHeaders(), key, delimiter);
    }

    public void setHeaders(InternetHeaders headers) {
        this.headers = headers;
    }

    public InternetHeaders getHeaders() {
        if (headers == null) {
            headers = new InternetHeaders();
        }

        return headers;
    }

    public void setMessageID(String messageID) {
        setHeader("Message-ID", messageID);
    }

    public String getMessageID() {
        return getHeader("Message-ID");
    }

    public void setMimeVersion(String version) {
        setHeader("Mime-Version", version);
    }

    public String getMimeVersion() {
        return getHeader("Mime-Version");
    }

    public void setSubject(String subject) {
        setHeader("Subject", subject);
    }

    public String getSubject() {
        return getHeader("Subject");
    }
    
    public abstract String getSenderIDHeader();
    
    public String getSenderID() {
        return getHeader(getSenderIDHeader());
    }
    
    public abstract String getReceiverIDHeader();
    
    public String getReceiverID() {
        return getHeader(getReceiverIDHeader());
    }
    
    public String generateMessageID() {
        MessageID id = new MessageID(getSenderID(), getReceiverID());

        return id.toString();
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        Enumeration<String> headers = getHeaders().getAllHeaderLines();

        while (headers.hasMoreElements()) {
            buf.append(headers.nextElement());
            buf.append(SystemUtils.LINE_SEPARATOR);
        }

        buf.append(SystemUtils.LINE_SEPARATOR);

        try {
            if (getData() != null) {
                ByteArrayOutputStream bout = new ByteArrayOutputStream();

                getData().writeTo(bout);
                buf.append(new String(bout.toByteArray()));
            }
        } catch (Exception e) {
            buf.append("(Exception converting data to string: " + e.getMessage() + "!)");
        }

        return buf.toString();
    }
}
