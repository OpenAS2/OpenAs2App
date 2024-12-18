package org.openas2.cert;

import org.openas2.OpenAS2Exception;

import java.io.IOException;
import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Map;


public interface AliasedCertificateFactory extends CertificateFactory {
    X509Certificate getCertificate(String alias) throws OpenAS2Exception;

    Map<String, X509Certificate> getCertificates() throws OpenAS2Exception;

    void addCertificate(String alias, X509Certificate cert, boolean overwrite) throws OpenAS2Exception;

    void addPrivateKey(String alias, Key key, String password) throws OpenAS2Exception;

    void clearCertificates() throws OpenAS2Exception;

    void removeCertificate(X509Certificate cert) throws OpenAS2Exception;

    void removeCertificate(String alias) throws OpenAS2Exception;
    
    boolean importCert(String alias, InputStream encodedCertStream) throws IOException, CertificateException, OpenAS2Exception;

    boolean importPrivateKey(String alias, KeyStore ks, String password) throws KeyStoreException, OpenAS2Exception, UnrecoverableKeyException, NoSuchAlgorithmException;
}
