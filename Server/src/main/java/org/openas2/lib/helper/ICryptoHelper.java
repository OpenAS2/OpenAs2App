package org.openas2.lib.helper;

import org.bouncycastle.mail.smime.SMIMEException;
import org.openas2.DispositionException;
import org.openas2.OpenAS2Exception;
import org.openas2.message.AS2Message;
import org.openas2.message.Message;

import javax.mail.internet.MimeBodyPart;
import java.io.InputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.Certificate;

public interface ICryptoHelper {
    int JCE_LIMITED_MAX_LENGTH = 128;
    String JCE_LIMITATION_ERROR = "OpenAS2 needs `Java Cryptography Extension` (JCE) Unlimited Strength Jurisdiction Policy Files!\nPlease verify it is installed in your Java installation. You can download it from Oracle Web site.";

    String DIGEST_MD2 = "md2";
    String DIGEST_MD5 = "md5";
    String DIGEST_SHA1 = "sha1";
    String DIGEST_SHA224 = "sha224";
    String DIGEST_SHA256 = "sha256";
    String DIGEST_SHA384 = "sha384";
    String DIGEST_SHA512 = "sha512";
    String CRYPT_CAST5 = "cast5";
    String CRYPT_3DES = "3des";
    String CRYPT_IDEA = "idea";
    String CRYPT_RC2 = "rc2";
    String CRYPT_RC2_CBC = "rc2_cbc";
    String AES128_CBC = "aes128";
    String AES192_CBC = "aes192";
    String AES256_CBC = "aes256";
    String AES256_WRAP = "aes256_wrap";

    String COMPRESSION_UNKNOWN = "compression-unknown";
    String COMPRESSION_NONE = "none";
    String COMPRESSION_ZLIB = "zlib";


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

    MimeBodyPart sign(MimeBodyPart part, Certificate cert, Key key, String digest, String contentTxfrEncoding, boolean adjustDigestToOldName, boolean isRemoveCmsAlgorithmProtectionAttr) throws Exception;

    MimeBodyPart verifySignature(MimeBodyPart part, Certificate cert) throws Exception;

    MimeBodyPart compress(Message msg, MimeBodyPart mbp, String compressionType, String contentTxfrEncoding) throws SMIMEException, OpenAS2Exception;

    void decompress(AS2Message msg) throws DispositionException;
}
