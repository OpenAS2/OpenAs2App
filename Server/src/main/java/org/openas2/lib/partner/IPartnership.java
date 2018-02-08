package org.openas2.lib.partner;

import java.util.Map;

public interface IPartnership {

	public static final String ATTRIBUTE_ENCRYPTION_ALGORITHM = "encrypt";
	public static final String ATTRIBUTE_SIGNATURE_ALGORITHM = "sign";
	public static final String ATTRIBUTE_SUBJECT = "subject";
	public static final String ATTRIBUTE_SOURCE = "source";
	public static final String ATTRIBUTE_DESTINATION = "destination";
	public static final String ATTRIBUTE_MDN_OPTIONS = "mdnoptions";
	public static final String ATTRIBUTE_COMPRESSION_TYPE = "compression_type";

	Map<String, String> getAttributes();

	String getAttribute(String name);

	void setAttribute(String name, String value);

	IPartner getSender();

	void setSender(IPartner sender);

	IPartner getReceiver();

	void setReceiver(IPartner receiver);

	String getEncryptionAlgorithm();

	void setEncryptionAlgorithm(String algorithm);

	String getCompressionType();

	void setCompressionType(String compressionType);

	String getSignatureAlgorithm();

	void setSignatureAlgorithm(String algorithm);

	String getSource();

	void setSource(String url);

	String getDestination();

	void setDestination(String url);

	String getSubject();

	void setSubject(String subject);

	String getMdnOptions();

	void setMdnOptions(String options);
}
