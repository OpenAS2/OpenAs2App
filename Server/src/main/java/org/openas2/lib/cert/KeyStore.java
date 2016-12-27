package org.openas2.lib.cert;

import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStoreException;
import java.security.cert.Certificate;
import java.util.Enumeration;

import org.openas2.lib.util.GeneralUtil;

public class KeyStore implements ICertificateStore {
    private java.security.KeyStore keyStore;

    public KeyStore(java.security.KeyStore keyStore) {
        super();
        this.keyStore = keyStore;
    }

    public String[] getAliases() throws CertificateException {
        try {
            return GeneralUtil.convert(getKeyStore().aliases());
        } catch (KeyStoreException kse) {
            throw new CertificateException("Error getting aliases", kse);
        }
    }

    public Certificate getCertificate(String alias) throws CertificateException {
        try {
            return getKeyStore().getCertificate(alias);
        } catch (KeyStoreException kse) {
            throw new CertificateException("Error getting certificate for alias: " + alias, kse);
        }
    }

    public void setCertificate(String alias, Certificate cert) throws CertificateException {
        try {
            getKeyStore().setCertificateEntry(alias, cert);
        } catch (KeyStoreException kse) {
            throw new CertificateException("Error setting certificate: " + alias, kse);
        }
    }

    public String getAlias(Certificate cert) throws CertificateException {
        try {
            return getKeyStore().getCertificateAlias(cert);
        } catch (KeyStoreException kse) {
            throw new CertificateException("Error getting alias for certificate: "
                    + cert.toString(), kse);
        }
    }

    public void removeCertificate(String alias) throws CertificateException {
        try {
            getKeyStore().deleteEntry(alias);
        } catch (KeyStoreException kse) {
            throw new CertificateException("Error while removing certificate: " + alias, kse);
        }
    }

    public void clearCertificates() throws CertificateException {
        try {
            java.security.KeyStore ks = getKeyStore();
            Enumeration<String> aliases = ks.aliases();

            while (aliases.hasMoreElements()) {
                ks.deleteEntry((String) aliases.nextElement());
            }
        } catch (KeyStoreException kse) {
            throw new CertificateException("Error clearing certificates", kse);
        }
    }

    public Key getKey(String alias, char[] password) throws CertificateException {
        try {
            return getKeyStore().getKey(alias, password);
        } catch (GeneralSecurityException gse) {
            throw new CertificateException("Error getting key for alias: " + alias, gse);
        }
    }

    public void setKey(String alias, Key key, char[] password) throws CertificateException {
        java.security.KeyStore ks = getKeyStore();
        Certificate[] certChain;
        try {
            certChain = ks.getCertificateChain(alias);
            ks.setKeyEntry(alias, key, password, certChain);
        } catch (KeyStoreException kse) {
            throw new CertificateException("Error setting key for alias: " + alias, kse);
        }
    }

    public java.security.KeyStore getKeyStore() {
        return keyStore;
    }

    public void setKeyStore(java.security.KeyStore keyStore) {
        this.keyStore = keyStore;
    }
}