package org.openas2.partner;


public interface SecurePartnership {
	public static final String PID_X509_ALIAS = "x509_alias"; // Alias to an X509 Certificate
	public static final String PA_ENCRYPT = "encrypt"; // Set this to the algorithm to use for encryption, check AS2Util constants for values
	public static final String PA_SIGN = "sign"; // Set this to the signature digest algorithm to sign sent messages
}
