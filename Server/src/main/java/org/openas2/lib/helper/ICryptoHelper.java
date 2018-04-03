package org.openas2.lib.helper;

import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.Certificate;
import javax.mail.internet.MimeBodyPart;

import org.bouncycastle.mail.smime.SMIMEException;
import org.openas2.DispositionException;
import org.openas2.OpenAS2Exception;
import org.openas2.message.AS2Message;
import org.openas2.message.Message;

public interface ICryptoHelper {
    static final int JCE_LIMITED_MAX_LENGTH = 128;
    static final String JCE_LIMITATION_ERROR = "OpenAS2 needs `Java Cryptography Extension` (JCE) Unlimited Strength Jurisdiction Policy Files!\nPlease verify it is installed in your Java installation. You can download it from Oracle Web site.";

    static final String DIGEST_MD2 = "md2";
    static final String DIGEST_MD5 = "md5";
    static final String DIGEST_SHA1 = "sha1";
    static final String DIGEST_SHA224 = "sha224";
    static final String DIGEST_SHA256 = "sha256";
    static final String DIGEST_SHA384 = "sha384";
    static final String DIGEST_SHA512 = "sha512";
    static final String CRYPT_CAST5 = "cast5";
    static final String CRYPT_3DES = "3des";
    static final String CRYPT_IDEA = "idea";
    static final String CRYPT_RC2 = "rc2";
    static final String CRYPT_RC2_CBC = "rc2_cbc";
    static final String AES128_CBC = "aes128";
    static final String AES192_CBC = "aes192";
    static final String AES256_CBC = "aes256";
    static final String AES256_WRAP = "aes256_wrap";
    
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
    String calculateMIC(MimeBodyPart part, String digest, boolean includeHeaders, boolean noCanonicalize) throws Exception;

    MimeBodyPart decrypt(MimeBodyPart part, Certificate cert, Key key) throws Exception;

    MimeBodyPart encrypt(MimeBodyPart part, Certificate cert, String algorithm, String contentTxfrEncoding) throws Exception;

    void initialize() throws Exception;

    MimeBodyPart sign(MimeBodyPart part, Certificate cert, Key key, String digest, String contentTxfrEncoding
    		, boolean adjustDigestToOldName, boolean isRemoveCmsAlgorithmProtectionAttr) throws Exception;

    MimeBodyPart verifySignature(MimeBodyPart part, Certificate cert) throws Exception;
    
    MimeBodyPart compress(Message msg, MimeBodyPart mbp, String compressionType, String contentTxfrEncoding)
			throws SMIMEException, OpenAS2Exception;
    
    void decompress(AS2Message msg) throws DispositionException;
}