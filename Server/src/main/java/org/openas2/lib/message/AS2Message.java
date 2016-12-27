package org.openas2.lib.message;

import java.io.IOException;
import java.io.InputStream;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.openas2.lib.Info;


public class AS2Message extends EDIINTMessage {
    public AS2Message() {
        super();
    }

    public AS2Message(MimeBodyPart data, String contentType)
        throws MessagingException {
        super(data, contentType);
    }

    public AS2Message(InputStream in) throws IOException, MessagingException {
        super(in);
    }
    
    public String getSenderIDHeader() {
        return "AS2-From";
    }
    
    public String getReceiverIDHeader() {
        return "AS2-To";
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
        setUserAgent("OpenAS2");
        setServer(Info.NAME_VERSION);
    }

    public void setDispositionNotificationOptions(String options) {
        setHeader("Disposition-Notification-Options", options);
    }

    public String getDispositionNotificationOptions() {
        return getHeader("Disposition-Notification-Options");
    }

    public void setDispositionNotificationTo(String to) {
        setHeader("Disposition-Notification-To", to);
    }

    public String getDispositionNotificationTo() {
        return getHeader("Disposition-Notification-To");
    }

    public void setReceiptDeliveryOption(String option) {
        setHeader("Receipt-Delivery-Option", option);
    }

    public String getReceiptDeliveryOption() {
        return getHeader("Receipt-Delivery-Option");
    }

    public void setRecipientAddress(String address) {
        setHeader("Recipient-Address", address);
    }

    public String getRecipientAddress() {
        return getHeader("Recipient-Address");
    }

    public void setServer(String server) {
        setHeader("Server", server);
    }

    public String getServer() {
        return getHeader("Server");
    }

    public void setUserAgent(String agent) {
        setHeader("User-Agent", agent);
    }

    public String getUserAgent() {
        return getHeader("User-Agent");
    }
}
