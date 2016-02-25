package org.openas2.lib.helper;

import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.Certificate;

import javax.mail.internet.MimeBodyPart;

public interface ICryptoHelper {
    static final String DIGEST_MD2 = "md2";
    static final String DIGEST_MD5 = "md5";
    static final String DIGEST_SHA1 = "sha1";
    static final String DIGEST_SHA224 = "sha-224";
    static final String DIGEST_SHA256 = "sha-256";
    static final String DIGEST_SHA384 = "sha-384";
    static final String DIGEST_SHA512 = "sha-512";
    static final String CRYPT_CAST5 = "cast5";
    static final String CRYPT_3DES = "3des";
    static final String CRYPT_IDEA = "idea";
    static final String CRYPT_RC2 = "rc2";
    static final String CRYPT_RC2_CBC = "rc2_cbc";
    
    static final String COMPRESSION_UNKNOWN = "compression-unknown";
    static final String COMPRESSION_NONE = "none";
    static final String COMPRESSION_ZLIB = "zlib";


    boolean isEncrypted(MimeBodyPart part) throws Exception;

    KeyStore getKeyStore() throws Exception;

    KeyStore loadKeyStore(InputStream in, char[] password) throws Exception;

    KeyStore loadKeyStore(String filename, char[] password) throws Exception;

    boolean isSigned(MimeBodyPart part) throws Exception;

    boolean isCompressed(MimeBodyPart part) throws Exception;

    String calculateMIC(MimeBodyPart part, String digest, boolean includeHeaders) throws Exception;

    MimeBodyPart decrypt(MimeBodyPart part, Certificate cert, Key key) throws Exception;

    void deinitialize() throws Exception;

    MimeBodyPart encrypt(MimeBodyPart part, Certificate cert, String algorithm) throws Exception;

    void initialize() throws Exception;

    MimeBodyPart sign(MimeBodyPart part, Certificate cert, Key key, String digest) throws Exception;

    MimeBodyPart verifySignature(MimeBodyPart part, Certificate cert) throws Exception;
}