package org.openas2.lib.helper;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.cms.Attribute;
import org.bouncycastle.asn1.cms.AttributeTable;
import org.bouncycastle.asn1.cms.CMSAttributes;
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.CMSAttributeTableGenerator;
import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.DefaultSignedAttributeTableGenerator;
import org.bouncycastle.cms.KeyTransRecipientId;
import org.bouncycastle.cms.KeyTransRecipientInformation;
import org.bouncycastle.cms.RecipientInformation;
import org.bouncycastle.cms.RecipientInformationStore;
import org.bouncycastle.cms.SignerInfoGenerator;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationStore;
import org.bouncycastle.cms.SignerInformationVerifier;
import org.bouncycastle.cms.bc.BcRSAKeyTransEnvelopedRecipient;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoGeneratorBuilder;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.cms.jcajce.ZlibCompressor;
import org.bouncycastle.cms.jcajce.ZlibExpanderProvider;
import org.bouncycastle.crypto.util.PrivateKeyFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.mail.smime.SMIMECompressed;
import org.bouncycastle.mail.smime.SMIMECompressedGenerator;
import org.bouncycastle.mail.smime.SMIMEEnveloped;
import org.bouncycastle.mail.smime.SMIMEEnvelopedGenerator;
import org.bouncycastle.mail.smime.SMIMEException;
import org.bouncycastle.mail.smime.SMIMESigned;
import org.bouncycastle.mail.smime.SMIMESignedGenerator;
import org.bouncycastle.mail.smime.SMIMESignedParser;
import org.bouncycastle.mail.smime.SMIMEUtil;
import org.bouncycastle.mail.smime.util.CRLFOutputStream;
import org.bouncycastle.operator.DigestCalculatorProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.OutputCompressor;
import org.bouncycastle.operator.OutputEncryptor;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;
import org.bouncycastle.util.encoders.Base64;
import org.bouncycastle.util.encoders.Hex;
import org.openas2.DispositionException;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.message.AS2Message;
import org.openas2.message.Message;
import org.openas2.processor.receiver.AS2ReceiverModule;
import org.openas2.util.AS2Util;
import org.openas2.util.DispositionType;

import javax.activation.CommandMap;
import javax.activation.MailcapCommandMap;
import javax.mail.MessagingException;
import javax.mail.internet.ContentType;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.DigestInputStream;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

public class BCCryptoHelper implements ICryptoHelper {
    private Log logger = LogFactory.getLog(BCCryptoHelper.class.getSimpleName());

    public boolean isEncrypted(MimeBodyPart part) throws MessagingException {
        ContentType contentType = new ContentType(part.getContentType());
        String baseType = contentType.getBaseType().toLowerCase();

        if (baseType.equalsIgnoreCase("application/pkcs7-mime")) {
            String smimeType = contentType.getParameter("smime-type");
            boolean checkResult = (smimeType != null) && smimeType.equalsIgnoreCase("enveloped-data");
            if (!checkResult && logger.isDebugEnabled()) {
                logger.debug("Check for encrypted data failed on SMIME content type: " + smimeType);
            }
            return (checkResult);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Check for encrypted data failed on BASE content type: " + baseType);
        }
        return false;
    }

    public boolean isSigned(MimeBodyPart part) throws MessagingException {
        ContentType contentType = new ContentType(part.getContentType());
        String baseType = contentType.getBaseType().toLowerCase();

        return baseType.equalsIgnoreCase("multipart/signed");
    }

    public boolean isCompressed(MimeBodyPart part) throws MessagingException {
        ContentType contentType = new ContentType(part.getContentType());
        String baseType = contentType.getBaseType().toLowerCase();

        if (logger.isTraceEnabled()) {
            try {
                logger.trace("Compression check.  MIME Base Content-Type:" + contentType.getBaseType());
                logger.trace("Compression check.  SMIME-TYPE:" + contentType.getParameter("smime-type"));
                logger.trace("Compressed MIME msg AFTER COMPRESSION Content-Disposition:" + part.getDisposition());
            } catch (MessagingException e) {
                logger.trace("Compression check: no data available.");
            }
        }
        if (baseType.equalsIgnoreCase("application/pkcs7-mime")) {
            String smimeType = contentType.getParameter("smime-type");
            boolean checkResult = (smimeType != null) && smimeType.equalsIgnoreCase("compressed-data");
            if (!checkResult && logger.isDebugEnabled()) {
                logger.debug("Check for compressed data failed on SMIME content type: " + smimeType);
            }
            return (checkResult);
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Check for compressed data failed on BASE content type: " + baseType);
        }
        return false;
    }

    public String calculateMIC(MimeBodyPart part, String digest, boolean includeHeaders) throws GeneralSecurityException, MessagingException, IOException {
        return calculateMIC(part, digest, includeHeaders, false);
    }

    public String calculateMIC(MimeBodyPart part, String digest, boolean includeHeaders, boolean noCanonicalize) throws GeneralSecurityException, MessagingException, IOException {
        String micAlg = convertAlgorithm(digest, true);

        if (logger.isDebugEnabled()) {
            logger.debug("Calc MIC called with digest: " + digest + " ::: Incl headers? " + includeHeaders + " ::: Prevent canonicalization: " + noCanonicalize + " ::: Encoding: " + part.getEncoding());
        }
        MessageDigest md = MessageDigest.getInstance(micAlg, "BC");

        if (includeHeaders && logger.isTraceEnabled()) {
            logger.trace("Calculating MIC on MIMEPART Headers: " + AS2Util.printHeaders(part.getAllHeaders()));
        }
        // convert the Mime data to a byte array, then to an InputStream
        ByteArrayOutputStream bOut = new ByteArrayOutputStream();

        // Canonicalize the data if not binary content transfer encoding
        OutputStream os = null;
        String encoding = part.getEncoding();
        // Default encoding in case the bodypart does not have a transfer encoding set
        if (encoding == null) {
            encoding = Session.DEFAULT_CONTENT_TRANSFER_ENCODING;
        }
        if ("binary".equals(encoding) || noCanonicalize) {
            os = bOut;
        } else {
            os = new CRLFOutputStream(bOut);
        }

        if (includeHeaders) {
            part.writeTo(os);
        } else {
            IOUtils.copy(part.getInputStream(), os);
        }

        byte[] data = bOut.toByteArray();

        InputStream bIn = trimCRLFPrefix(data);

        // calculate the hash of the data and mime header
        DigestInputStream digIn = new DigestInputStream(bIn, md);

        byte[] buf = new byte[4096];

        while (digIn.read(buf) >= 0) {
        }

        bOut.close();

        byte[] mic = digIn.getMessageDigest().digest();
        String micString = new String(Base64.encode(mic));
        StringBuffer micResult = new StringBuffer(micString);
        micResult.append(", ").append(digest);

        return micResult.toString();
    }

    public MimeBodyPart decrypt(MimeBodyPart part, Certificate cert, Key key) throws GeneralSecurityException, MessagingException, CMSException, IOException, SMIMEException {
        // Make sure the data is encrypted
        if (!isEncrypted(part)) {
            throw new GeneralSecurityException("Content-Type indicates data isn't encrypted");
        }

        // Cast parameters to what BC needs
        X509Certificate x509Cert = castCertificate(cert);

        // Parse the MIME body into an SMIME envelope object
        SMIMEEnveloped envelope = new SMIMEEnveloped(part);

        // Get the recipient object for decryption
        if (logger.isDebugEnabled()) {
            logger.debug("Extracted X500 info::  PRINCIPAL : " + x509Cert.getIssuerX500Principal() + " ::  NAME : " + x509Cert.getIssuerX500Principal().getName());
        }

        X500Name x500Name = new X500Name(x509Cert.getIssuerX500Principal().getName());
        KeyTransRecipientId certRecId = new KeyTransRecipientId(x500Name, x509Cert.getSerialNumber());
        RecipientInformationStore recipientInfoStore = envelope.getRecipientInfos();

        Collection<RecipientInformation> recipients = recipientInfoStore.getRecipients();

        if (recipients == null) {
            throw new GeneralSecurityException("Certificate recipients could not be extracted");
        }
        //RecipientInformation recipientInfo  = recipientInfoStore.get(recId);
        //Object recipient = null;        

        boolean foundRecipient = false;
        for (Iterator<RecipientInformation> iterator = recipients.iterator(); iterator.hasNext(); ) {
            RecipientInformation recipientInfo = iterator.next();
            //recipient = iterator.next();
            if (recipientInfo instanceof KeyTransRecipientInformation) {
                // X509CertificateHolder x509CertHolder = new X509CertificateHolder(x509Cert.getEncoded());

                //RecipientId rid = recipientInfo.getRID();
                if (certRecId.match(recipientInfo) && !foundRecipient) {
                    foundRecipient = true;
                    // byte[] decryptedData = recipientInfo.getContent(new JceKeyTransEnvelopedRecipient((PrivateKey)key).setProvider("BC"));
                    byte[] decryptedData = recipientInfo.getContent(new BcRSAKeyTransEnvelopedRecipient(PrivateKeyFactory.createKey(PrivateKeyInfo.getInstance(key.getEncoded()))));

                    return SMIMEUtil.toMimeBodyPart(decryptedData);
                } else {
                    if (logger.isDebugEnabled()) {
                        logger.debug("Failed match on recipient ID's:\n     RID from msg:" + recipientInfo.getRID().toString() + "    \n     RID from priv cert: " + certRecId.toString());
                    }
                }
            }
        }
        throw new GeneralSecurityException("Matching certificate recipient could not be found");
    }

    public void deinitialize() {
    }

    public MimeBodyPart encrypt(MimeBodyPart part, Certificate cert, String algorithm, String contentTxfrEncoding) throws GeneralSecurityException, SMIMEException, MessagingException {
        X509Certificate x509Cert = castCertificate(cert);


        SMIMEEnvelopedGenerator gen = new SMIMEEnvelopedGenerator();
        gen.setContentTransferEncoding(getEncoding(contentTxfrEncoding));

        if (logger.isDebugEnabled()) {
            logger.debug("Encrypting on MIME part containing the following headers: " + AS2Util.printHeaders(part.getAllHeaders()));
        }

        gen.addRecipientInfoGenerator(new JceKeyTransRecipientInfoGenerator(x509Cert).setProvider("BC"));

        return gen.generate(part, getOutputEncryptor(algorithm));
    }

    public void initialize() {
        Security.addProvider(new BouncyCastleProvider());

        MailcapCommandMap mc = (MailcapCommandMap) CommandMap.getDefaultCommandMap();
        mc.addMailcap("application/pkcs7-signature;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.pkcs7_signature");
        mc.addMailcap("application/pkcs7-mime;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.pkcs7_mime");
        mc.addMailcap("application/x-pkcs7-signature;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.x_pkcs7_signature");
        mc.addMailcap("application/x-pkcs7-mime;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.x_pkcs7_mime");
        mc.addMailcap("multipart/signed;; x-java-content-handler=org.bouncycastle.mail.smime.handlers.multipart_signed");
        CommandMap.setDefaultCommandMap(mc);
    }

    public MimeBodyPart sign(MimeBodyPart part, Certificate cert, Key key, String digest, String contentTxfrEncoding, boolean adjustDigestToOldName, boolean isRemoveCmsAlgorithmProtectionAttr) throws GeneralSecurityException, SMIMEException, MessagingException {
        //String signDigest = convertAlgorithm(digest, true);
        X509Certificate x509Cert = castCertificate(cert);
        PrivateKey privKey = castKey(key);
        String encryptAlg = cert.getPublicKey().getAlgorithm();

        // Fix copied from https://github.com/phax/as2-lib/commit/ed08dd00b6d721ec3e3e7255f642045c9cbee9c3
        SMIMESignedGenerator sGen = new SMIMESignedGenerator(adjustDigestToOldName ? SMIMESignedGenerator.RFC3851_MICALGS : SMIMESignedGenerator.RFC5751_MICALGS);
        sGen.setContentTransferEncoding(getEncoding(contentTxfrEncoding));
        SignerInfoGenerator sig;
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Params for creating SMIME signed generator:: SIGN DIGEST: " + digest + " PUB ENCRYPT ALG: " + encryptAlg + " X509 CERT: " + x509Cert);
                logger.debug("Signing on MIME part containing the following headers: " + AS2Util.printHeaders(part.getAllHeaders()));
            }
            // Remove the dash for SHA based digest for signing call
            if (digest.toUpperCase().startsWith("SHA-")) {
                digest = digest.replaceAll("-", "");
            }
            JcaSimpleSignerInfoGeneratorBuilder jSig = new JcaSimpleSignerInfoGeneratorBuilder().setProvider("BC");
            sig = jSig.build(digest + "with" + encryptAlg, privKey, x509Cert);
            // Some AS2 systems cannot handle certain OID's ...
            if (isRemoveCmsAlgorithmProtectionAttr) {
                final CMSAttributeTableGenerator sAttrGen = sig.getSignedAttributeTableGenerator();
                sig = new SignerInfoGenerator(sig, new DefaultSignedAttributeTableGenerator() {
                    @Override
                    public AttributeTable getAttributes(@SuppressWarnings("rawtypes") Map parameters) {
                        AttributeTable ret = sAttrGen.getAttributes(parameters);
                        return ret.remove(CMSAttributes.cmsAlgorithmProtect);
                    }
                }, sig.getUnsignedAttributeTableGenerator());
            }
        } catch (OperatorCreationException e) {
            throw new GeneralSecurityException(e);
        }
        sGen.addSignerInfoGenerator(sig);

        MimeMultipart signedData;

        signedData = sGen.generate(part);

        MimeBodyPart tmpBody = new MimeBodyPart();
        tmpBody.setContent(signedData);
        // Content-type header is required, unit tests fail badly on async MDNs if not set.
        tmpBody.setHeader("Content-Type", signedData.getContentType());
        return tmpBody;
    }

    public MimeBodyPart verifySignature(MimeBodyPart part, Certificate cert) throws GeneralSecurityException, IOException, MessagingException, CMSException, OperatorCreationException {
        // Make sure the data is signed
        if (!isSigned(part)) {
            throw new GeneralSecurityException("Content-Type indicates data isn't signed");
        }

        X509Certificate x509Cert = castCertificate(cert);

        MimeMultipart mainParts = (MimeMultipart) part.getContent();

        SMIMESigned signedPart = new SMIMESigned(mainParts);
        //SignerInformationStore  signers = signedPart.getSignerInfos();

        DigestCalculatorProvider dcp = new JcaDigestCalculatorProviderBuilder().setProvider("BC").build();
        String contentTxfrEnc = signedPart.getContent().getEncoding();
        if (contentTxfrEnc == null || contentTxfrEnc.length() < 1) {
            contentTxfrEnc = Session.DEFAULT_CONTENT_TRANSFER_ENCODING;
        }
        SMIMESignedParser ssp = new SMIMESignedParser(dcp, mainParts, contentTxfrEnc);
        SignerInformationStore sis = ssp.getSignerInfos();

        if (logger.isTraceEnabled()) {
            String headers = null;
            try {
                headers = AS2Util.printHeaders(part.getAllHeaders());
                logger.trace("Headers on MimeBodyPart passed in to signature verifier: " + headers);
                headers = AS2Util.printHeaders(ssp.getContent().getAllHeaders());
                logger.trace("Checking signature on SIGNED MIME part extracted from multipart contains headers: " + headers);
            } catch (Throwable e) {
                logger.trace("Error logging mime part for signer: " + org.openas2.logging.Log.getExceptionMsg(e), e);
            }
        }

        Iterator<SignerInformation> it = sis.getSigners().iterator();
        SignerInformationVerifier signerInfoVerifier = new JcaSimpleSignerInfoVerifierBuilder().setProvider("BC").build(x509Cert);
        while (it.hasNext()) {
            SignerInformation signer = it.next();
            if (logger.isTraceEnabled()) {
                try { // Code block below does not do null-checks or other encoding error checking. 
                    Map<Object, Attribute> attrTbl = signer.getSignedAttributes().toHashtable();
                    StringBuilder strBuf = new StringBuilder();
                    for (Map.Entry<Object, Attribute> pair : attrTbl.entrySet()) {
                        strBuf.append("\n\t").append(pair.getKey()).append(":=");
                        ASN1Encodable[] asn1s = pair.getValue().getAttributeValues();
                        for (int i = 0; i < asn1s.length; i++) {
                            strBuf.append(asn1s[i]).append(";");
                        }
                    }
                    logger.trace("Signer Attributes: " + strBuf.toString());

                    AttributeTable attributes = signer.getSignedAttributes();
                    Attribute attribute = attributes.get(CMSAttributes.messageDigest);
                    DEROctetString digest = (DEROctetString) attribute.getAttrValues().getObjectAt(0);
                    logger.trace("\t**** Signed Attribute Message-Digest := " + Hex.toHexString(digest.getOctets()));
                    logger.trace("\t**** Signed Content-Digest := " + Hex.toHexString(signer.getContentDigest()));
                } catch (Exception e) {
                    logger.trace("Signer Attributes: data not available.");
                }
            }
            if (signer.verify(signerInfoVerifier)) {
                logSignerInfo("Verified signature for signer info", signer, part, x509Cert);
                return signedPart.getContent();
            }
            logSignerInfo("Failed to verify signature for signer info", signer, part, x509Cert);
        }
        throw new SignatureException("Signature Verification failed");
    }

    public MimeBodyPart compress(Message msg, MimeBodyPart mbp, String compressionType, String contentTxfrEncoding) throws SMIMEException, OpenAS2Exception {
        OutputCompressor compressor = null;
        if (compressionType != null) {
            if (compressionType.equalsIgnoreCase(ICryptoHelper.COMPRESSION_ZLIB)) {
                compressor = new ZlibCompressor();
            } else {
                throw new OpenAS2Exception("Unsupported compression type: " + compressionType);
            }
        }
        SMIMECompressedGenerator sCompGen = new SMIMECompressedGenerator();
        sCompGen.setContentTransferEncoding(getEncoding(contentTxfrEncoding));
        MimeBodyPart smime = sCompGen.generate(mbp, compressor);
        if (logger.isTraceEnabled()) {
            try {
                logger.trace("Compressed MIME msg AFTER COMPRESSION Content-Type:" + smime.getContentType());
                logger.trace("Compressed MIME msg AFTER COMPRESSION Content-Disposition:" + smime.getDisposition());
            } catch (MessagingException e) {
            }
        }
        return smime;
    }

    public void decompress(AS2Message msg) throws DispositionException {
        try {
            if (logger.isDebugEnabled()) {
                logger.debug("Decompressing a compressed message");
            }
            SMIMECompressed compressed = new SMIMECompressed(msg.getData());
            // decompression step MimeBodyPart
            MimeBodyPart recoveredPart = SMIMEUtil.toMimeBodyPart(compressed.getContent(new ZlibExpanderProvider()));
            // Update the message object
            msg.setData(recoveredPart);
        } catch (Exception ex) {

            msg.setLogMsg("Error decompressing received message: " + ex.getCause());
            logger.error(msg, ex);
            throw new DispositionException(new DispositionType("automatic-action", "MDN-sent-automatically", "processed", "Error", "unexpected-processing-error"), AS2ReceiverModule.DISP_DECOMPRESSION_ERROR, ex);
        }
    }

    protected String getEncoding(String contentTxfrEncoding) {
        // Bouncy castle only deals with binary or base64 so pass base64 for 7bit, 8bit etc
        return "binary".equalsIgnoreCase(contentTxfrEncoding) ? "binary" : "base64";
    }

    protected X509Certificate castCertificate(Certificate cert) throws GeneralSecurityException {
        if (cert == null) {
            throw new GeneralSecurityException("Certificate is null");
        }
        if (!(cert instanceof X509Certificate)) {
            throw new GeneralSecurityException("Certificate must be an instance of X509Certificate");
        }

        return (X509Certificate) cert;
    }

    protected PrivateKey castKey(Key key) throws GeneralSecurityException {
        if (!(key instanceof PrivateKey)) {
            throw new GeneralSecurityException("Key must implement PrivateKey interface");
        }

        return (PrivateKey) key;
    }

    protected String convertAlgorithm(String algorithm, boolean toBC) throws NoSuchAlgorithmException {
        if (algorithm == null) {
            throw new NoSuchAlgorithmException("Algorithm is null");
        }
        if (toBC) {
            if (algorithm.toUpperCase().startsWith("SHA-")) {
                algorithm = algorithm.replaceAll("-", "");
            }
            if (algorithm.equalsIgnoreCase(DIGEST_MD5)) {
                return SMIMESignedGenerator.DIGEST_MD5;
            } else if (algorithm.equalsIgnoreCase(DIGEST_SHA1)) {
                return SMIMESignedGenerator.DIGEST_SHA1;
            } else if (algorithm.equalsIgnoreCase(DIGEST_SHA224)) {
                return SMIMESignedGenerator.DIGEST_SHA224;
            } else if (algorithm.equalsIgnoreCase(DIGEST_SHA256)) {
                return SMIMESignedGenerator.DIGEST_SHA256;
            } else if (algorithm.equalsIgnoreCase(DIGEST_SHA384)) {
                return SMIMESignedGenerator.DIGEST_SHA384;
            } else if (algorithm.equalsIgnoreCase(DIGEST_SHA512)) {
                return SMIMESignedGenerator.DIGEST_SHA512;
            } else if (algorithm.equalsIgnoreCase(CRYPT_3DES)) {
                return SMIMEEnvelopedGenerator.DES_EDE3_CBC;
            } else if (algorithm.equalsIgnoreCase(CRYPT_CAST5)) {
                return SMIMEEnvelopedGenerator.CAST5_CBC;
            } else if (algorithm.equalsIgnoreCase(CRYPT_IDEA)) {
                return SMIMEEnvelopedGenerator.IDEA_CBC;
            } else if (algorithm.equalsIgnoreCase(CRYPT_RC2)) {
                return SMIMEEnvelopedGenerator.RC2_CBC;
            } else if (algorithm.equalsIgnoreCase(CRYPT_RC2_CBC)) {
                return SMIMEEnvelopedGenerator.RC2_CBC;
            } else if (algorithm.equalsIgnoreCase(AES256_CBC)) {
                return SMIMEEnvelopedGenerator.AES256_CBC;
            } else if (algorithm.equalsIgnoreCase(AES192_CBC)) {
                return SMIMEEnvelopedGenerator.AES192_CBC;
            } else if (algorithm.equalsIgnoreCase(AES128_CBC)) {
                return SMIMEEnvelopedGenerator.AES128_CBC;
            } else if (algorithm.equalsIgnoreCase(AES256_WRAP)) {
                return SMIMEEnvelopedGenerator.AES256_WRAP;
            } else {
                throw new NoSuchAlgorithmException("Unsupported or invalid algorithm: " + algorithm);
            }
        }
        if (algorithm.equalsIgnoreCase(SMIMESignedGenerator.DIGEST_MD5)) {
            return DIGEST_MD5;
        } else if (algorithm.equalsIgnoreCase(SMIMESignedGenerator.DIGEST_SHA1)) {
            return DIGEST_SHA1;
        } else if (algorithm.equalsIgnoreCase(SMIMESignedGenerator.DIGEST_SHA224)) {
            return DIGEST_SHA224;
        } else if (algorithm.equalsIgnoreCase(SMIMESignedGenerator.DIGEST_SHA256)) {
            return DIGEST_SHA256;
        } else if (algorithm.equalsIgnoreCase(SMIMESignedGenerator.DIGEST_SHA384)) {
            return DIGEST_SHA384;
        } else if (algorithm.equalsIgnoreCase(SMIMESignedGenerator.DIGEST_SHA512)) {
            return DIGEST_SHA512;
        } else if (algorithm.equalsIgnoreCase(SMIMEEnvelopedGenerator.CAST5_CBC)) {
            return CRYPT_CAST5;
        } else if (algorithm.equalsIgnoreCase(SMIMEEnvelopedGenerator.AES128_CBC)) {
            return AES128_CBC;
        } else if (algorithm.equalsIgnoreCase(SMIMEEnvelopedGenerator.AES192_CBC)) {
            return AES192_CBC;
        } else if (algorithm.equalsIgnoreCase(SMIMEEnvelopedGenerator.AES256_CBC)) {
            return AES256_CBC;
        } else if (algorithm.equalsIgnoreCase(SMIMEEnvelopedGenerator.AES256_WRAP)) {
            return AES256_WRAP;
        } else if (algorithm.equalsIgnoreCase(SMIMEEnvelopedGenerator.DES_EDE3_CBC)) {
            return CRYPT_3DES;
        } else if (algorithm.equalsIgnoreCase(SMIMEEnvelopedGenerator.IDEA_CBC)) {
            return CRYPT_IDEA;
        } else if (algorithm.equalsIgnoreCase(SMIMEEnvelopedGenerator.RC2_CBC)) {
            return CRYPT_RC2;
        } else {
            throw new NoSuchAlgorithmException("Unknown algorithm: " + algorithm);
        }

    }


    /**
     * Looks up the correct ASN1 OID of the passed in algorithm string and returns the encryptor.
     * The encryption key length is set where necessary
     *
     * @param algorithm The name of the algorithm to use for encryption
     * @return the OutputEncryptor of the given hash algorithm
     * @throws NoSuchAlgorithmException - Houston we have a problem
     *                                  <p>
     *                                  TODO: Possibly just use new ASN1ObjectIdentifier(algorithm) instead of explicit lookup to support random configured algorithms
     *                                  but will require determining if this has any side effects from a security point of view.
     */
    protected OutputEncryptor getOutputEncryptor(String algorithm) throws NoSuchAlgorithmException {
        if (algorithm == null) {
            throw new NoSuchAlgorithmException("Algorithm is null");
        }
        ASN1ObjectIdentifier asn1ObjId = null;
        int keyLen = -1;
        if (algorithm.equalsIgnoreCase(DIGEST_MD2)) {
            asn1ObjId = new ASN1ObjectIdentifier(PKCSObjectIdentifiers.md2.getId());
        } else if (algorithm.equalsIgnoreCase(DIGEST_MD5)) {
            asn1ObjId = new ASN1ObjectIdentifier(PKCSObjectIdentifiers.md5.getId());
        } else if (algorithm.equalsIgnoreCase(DIGEST_SHA1)) {
            asn1ObjId = new ASN1ObjectIdentifier(OIWObjectIdentifiers.idSHA1.getId());
        } else if (algorithm.equalsIgnoreCase(DIGEST_SHA224)) {
            asn1ObjId = new ASN1ObjectIdentifier(NISTObjectIdentifiers.id_sha224.getId());
        } else if (algorithm.equalsIgnoreCase(DIGEST_SHA256)) {
            asn1ObjId = new ASN1ObjectIdentifier(NISTObjectIdentifiers.id_sha256.getId());
        } else if (algorithm.equalsIgnoreCase(DIGEST_SHA384)) {
            asn1ObjId = new ASN1ObjectIdentifier(NISTObjectIdentifiers.id_sha384.getId());
        } else if (algorithm.equalsIgnoreCase(DIGEST_SHA512)) {
            asn1ObjId = new ASN1ObjectIdentifier(NISTObjectIdentifiers.id_sha512.getId());
        } else if (algorithm.equalsIgnoreCase(CRYPT_3DES)) {
            asn1ObjId = new ASN1ObjectIdentifier(PKCSObjectIdentifiers.des_EDE3_CBC.getId());
        } else if (algorithm.equalsIgnoreCase(CRYPT_RC2) || algorithm.equalsIgnoreCase(CRYPT_RC2_CBC)) {
            asn1ObjId = new ASN1ObjectIdentifier(PKCSObjectIdentifiers.RC2_CBC.getId());
            keyLen = 40;
        } else if (algorithm.equalsIgnoreCase(AES128_CBC)) {
            asn1ObjId = CMSAlgorithm.AES128_CBC;
        } else if (algorithm.equalsIgnoreCase(AES192_CBC)) {
            asn1ObjId = CMSAlgorithm.AES192_CBC;
        } else if (algorithm.equalsIgnoreCase(AES256_CBC)) {
            asn1ObjId = CMSAlgorithm.AES256_CBC;
        } else if (algorithm.equalsIgnoreCase(AES256_WRAP)) {
            asn1ObjId = CMSAlgorithm.AES256_WRAP;
        } else if (algorithm.equalsIgnoreCase(CRYPT_CAST5)) {
            asn1ObjId = CMSAlgorithm.CAST5_CBC;
        } else if (algorithm.equalsIgnoreCase(CRYPT_IDEA)) {
            asn1ObjId = CMSAlgorithm.IDEA_CBC;
        } else {
            throw new NoSuchAlgorithmException("Unsupported or invalid algorithm: " + algorithm);
        }
        OutputEncryptor oe = null;
        try {
            if (keyLen < 0) {
                oe = new JceCMSContentEncryptorBuilder(asn1ObjId).setProvider("BC").build();
            } else {
                oe = new JceCMSContentEncryptorBuilder(asn1ObjId, keyLen).setProvider("BC").build();
            }
        } catch (CMSException e1) {
            throw new NoSuchAlgorithmException("Error creating encryptor builder using algorithm: " + algorithm + " Cause:" + e1.getCause());
        }
        return oe;
    }

    protected InputStream trimCRLFPrefix(byte[] data) {
        ByteArrayInputStream bIn = new ByteArrayInputStream(data);

        int scanPos = 0;
        int len = data.length;

        while (scanPos < (len - 1)) {
            if (new String(data, scanPos, 2).equals("\r\n")) {
                bIn.read();
                bIn.read();
                scanPos += 2;
            } else {
                return bIn;
            }
        }

        return bIn;
    }

    public KeyStore getKeyStore() throws KeyStoreException, NoSuchProviderException {
        return KeyStore.getInstance("PKCS12", "BC");
    }

    public KeyStore loadKeyStore(InputStream in, char[] password) throws Exception {
        KeyStore ks = getKeyStore();
        ks.load(in, password);
        return ks;
    }

    public KeyStore loadKeyStore(String filename, char[] password) throws Exception {
        FileInputStream fIn = new FileInputStream(filename);

        try {
            return loadKeyStore(fIn, password);
        } finally {
            fIn.close();
        }
    }

    public String getHeaderValue(MimeBodyPart part, String headerName) {
        try {
            String[] values = part.getHeader(headerName);
            if (values == null) {
                return null;
            }
            return values[0];
        } catch (MessagingException e) {
            return null;
        }
    }

    public void logSignerInfo(String msgPrefix, SignerInformation signer, MimeBodyPart part, X509Certificate cert) {
        if (logger.isDebugEnabled()) {
            try {
                logger.debug(msgPrefix + ": \n    Digest Alg OID: " + signer.getDigestAlgOID() + "\n    Encrypt Alg OID: " + signer.getEncryptionAlgOID() + "\n    Signer Version: " + signer.getVersion() + "\n    Content Digest: " + Arrays.toString(signer.getContentDigest()) + "\n    Content Type: " + signer.getContentType() + "\n    SID: " + signer.getSID().getIssuer() + "\n    Signature: " + Arrays.toString(signer.getSignature()) + "\n    Unsigned attribs: " + signer.getUnsignedAttributes() + "\n    Content-transfer-encoding: " + part.getEncoding() + "\n    Certificate: " + cert);
            } catch (Throwable e) {
                logger.debug("Error logging signer info: " + org.openas2.logging.Log.getExceptionMsg(e), e);
            }
        }
    }
}
