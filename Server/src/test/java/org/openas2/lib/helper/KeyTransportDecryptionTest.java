package org.openas2.lib.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.security.spec.MGF1ParameterSpec;
import java.util.Date;

import javax.crypto.spec.OAEPParameterSpec;
import javax.crypto.spec.PSource;

import jakarta.mail.internet.MimeBodyPart;

import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cms.CMSAlgorithm;
import org.bouncycastle.cms.RecipientInfoGenerator;
import org.bouncycastle.cms.jcajce.JceCMSContentEncryptorBuilder;
import org.bouncycastle.cms.jcajce.JceKeyTransRecipientInfoGenerator;
import org.bouncycastle.mail.smime.SMIMEEnvelopedGenerator;
import org.bouncycastle.operator.jcajce.JcaAlgorithmParametersConverter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openas2.util.AS2Util;

/**
 * Verifies that an inbound message decrypts whichever RSA key transport the sender wrapped its content key with.
 * <p>
 * PKCS#1 v1.5 is what OpenAS2 itself sends, but RSAES-OAEP is what others send by default: Microsoft BizTalk is
 * one. The algorithm is named in the envelope's recipient info, so decryption has to honour it rather than
 * assume PKCS#1 v1.5, which fails an OAEP-wrapped key with "block incorrect".
 */
public class KeyTransportDecryptionTest {

    private static final String PAYLOAD = "<order/>";

    private static KeyPair receiverKeys;
    private static X509Certificate receiverCert;

    @BeforeAll
    public static void setUp() throws Exception {
        AS2Util.getCryptoHelper(); // registers the BC provider
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        receiverKeys = kpg.generateKeyPair();
        receiverCert = selfSignedCert(receiverKeys, "CN=receiver");
    }

    @Test
    public void aPkcs1WrappedKeyDecrypts() throws Exception {
        assertDecrypts(new JceKeyTransRecipientInfoGenerator(receiverCert).setProvider("BC"));
    }

    @Test
    public void anOaepWrappedKeyWithDefaultParametersDecrypts() throws Exception {
        assertDecrypts(oaep(OAEPParameterSpec.DEFAULT));
    }

    @Test
    public void anOaepWrappedKeyWithSha256Decrypts() throws Exception {
        assertDecrypts(oaep(new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT)));
    }

    private static void assertDecrypts(RecipientInfoGenerator recipient) throws Exception {
        MimeBodyPart part = new MimeBodyPart();
        part.setContent(PAYLOAD, "application/xml");
        part.setHeader("Content-Type", "application/xml");

        SMIMEEnvelopedGenerator gen = new SMIMEEnvelopedGenerator();
        gen.addRecipientInfoGenerator(recipient);
        MimeBodyPart encrypted = gen.generate(part,
                new JceCMSContentEncryptorBuilder(CMSAlgorithm.AES256_CBC).setProvider("BC").build());

        MimeBodyPart decrypted = new BCCryptoHelper().decrypt(encrypted, receiverCert, receiverKeys.getPrivate());

        assertEquals(PAYLOAD, new String(decrypted.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    private static RecipientInfoGenerator oaep(OAEPParameterSpec spec) throws Exception {
        return new JceKeyTransRecipientInfoGenerator(receiverCert,
                new JcaAlgorithmParametersConverter().getAlgorithmIdentifier(PKCSObjectIdentifiers.id_RSAES_OAEP, spec))
                .setProvider("BC");
    }

    private static X509Certificate selfSignedCert(KeyPair keyPair, String dn) throws Exception {
        X500Name subject = new X500Name(dn);
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject, BigInteger.valueOf(System.nanoTime()),
                new Date(System.currentTimeMillis() - 3600_000L),
                new Date(System.currentTimeMillis() + 24 * 3600_000L),
                subject, keyPair.getPublic());
        return new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate())));
    }
}
