package org.openas2.util;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the trust manager that trusts what any one of several trust stores trusts.
 * <p>
 * The HTTPS tests in {@link HTTPUtilSslTrustStoreTest} cover the server side end to end; these cover
 * the rest of the contract directly: client chains, the combined list of accepted issuers, and that a
 * chain no store trusts is rejected with every store's reason kept.
 */
@TestInstance(Lifecycle.PER_CLASS)
public class MultiKeyStoreTrustManagerTest {

    private X509Certificate firstRoot;
    private X509Certificate secondRoot;
    private X509Certificate unknownRoot;
    private MultiKeyStoreTrustManager both;

    @BeforeAll
    public void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        firstRoot = selfSignedCa(kpg.generateKeyPair(), "CN=First Root");
        secondRoot = selfSignedCa(kpg.generateKeyPair(), "CN=Second Root");
        unknownRoot = selfSignedCa(kpg.generateKeyPair(), "CN=Unknown Root");
        both = MultiKeyStoreTrustManager.forKeyStores(trustStore(firstRoot), trustStore(secondRoot));
    }

    @Test
    public void aChainTrustedByTheFirstStoreIsTrusted() throws Exception {
        both.checkServerTrusted(new X509Certificate[]{firstRoot}, "RSA");
        both.checkClientTrusted(new X509Certificate[]{firstRoot}, "RSA");
    }

    @Test
    public void aChainTrustedOnlyByALaterStoreIsTrusted() throws Exception {
        both.checkServerTrusted(new X509Certificate[]{secondRoot}, "RSA");
        both.checkClientTrusted(new X509Certificate[]{secondRoot}, "RSA");
    }

    @Test
    public void aChainNoStoreTrustsIsRejectedWithEveryReasonKept() {
        CertificateException rejected = assertThrows(CertificateException.class,
                () -> both.checkServerTrusted(new X509Certificate[]{unknownRoot}, "RSA"));
        assertEquals(1, rejected.getSuppressed().length, "the second store's reason should be attached to the first's");

        assertThrows(CertificateException.class, () -> both.checkClientTrusted(new X509Certificate[]{unknownRoot}, "RSA"),
                "client chains must be checked against the stores too, not accepted by default");
    }

    @Test
    public void theAcceptedIssuersAreThoseOfEveryStore() {
        List<X509Certificate> issuers = Arrays.asList(both.getAcceptedIssuers());

        assertEquals(2, issuers.size());
        assertTrue(issuers.contains(firstRoot) && issuers.contains(secondRoot));
    }

    @Test
    public void aNullStoreStandsForTheJvmTrustStore() throws Exception {
        // The JVM trust store holds the public roots, so combining with it can only add issuers
        int jvmIssuers = MultiKeyStoreTrustManager.forKeyStores((KeyStore) null).getAcceptedIssuers().length;

        assertEquals(jvmIssuers + 1, MultiKeyStoreTrustManager.forKeyStores(trustStore(firstRoot), null).getAcceptedIssuers().length);
    }

    @Test
    public void atLeastOneTrustManagerIsRequired() {
        assertThrows(IllegalArgumentException.class, () -> new MultiKeyStoreTrustManager(Collections.emptyList()));
    }

    private KeyStore trustStore(X509Certificate cert) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setCertificateEntry("cert", cert);
        return ks;
    }

    private X509Certificate selfSignedCa(KeyPair keys, String dn) throws Exception {
        X500Name name = new X500Name(dn);
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(name, BigInteger.valueOf(System.nanoTime()),
                new Date(System.currentTimeMillis() - 3600_000L), new Date(System.currentTimeMillis() + 24 * 3600_000L),
                name, keys.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.digitalSignature));
        return new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(keys.getPrivate())));
    }
}
