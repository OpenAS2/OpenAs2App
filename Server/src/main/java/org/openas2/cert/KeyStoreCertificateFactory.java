package org.openas2.cert;

import java.security.KeyStore;

public interface KeyStoreCertificateFactory extends CertificateFactory {

    public void setKeyStore(KeyStore keyStore);
    public KeyStore getKeyStore();

}
