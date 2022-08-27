package org.openas2.cmd.processor.restapi.apiv2;

import java.security.cert.X509Certificate;
import java.util.Date;

public class Certificate {
    public Certificate() {
    }

    public static Certificate fromX509Certificate(X509Certificate x509, String alias, boolean hasPrivateKey) {
        Certificate cert = new Certificate();

        cert.version = x509.getVersion();
        cert.serialNumber = x509.getSerialNumber().toString(16);
        cert.issuer = x509.getIssuerX500Principal().getName();
        cert.subject = x509.getSubjectX500Principal().getName();
        cert.notBefore = x509.getNotBefore();
        cert.notAfter = x509.getNotAfter();
        cert.alias = alias;
        cert.hasPrivateKey = hasPrivateKey;

        return cert;
    }

    private String alias;

    public String getAlias() {
        return alias;
    }

    private int version;

    public int getVersion() {
        return version;
    }

    private String serialNumber;

    public String getSerialNumber() {
        return serialNumber;
    }

    private String issuer;

    public String getIssuer() {
        return issuer;
    }

    private String subject;

    public String getSubject() {
        return subject;
    }

    private Date notBefore;

    public Date getNotBefore() {
        return notBefore;
    }

    private Date notAfter;

    public Date getNotAfter() {
        return notAfter;
    }

    private boolean hasPrivateKey;

    public boolean isHasPrivateKey() {
        return hasPrivateKey;
    }

    private byte[] publicKey;

    public byte[] getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(byte[] publicKey) {
        this.publicKey = publicKey;
    }

    private byte[] pkcs12Container;

    public byte[] getPkcs12Container() {
        return pkcs12Container;
    }

    public void setPkcs12Container(byte[] pkcs12Container) {
        this.pkcs12Container = pkcs12Container;
    }
}
