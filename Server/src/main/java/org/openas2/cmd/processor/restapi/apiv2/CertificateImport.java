package org.openas2.cmd.processor.restapi.apiv2;

import org.openas2.params.InvalidParameterException;

public class CertificateImport {
    public static final String certificateImportUsage = "You must send { \"alias\": \"alias of certificate\", \"publicKey\": \"base64-encoded public key\" } or { \"alias\": \"alias of certificate\", \"pkcs12Container\": \"base64-encoded pkcs12 container (.p12-file)\", \"pkcs12Container\": \"password for pkcs12Container\" }";

    private String alias;

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    private byte[] publicKey;

    public byte[] getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(byte[] publicKey) {
        this.publicKey = publicKey;
    }

    private String password;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    private byte[] pkcs12Container;

    public byte[] getPkcs12Container() {
        return pkcs12Container;
    }

    public void setPkcs12Container(byte[] pkcs12Container) {
        this.pkcs12Container = pkcs12Container;
    }

    /**
     * Returns whether this is a public key import or a pkcs12 container export with password.
     * @return True if it is a pkcs12 container with password, otherwise false.
     * @throws InvalidParameterException Throws if the properties are not valid.
     */
    public boolean isPrivateKeyRequest() throws InvalidParameterException {
        if (getPublicKey() == null) {
            if (getPkcs12Container() == null || getPassword() == null) {
                throw new InvalidParameterException(certificateImportUsage);
            }

            return true;
        } else {
            if (getPkcs12Container() != null || getPassword() != null) {
                throw new InvalidParameterException(certificateImportUsage);
            }

            return false;
        }
    }
}
