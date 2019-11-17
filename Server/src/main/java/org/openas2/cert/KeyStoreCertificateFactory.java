package org.openas2.cert;

import java.security.KeyStore;

public interface KeyStoreCertificateFactory extends CertificateFactory {

    void setKeyStore(KeyStore keyStore);

    KeyStore getKeyStore();

}
