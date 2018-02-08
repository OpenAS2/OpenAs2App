package org.openas2.lib.partner;

import java.util.Map;

public interface IPartner {

	public static final String ATTRIBUTE_AS1ID = "as1";
	public static final String ATTRIBUTE_AS2ID = "as2";
	public static final String ATTRIBUTE_EDIID = "edi";
	public static final String ATTRIBUTE_CERTIFICATE_ALIAS = "certificate";
	public static final String ATTRIBUTE_CONTACT_EMAIL = "contact";

	Map<String, String> getAttributes();

	String getAttribute(String name);

	void setAttribute(String name, String value);

	String getAs1Id();

	void setAs1Id(String id);

	String getAs2Id();

	void setAs2Id(String id);

	String getEdiId();

	void setEdiId(String id);

	String getCertificateAlias();

	void setCertificateAlias(String alias);

	String getContactEmail();

	void setContactEmail(String email);
}
