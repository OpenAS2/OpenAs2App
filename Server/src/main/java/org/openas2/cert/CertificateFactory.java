package org.openas2.cert;

import org.openas2.Component;
import org.openas2.OpenAS2Exception;
import org.openas2.message.Message;
import org.openas2.message.MessageMDN;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public interface CertificateFactory extends Component {
    String COMPID_CERTIFICATE_FACTORY = "certificatefactory";

    X509Certificate getCertificate(Message msg, String partnershipType) throws OpenAS2Exception;

    PrivateKey getPrivateKey(Message msg, X509Certificate cert) throws OpenAS2Exception;

    X509Certificate getCertificate(MessageMDN msg, String partnershipType) throws OpenAS2Exception;

    PrivateKey getPrivateKey(MessageMDN msg, X509Certificate cert) throws OpenAS2Exception;
}
