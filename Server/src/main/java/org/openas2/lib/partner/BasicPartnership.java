package org.openas2.lib.partner;

import java.util.HashMap;
import java.util.Map;

import org.openas2.lib.util.GeneralUtil;

public class BasicPartnership implements IPartnership {
    private IPartner sender;
    private IPartner receiver;
    private Map<String, String> attributes;

    public BasicPartnership() {
        super();
    }

    public IPartner getReceiver() {
        return receiver;
    }

    public void setReceiver(IPartner receiver) {
        this.receiver = receiver;
    }

    public IPartner getSender() {
        return sender;
    }

    public void setSender(IPartner sender) {
        this.sender = sender;
    }

    public Map<String, String> getAttributes() {
        if (attributes == null) {
            attributes = new HashMap<String, String>();
        }
        return attributes;
    }

    public String getAttribute(String name) {
        return (String) getAttributes().get(name);
    }

    public void setAttribute(String name, String value) {
        getAttributes().put(name, value);
    }

    public String getEncryptionAlgorithm() {
        return getAttribute(IPartnership.ATTRIBUTE_ENCRYPTION_ALGORITHM);
    }

    public void setEncryptionAlgorithm(String encryptionAlgorithm) {
        setAttribute(IPartnership.ATTRIBUTE_ENCRYPTION_ALGORITHM, encryptionAlgorithm);
    }

    public String getCompressionType() {
        return getAttribute(IPartnership.ATTRIBUTE_COMPRESSION_TYPE);
    }

    public void setCompressionType(String compressionType) {
        setAttribute(IPartnership.ATTRIBUTE_COMPRESSION_TYPE, compressionType);
    }

    public String getSignatureAlgorithm() {
        return getAttribute(IPartnership.ATTRIBUTE_SIGNATURE_ALGORITHM);
    }

    public void setSignatureAlgorithm(String signatureAlgorithm) {
        setAttribute(IPartnership.ATTRIBUTE_SIGNATURE_ALGORITHM, signatureAlgorithm);
    }

    public String getSource() {
        return getAttribute(IPartnership.ATTRIBUTE_SOURCE);
    }

    public void setSource(String source) {
        setAttribute(IPartnership.ATTRIBUTE_SOURCE, source);
    }

    public String getDestination() {
        return getAttribute(IPartnership.ATTRIBUTE_DESTINATION);
    }

    public void setDestination(String destination) {
        setAttribute(IPartnership.ATTRIBUTE_DESTINATION, destination);
    }

    public String getSubject() {
        return getAttribute(IPartnership.ATTRIBUTE_SUBJECT);
    }

    public void setSubject(String subject) {
        setAttribute(IPartnership.ATTRIBUTE_SUBJECT, subject);
    }

    public String getMdnOptions() {
        return getAttribute(IPartnership.ATTRIBUTE_MDN_OPTIONS);
    }

    public void setMdnOptions(String mdnOptions) {
        setAttribute(IPartnership.ATTRIBUTE_MDN_OPTIONS, mdnOptions);
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append("Sender: ");
        if (getSender() != null) {
            buf.append(getSender().toString());
        } else {
            buf.append("<none>");
        }
        buf.append("  ");
        if (getReceiver() != null) {
            buf.append("Receiver: ").append(getReceiver().toString());
        } else {
            buf.append("<none>");
        }
        buf.append("  Attributes: ").append(GeneralUtil.convert(getAttributes(), "=", ", "));
        return buf.toString();
    }
}