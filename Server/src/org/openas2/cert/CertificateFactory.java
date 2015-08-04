package org.openas2.cert;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import org.openas2.Component;
import org.openas2.OpenAS2Exception;
import org.openas2.message.Message;
import org.openas2.message.MessageMDN;

public interface CertificateFactory extends Component {
	public static final String COMPID_CERTIFICATE_FACTORY = "certificatefactory";
	
	public X509Certificate getCertificate(Message msg, String partnershipType) throws OpenAS2Exception;
	public PrivateKey getPrivateKey(Message msg, X509Certificate cert) throws OpenAS2Exception;
	public X509Certificate getCertificate(MessageMDN msg, String partnershipType) throws OpenAS2Exception;
	public PrivateKey getPrivateKey(MessageMDN msg, X509Certificate cert) throws OpenAS2Exception;
}
