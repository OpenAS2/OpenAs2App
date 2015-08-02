package org.openas2.lib.cert;

import java.security.Key;
import java.security.cert.Certificate;


public interface ICertificateStore {    
    String[] getAliases() throws CertificateException;

    Certificate getCertificate(String alias) throws CertificateException;

    void setCertificate(String alias, Certificate cert) throws CertificateException;

    String getAlias(Certificate cert) throws CertificateException;

    void removeCertificate(String alias) throws CertificateException;

    void clearCertificates() throws CertificateException;
    
    Key getKey(String alias, char[] password) throws CertificateException;

    void setKey(String alias, Key key, char[] password) throws CertificateException;    
}