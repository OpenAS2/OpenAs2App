package org.openas2.cert;

import org.openas2.Component;
import org.openas2.OpenAS2Exception;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public interface CertificateFactory extends Component {
    String COMPID_CERTIFICATE_FACTORY = "certificatefactory";

    PrivateKey getPrivateKey(String alias) throws OpenAS2Exception;

    X509Certificate getCertificate(String alias) throws OpenAS2Exception;
}
