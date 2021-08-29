package org.openas2.message;

import org.apache.commons.logging.LogFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.WrappedException;
import org.openas2.lib.helper.ICryptoHelper;
import org.openas2.params.InvalidParameterException;
import org.openas2.partner.Partnership;
import org.openas2.processor.msgtracking.TrackingModule;
import org.openas2.util.Properties;
import org.openas2.util.IOUtil;

import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.internet.ContentDisposition;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.ParseException;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;


public abstract class BaseMessage implements Message {
    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private DataHistory history;
    private InternetHeaders headers;
    private Map<String, String> attributes;
    private MessageMDN MDN;
    private MimeBodyPart data;
    private Partnership partnership;
    private String compressionType = ICryptoHelper.COMPRESSION_NONE;
    private boolean rxdMsgWasSigned = false;
    private boolean rxdMsgWasEncrypted = false;
    private Map<Object, Object> options = new HashMap<Object, Object>();
    private String calculatedMIC = null;
    private String logMsg = null;
    private String status = MSG_STATUS_MSG_INIT;
    private Map<String, String> customOuterMimeHeaders = new HashMap<String, String>();
    private String payloadFilename = null;


    public BaseMessage() {
        super();
    }

    public String getAppTitle() {
        return Properties.getProperty(Properties.APP_TITLE_PROP, "OpenAS2 Server");
    }

    public Map<Object, Object> getOptions() {
        if (options == null) {
            options = new HashMap<Object, Object>();
        }
        return options;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Map<String, String> getCustomOuterMimeHeaders() {
        return customOuterMimeHeaders;
    }


    public void setCustomOuterMimeHeaders(Map<String, String> customOuterMimeHeaders) {
        this.customOuterMimeHeaders = customOuterMimeHeaders;
    }

    public void addCustomOuterMimeHeader(String key, String value) {
        this.customOuterMimeHeaders.put(key, value);
    }

    public void setOption(Object key, Object value) {
        getOptions().put(key, value);
    }

    public Object getOption(Object key) {
        return getOptions().get(key);
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

    public String getContentType() {
        return getHeader("Content-Type");
    }

    public void setContentType(String contentType) {
        setHeader("Content-Type", contentType);
    }

    public String getCompressionType() {
        return (compressionType);
    }

    public void setCompressionType(String myCompressionType) {
        compressionType = myCompressionType;
    }

    /**
     * Gets the "Content-Disposition" header from the message object
     *
     * @return the string value of the header
     */
    public String getContentDisposition() {
        return getHeader("Content-Disposition");
    }

    /**
     * Sets the "Content-Disposition" header in the message object
     *
     * @param contentDisposition the string value to be set
     */
    public void setContentDisposition(String contentDisposition) {
        setHeader("Content-Disposition", contentDisposition);
    }

    public void setData(MimeBodyPart data, DataHistoryItem historyItem) {
        this.data = data;

        if (data != null) {
            try {
                setContentType(data.getContentType());
            } catch (MessagingException e) {
                setContentType(null);
            }
            try {
                setContentDisposition(data.getHeader("Content-Disposition", null));
            } catch (MessagingException e) {
                setContentDisposition(null); // TODO: why ignore?????
            }
        }

        if (historyItem != null) {
            getHistory().getItems().add(historyItem);
        }
    }

    public DataHistoryItem setData(MimeBodyPart data) throws OpenAS2Exception {
        try {
            DataHistoryItem historyItem = new DataHistoryItem(data.getContentType());
            setData(data, historyItem);

            return historyItem;
        } catch (Exception e) {
            throw new WrappedException(e);
        }
    }

    public MimeBodyPart getData() {
        return data;
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

    public DataHistory getHistory() {
        if (history == null) {
            history = new DataHistory();
        }

        return history;
    }

    public void setHistory(DataHistory history) {
        this.history = history;
    }

    public MessageMDN getMDN() {
        return MDN;
    }

    public void setMDN(MessageMDN mdn) {
        this.MDN = mdn;
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

    public abstract String generateMessageID() throws InvalidParameterException;

    public String getSubject() {
        return getHeader("Subject");
    }

    public void setSubject(String subject) {
        setHeader("Subject", subject);
    }

    public boolean isRxdMsgWasSigned() {
        return rxdMsgWasSigned;
    }

    public void setRxdMsgWasSigned(boolean rxdMsgWasSigned) {
        this.rxdMsgWasSigned = rxdMsgWasSigned;
    }

    public boolean isRxdMsgWasEncrypted() {
        return rxdMsgWasEncrypted;
    }

    public void setRxdMsgWasEncrypted(boolean rxdMsgWasEncrypted) {
        this.rxdMsgWasEncrypted = rxdMsgWasEncrypted;
    }

    public String getXForwardedFor() {
        return getHeader("X-Forwarded-For");
    }

    public String getXRealIP() {
        return getHeader("X-Real-IP");
    }

    public void addHeader(String key, String value) {
        getHeaders().addHeader(key, value);
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("Message From:").append(getPartnership().getSenderIDs());
        buf.append("To:").append(getPartnership().getReceiverIDs());

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

        MessageMDN mdn = getMDN();

        if (mdn != null) {
            buf.append(System.getProperty("line.separator") + "MDN:");
            buf.append(mdn.toString());
        }

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

        // read in data history
        history = (DataHistory) in.readObject();

        try {
            // read in message headers
            headers = new InternetHeaders(in);

            // read in mime body 
            if (in.read() == 1) {
                data = new MimeBodyPart(in);
            }
        } catch (MessagingException me) {
            throw new IOException("Messaging exception: " + me.getMessage());
        }

        // read in MDN
        MDN = (MessageMDN) in.readObject();

        if (MDN != null) {
            MDN.setMessage(this);
        }

        customOuterMimeHeaders = new HashMap<String, String>();
    }

    private void writeObject(java.io.ObjectOutputStream out) throws IOException {
        // write partnership info
        out.writeObject(partnership);

        // write attributes
        out.writeObject(attributes);

        // write data history
        out.writeObject(history);

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

        // write the message's MDN
        out.writeObject(MDN);
    }

    public String getLogMsgID() {
        return " [" + getMessageID() + "]";
    }

    public String getLogMsg() {
        return logMsg;
    }

    public void setLogMsg(String msg) {
        logMsg = msg;
    }

    public String getCalculatedMIC() {
        return calculatedMIC;
    }

    public void setCalculatedMIC(String calculatedMIC) {
        this.calculatedMIC = calculatedMIC;
    }

    public String getPayloadFilename() {
        return payloadFilename;
    }

    public void setPayloadFilename(String filename) {
        payloadFilename = filename;
    }

    public void trackMsgState(Session session) {
        // Log a start sending fail state but do not allow exceptions to stop the process
        try {
            options.put("OPTIONAL_MODULE", "true");
            session.getProcessor().handle(TrackingModule.DO_TRACK_MSG, this, options);
        } catch (Exception et) {
            setLogMsg("Unable to persist message tracking state: " + org.openas2.logging.Log.getExceptionMsg(et));
            LogFactory.getLog(BaseMessage.class.getSimpleName()).error(this, et);
        }

    }

    public String extractPayloadFilename() throws ParseException {
        String s = getContentDisposition();
        if (s == null || s.length() < 1) {
            return null;
        }
        // TODO: This should be a case insensitive lookup per RFC6266
        String tmpFilename = null;

        ContentDisposition cd = new ContentDisposition(s);
        tmpFilename = cd.getParameter("filename");

        if (tmpFilename == null || tmpFilename.length() < 1) {
            /* Try to extract manually */
            int n = s.indexOf("filename=");
            if (n > -1) {
                tmpFilename = s.substring(n);
                tmpFilename = tmpFilename.replaceFirst("filename=", "");

                int n1 = tmpFilename.indexOf(",");
                if (n1 > -1) {
                    s = s.substring(0, n1 - 1);
                }
                tmpFilename = tmpFilename.replaceAll("\"", "");
                s = s.trim();
            } else {
                /* Try just using file separator */
                int pos = s.lastIndexOf(File.separator);
                if (pos >= 0) {
                    tmpFilename = s.substring(pos + 1);
                }
            }
        }
        if (tmpFilename == null || tmpFilename.length() < 1) {
            return null;
        }
        try {
          tmpFilename = IOUtil.getSafeFilename(tmpFilename);          
        } catch (OpenAS2Exception oae) {
          ParseException pe = new ParseException("Unable to extract a usable filename");
          pe.initCause(oae);
          throw pe;
        }
        return tmpFilename;
    }
    
}
