package org.openas2.util;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.function.Executable;
import org.openas2.processor.sender.HttpSenderModule;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.CertPathBuilderException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies how the SSL trust keystore combines with the JVM trust store for outbound HTTPS.
 * <p>
 * The situation this covers is a partner whose HTTPS server presents only its leaf certificate and
 * not the intermediate that issued it, so the chain cannot be built from the JVM's trusted roots
 * alone. Putting the intermediate in the SSL trust keystore completes the chain, but enabling that
 * keystore used to make it the only source of trust, so every other partner with a publicly issued
 * certificate stopped being trusted. With "ssl_trust_keystore.include_jvm_trust_store" set the
 * keystore only adds to the JVM's trust, and without it the behaviour is exactly as before.
 * <p>
 * The JVM trust store is stood in for by a generated store, so the "public" root is as trusted here
 * as a root in cacerts would be, without touching the real one.
 */
@TestInstance(Lifecycle.PER_CLASS)
public class HTTPUtilSslTrustStoreTest {

    private static final char[] PASSWORD = "testpwd".toCharArray();

    private final List<HttpsServer> servers = new ArrayList<HttpsServer>();

    // The partner: leaf issued by an intermediate, and the server sends only the leaf
    private X509Certificate partnerIntermediate;
    private X509Certificate partnerRoot;
    private String partnerUrl;
    // A partner with a publicly issued certificate, chaining to a root in the JVM trust store
    private X509Certificate publicRoot;
    private String publicPartnerUrl;
    // A server nobody has said to trust
    private String untrustedUrl;

    @BeforeAll
    public void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);

        KeyPair partnerRootKeys = kpg.generateKeyPair();
        partnerRoot = caCert(partnerRootKeys, "CN=Partner Root CA", null, null);
        KeyPair partnerIntermediateKeys = kpg.generateKeyPair();
        partnerIntermediate = caCert(partnerIntermediateKeys, "CN=Partner Issuing CA", partnerRoot, partnerRootKeys);
        KeyPair partnerLeafKeys = kpg.generateKeyPair();
        X509Certificate partnerLeaf = leafCert(partnerLeafKeys, partnerIntermediate, partnerIntermediateKeys);
        partnerUrl = startServer(partnerLeafKeys, partnerLeaf);

        KeyPair publicRootKeys = kpg.generateKeyPair();
        publicRoot = caCert(publicRootKeys, "CN=Public Root CA", null, null);
        KeyPair publicLeafKeys = kpg.generateKeyPair();
        X509Certificate publicLeaf = leafCert(publicLeafKeys, publicRoot, publicRootKeys);
        publicPartnerUrl = startServer(publicLeafKeys, publicLeaf);

        KeyPair unknownRootKeys = kpg.generateKeyPair();
        X509Certificate unknownRoot = caCert(unknownRootKeys, "CN=Unknown Root CA", null, null);
        KeyPair unknownLeafKeys = kpg.generateKeyPair();
        untrustedUrl = startServer(unknownLeafKeys, leafCert(unknownLeafKeys, unknownRoot, unknownRootKeys));
    }

    @AfterEach
    public void resetTrust() {
        Properties.getProperties().remove(HTTPUtil.HTTP_PROP_SSL_TRUST_INCLUDE_JVM_TRUST_STORE);
        HTTPUtil.jvmTrustStore = null;
    }

    @AfterAll
    public void tearDown() {
        for (HttpsServer server : servers) {
            server.stop(0);
        }
    }

    /* ------------------------------------------------------- the partner sending only its leaf */

    @Test
    public void aLeafOnlyServerIsNotTrustedFromTheRootAlone() throws Exception {
        // The original problem: the root is trusted but the missing intermediate breaks the chain
        HTTPUtil.jvmTrustStore = trustStore(partnerRoot);
        enableJvmTrust();

        assertChainNotTrusted(() -> get(partnerUrl, trustStore(publicRoot)),
                "with no intermediate available anywhere the chain cannot be built");
    }

    @Test
    public void theIntermediateInTheSslTrustKeystoreCompletesTheChain() throws Exception {
        assertEquals(200, get(partnerUrl, trustStore(partnerIntermediate)),
                "an intermediate in the trust store is enough to trust the leaf it issued");
    }

    /* ----------------------------------------- what enabling the keystore does to other partners */

    @Test
    public void withoutTheFlagTheKeystoreReplacesTheJvmTrustStore() throws Exception {
        // Unchanged behaviour: the keystore is the only source of trust
        HTTPUtil.jvmTrustStore = trustStore(publicRoot);

        assertChainNotTrusted(() -> get(publicPartnerUrl, trustStore(partnerIntermediate)),
                "without the flag a partner trusted only by the JVM trust store must still be rejected");
    }

    @Test
    public void withTheFlagAPartnerTrustedByTheJvmIsStillTrusted() throws Exception {
        HTTPUtil.jvmTrustStore = trustStore(publicRoot);
        enableJvmTrust();

        assertEquals(200, get(publicPartnerUrl, trustStore(partnerIntermediate)),
                "adding one partner's intermediate must not stop other partners being trusted");
    }

    @Test
    public void withTheFlagTheKeystoreIsStillHonoured() throws Exception {
        HTTPUtil.jvmTrustStore = trustStore(publicRoot);
        enableJvmTrust();

        assertEquals(200, get(partnerUrl, trustStore(partnerIntermediate)));
    }

    @Test
    public void withTheFlagAServerNeitherStoreTrustsIsRejected() throws Exception {
        HTTPUtil.jvmTrustStore = trustStore(publicRoot);
        enableJvmTrust();

        assertChainNotTrusted(() -> get(untrustedUrl, trustStore(partnerIntermediate)),
                "combining the stores must not trust anything that neither of them trusts");
    }

    /* ------------------------------------------------------------------------------------- fixtures */

    /**
     * Asserts the connection failed because no trusted certification path could be built, rather than
     * for some other reason such as the host name, which would let these tests pass without proving
     * anything about trust.
     */
    private void assertChainNotTrusted(Executable connect, String why) {
        IOException thrown = assertThrows(IOException.class, connect, why);
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            if (t instanceof CertPathBuilderException) {
                return;
            }
        }
        throw new AssertionError(why + ", but it failed for a different reason: " + thrown, thrown);
    }

    private void enableJvmTrust() {
        Properties.setProperty(HTTPUtil.HTTP_PROP_SSL_TRUST_INCLUDE_JVM_TRUST_STORE, "true");
    }

    private int get(String url, KeyStore sslTrustKeystore) throws Exception {
        Map<String, Object> options = new HashMap<String, Object>();
        options.put(HTTPUtil.PARAM_CONNECT_TIMEOUT, "10000");
        options.put(HTTPUtil.PARAM_SOCKET_TIMEOUT, "10000");
        options.put(HttpSenderModule.PARAM_CUSTOM_SSL_TRUST_STORE, sslTrustKeystore);
        return HTTPUtil.execRequest(HTTPUtil.Method.GET, url, null, null, null, options, 0L, false).getStatusCode();
    }

    private KeyStore trustStore(X509Certificate... certs) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        for (int i = 0; i < certs.length; i++) {
            ks.setCertificateEntry("cert" + i, certs[i]);
        }
        return ks;
    }

    private String startServer(KeyPair keys, X509Certificate leaf) throws Exception {
        // The key entry holds only the leaf, so that is all the server presents
        KeyStore serverKs = KeyStore.getInstance("PKCS12");
        serverKs.load(null, null);
        serverKs.setKeyEntry("server", keys.getPrivate(), PASSWORD, new X509Certificate[]{leaf});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(serverKs, PASSWORD);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);

        HttpsServer server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(ctx));
        server.createContext("/as2", exchange -> {
            byte[] body = "OK".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        servers.add(server);
        return "https://127.0.0.1:" + server.getAddress().getPort() + "/as2";
    }

    private X509Certificate caCert(KeyPair keys, String dn, X509Certificate issuer, KeyPair issuerKeys) throws Exception {
        X500Name subject = new X500Name(dn);
        X500Name issuerName = issuer == null ? subject : X500Name.getInstance(issuer.getSubjectX500Principal().getEncoded());
        JcaX509ExtensionUtils ext = new JcaX509ExtensionUtils();
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(issuerName, serial(), notBefore(), notAfter(),
                subject, keys.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
        builder.addExtension(Extension.subjectKeyIdentifier, false, ext.createSubjectKeyIdentifier(keys.getPublic()));
        if (issuer != null) {
            builder.addExtension(Extension.authorityKeyIdentifier, false, ext.createAuthorityKeyIdentifier(issuer));
        }
        KeyPair signer = issuerKeys == null ? keys : issuerKeys;
        return new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(signer.getPrivate())));
    }

    private X509Certificate leafCert(KeyPair keys, X509Certificate issuer, KeyPair issuerKeys) throws Exception {
        JcaX509ExtensionUtils ext = new JcaX509ExtensionUtils();
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                X500Name.getInstance(issuer.getSubjectX500Principal().getEncoded()), serial(), notBefore(), notAfter(),
                new X500Name("CN=localhost"), keys.getPublic());
        builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment));
        builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(new GeneralName[]{
                new GeneralName(GeneralName.dNSName, "localhost"),
                new GeneralName(GeneralName.iPAddress, "127.0.0.1")}));
        builder.addExtension(Extension.authorityKeyIdentifier, false, ext.createAuthorityKeyIdentifier(issuer));
        return new JcaX509CertificateConverter().getCertificate(
                builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(issuerKeys.getPrivate())));
    }

    private BigInteger serial() {
        return BigInteger.valueOf(System.nanoTime());
    }

    private Date notBefore() {
        return new Date(System.currentTimeMillis() - 3600_000L);
    }

    private Date notAfter() {
        return new Date(System.currentTimeMillis() + 24 * 3600_000L);
    }
}
