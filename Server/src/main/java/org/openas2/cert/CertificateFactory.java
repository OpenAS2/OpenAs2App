package org.openas2.cert;

import org.openas2.Component;
import org.openas2.OpenAS2Exception;
import org.openas2.params.InvalidParameterException;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public interface CertificateFactory extends Component {
    String COMPID_AS2_CERTIFICATE_FACTORY = "as2_certs";
    String COMPID_SSL_TRUST_CERTIFICATE_FACTORY = "ssl_trust_certs";

    PrivateKey getPrivateKey(String alias) throws OpenAS2Exception;

    X509Certificate getCertificate(String alias) throws OpenAS2Exception;

    String getIdentifier() throws InvalidParameterException;
}
