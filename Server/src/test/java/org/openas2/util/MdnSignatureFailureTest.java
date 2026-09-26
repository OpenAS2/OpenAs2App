package org.openas2.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SignatureException;
import java.security.cert.X509Certificate;
import java.util.Date;

import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openas2.OpenAS2Exception;
import org.openas2.message.AS2Message;
import org.openas2.message.AS2MessageMDN;
import org.openas2.message.Message;

/**
 * Verifies that parsing an MDN tells a signature that does not verify apart from every other failure.
 * <p>
 * Only the former is retried with the partner's fallback certificate, because it is the failure a
 * partner rotating the certificate it signs MDNs with produces. Any other failure has nothing to do
 * with which certificate was used, so retrying it would only hide the real reason behind a second,
 * misleading one.
 */
public class MdnSignatureFailureTest {

    private static KeyPair partnerKeys;
    private static X509Certificate partnerCert;
    private static X509Certificate otherCert;

    @BeforeAll
    public static void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        partnerKeys = kpg.generateKeyPair();
        partnerCert = selfSignedCert(partnerKeys, "CN=partner-current");
        otherCert = selfSignedCert(kpg.generateKeyPair(), "CN=partner-previous");
    }

    @Test
    public void anMdnSignedWithAnotherCertificateIsASignatureFailure() throws Exception {
        Message msg = messageWithMdn(signedReport());

        assertThrows(SignatureException.class, () -> AS2Util.parseMDN(msg, otherCert),
                "this is the failure a partner rotating its certificate produces, so it must be distinguishable");
    }

    @Test
    public void anMdnSignedWithTheGivenCertificateIsParsed() throws Exception {
        Message msg = messageWithMdn(signedReport());

        assertTrue(AS2Util.parseMDN(msg, partnerCert), "an MDN signed with the given certificate must verify and parse");
        assertEquals("processed", msg.getMDN().getAttribute(AS2MessageMDN.MDNA_DISPOSITION).split(";")[1].trim());
    }

    @Test
    public void anMdnThatCannotBeReadIsNotReportedAsASignatureFailure() throws Exception {
        // Claims to be signed but is not a signed structure at all: no certificate would ever verify it
        MimeBodyPart garbage = new MimeBodyPart();
        garbage.setContent("not a signed structure", "multipart/signed; protocol=\"application/pkcs7-signature\"; boundary=\"none\"");
        garbage.setHeader("Content-Type", "multipart/signed; protocol=\"application/pkcs7-signature\"; boundary=\"none\"");
        Message msg = messageWithMdn(garbage);

        OpenAS2Exception thrown = assertThrows(OpenAS2Exception.class, () -> AS2Util.parseMDN(msg, partnerCert),
                "a malformed MDN must not be retried with the fallback, which would mask why it failed");
        assertEquals("Failed to verify signature of received MDN.", thrown.getMessage());
    }

    private MimeBodyPart signedReport() throws Exception {
        MimeMultipart report = new MimeMultipart("report; report-type=disposition-notification");
        MimeBodyPart text = new MimeBodyPart();
        text.setText("The message was received.");
        report.addBodyPart(text);
        MimeBodyPart disposition = new MimeBodyPart();
        disposition.setContent("Disposition: automatic-action/MDN-sent-automatically; processed\r\n", "message/disposition-notification");
        report.addBodyPart(disposition);
        MimeBodyPart reportPart = new MimeBodyPart();
        reportPart.setContent(report);
        reportPart.setHeader("Content-Type", report.getContentType());

        return AS2Util.getCryptoHelper().sign(reportPart, partnerCert, partnerKeys.getPrivate(), "SHA-256", "binary", false, false);
    }

    private Message messageWithMdn(MimeBodyPart mdnData) {
        AS2Message msg = new AS2Message();
        AS2MessageMDN mdn = new AS2MessageMDN(msg, false);
        mdn.setData(mdnData);
        msg.setMDN(mdn);
        return msg;
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
