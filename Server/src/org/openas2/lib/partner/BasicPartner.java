package org.openas2.lib.partner;

import java.util.HashMap;
import java.util.Map;

import org.openas2.lib.util.GeneralUtil;

public class BasicPartner implements IPartner {
    private Map<String,String> attributes;

    public BasicPartner() {
        super();
    }

    public Map<String,String> getAttributes() {
        if (attributes == null) {
            attributes = new HashMap<String,String>();
        }
        return attributes;
    }

    public String getAttribute(String name) {
        return (String) getAttributes().get(name);
    }

    public void setAttribute(String name, String value) {
        getAttributes().put(name, value);
    }

    public String getAs1Id() {
        return getAttribute(IPartner.ATTRIBUTE_AS1ID);
    }

    public void setAs1Id(String as1Id) {
        setAttribute(IPartner.ATTRIBUTE_AS1ID, as1Id);
    }

    public String getAs2Id() {
        return getAttribute(IPartner.ATTRIBUTE_AS2ID);
    }

    public void setAs2Id(String as2Id) {
        setAttribute(IPartner.ATTRIBUTE_AS2ID, as2Id);
    }

    public String getCertificateAlias() {
        return getAttribute(IPartner.ATTRIBUTE_CERTIFICATE_ALIAS);
    }

    public void setCertificateAlias(String certificateAlias) {
        setAttribute(IPartner.ATTRIBUTE_CERTIFICATE_ALIAS, certificateAlias);
    }

    public String getContactEmail() {
        return getAttribute(IPartner.ATTRIBUTE_CONTACT_EMAIL);
    }

    public void setContactEmail(String contactEmail) {
        setAttribute(IPartner.ATTRIBUTE_CONTACT_EMAIL, contactEmail);
    }

    public String getEdiId() {
        return getAttribute(IPartner.ATTRIBUTE_EDIID);
    }

    public void setEdiId(String ediId) {
        setAttribute(IPartner.ATTRIBUTE_EDIID, ediId);
    }

    public String toString() {
        return GeneralUtil.convert(getAttributes(), "=", ", ");
    }
}