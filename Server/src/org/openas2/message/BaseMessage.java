package org.openas2.message;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;

import org.openas2.OpenAS2Exception;
import org.openas2.WrappedException;
import org.openas2.lib.helper.ICryptoHelper;
import org.openas2.partner.Partnership;


public abstract class BaseMessage implements Message {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private DataHistory history;
    private InternetHeaders headers;
    private Map<String,String> attributes;
    private MessageMDN MDN;
    private MimeBodyPart data;
    private Partnership partnership;
	private String compressionType = ICryptoHelper.COMPRESSION_NONE;
	private boolean rxdMsgWasSigned = false;

	public BaseMessage() {
        super();
    }

    public void setAttribute(String key, String value) {
        getAttributes().put(key, value);
    }

    public String getAttribute(String key) {
        return (String) getAttributes().get(key);
    }

    public void setAttributes(Map<String, String> attributes) {
        this.attributes = attributes;
    }

    public Map<String, String> getAttributes() {
        if (attributes == null) {
            attributes = new HashMap<String, String>();
        }

        return attributes;
    }

    public void setContentType(String contentType) {
        setHeader("Content-Type", contentType);
    }

    public String getContentType() {
        return getHeader("Content-Type");
    }

    public String getCompressionType() {
        return (compressionType);
    }

    public void setCompressionType(String myCompressionType) {
        compressionType = myCompressionType;
    }

    /**
     * @since 2007-06-01
     * @param contentDisposition
     */
    public void setContentDisposition(String contentDisposition) {
        setHeader("Content-Disposition", contentDisposition);
    }

    /**
     * @since 2007-06-01
     * @return
     */
    public String getContentDisposition() {
        return getHeader("Content-Disposition");
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
            }
            catch (MessagingException e) { 
            	setContentDisposition(null); 
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

    public void setHeaders(InternetHeaders headers) {
        this.headers = headers;
    }

    public InternetHeaders getHeaders() {
        if (headers == null) {
            headers = new InternetHeaders();
        }

        return headers;
    }

    public void setHistory(DataHistory history) {
        this.history = history;
    }

    public DataHistory getHistory() {
        if (history == null) {
            history = new DataHistory();
        }

        return history;
    }

    public void setMDN(MessageMDN mdn) {
        this.MDN = mdn;
    }

    public MessageMDN getMDN() {
        return MDN;
    }

    public void setMessageID(String messageID) {
        setHeader("Message-ID", messageID);
    }

    public String getMessageID() {
        return getHeader("Message-ID");
    }

    public void setPartnership(Partnership partnership) {
        this.partnership = partnership;
    }

    public Partnership getPartnership() {
        if (partnership == null) {
            partnership = new Partnership();
        }

        return partnership;
    }

    public abstract String generateMessageID();

    public void setSubject(String subject) {
        setHeader("Subject", subject);
    }

    public String getSubject() {
        return getHeader("Subject");
    }

    public boolean isRxdMsgWasSigned()
	{
		return rxdMsgWasSigned;
	}

	public void setRxdMsgWasSigned(boolean rxdMsgWasSigned)
	{
		this.rxdMsgWasSigned = rxdMsgWasSigned;
	}

    public void addHeader(String key, String value) {
        getHeaders().addHeader(key, value);
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("Message From:").append(getPartnership().getSenderIDs());
        buf.append("To:").append(getPartnership().getReceiverIDs());

        Enumeration<Header> headerEn = getHeaders().getAllHeaders();
        buf.append("\r\nHeaders:{");

        while (headerEn.hasMoreElements()) {
            Header header = headerEn.nextElement();
            buf.append(header.getName()).append("=").append(header.getValue());

            if (headerEn.hasMoreElements()) {
                buf.append(", ");
            }
        }

        buf.append("}");
        buf.append("\r\nAttributes:").append(getAttributes());

        MessageMDN mdn = getMDN();

        if (mdn != null) {
            buf.append("\r\nMDN:");
            buf.append(mdn.toString());
        }

        return buf.toString();
    }

    public void updateMessageID() {
        setMessageID(generateMessageID());
    }

    private void readObject(java.io.ObjectInputStream in)
        throws IOException, ClassNotFoundException {
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
    }

    private void writeObject(java.io.ObjectOutputStream out)
        throws IOException {
        // write partnership info
        out.writeObject(partnership);

        // write attributes
        out.writeObject(attributes);
		
		// write data history
		out.writeObject(history);
		
        // write message headers
        Enumeration<String> en = headers.getAllHeaderLines();

        while (en.hasMoreElements()) {
            out.writeBytes(en.nextElement().toString() + "\r\n");
        }

        out.writeBytes(new String("\r\n"));

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
    
    public String getLoggingText() {
    	return " [" + getMessageID() + "]";
    }
}
