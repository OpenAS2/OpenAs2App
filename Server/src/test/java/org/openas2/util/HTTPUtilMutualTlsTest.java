package org.openas2.util;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.io.TempDir;
import org.openas2.processor.sender.HttpSenderModule;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManagerFactory;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that outbound HTTPS connections present a client certificate (mutual TLS)
 * when a client keystore is configured in the request options, against a local HTTPS
 * server that requires client authentication.
 */
@TestInstance(Lifecycle.PER_CLASS)
public class HTTPUtilMutualTlsTest {

    private static final char[] PASSWORD = "testpwd".toCharArray();

    @TempDir
    static Path tempDir;

    private HttpsServer server;
    private String url;
    private String clientKeystorePath;
    private KeyStore serverCertTrustStore;

    @BeforeAll
    public void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        KeyPair serverKeys = kpg.generateKeyPair();
        KeyPair trustedClientKeys = kpg.generateKeyPair();
        KeyPair untrustedClientKeys = kpg.generateKeyPair();

        X509Certificate serverCert = selfSignedCert(serverKeys, "CN=localhost", true);
        X509Certificate trustedClientCert = selfSignedCert(trustedClientKeys, "CN=openas2-client", false);
        X509Certificate untrustedClientCert = selfSignedCert(untrustedClientKeys, "CN=other-client", false);

        // Client keystore holding both keys so the alias parameter decides which one is used
        KeyStore clientKs = KeyStore.getInstance("PKCS12");
        clientKs.load(null, null);
        clientKs.setKeyEntry("trusted", trustedClientKeys.getPrivate(), PASSWORD, new X509Certificate[]{trustedClientCert});
        clientKs.setKeyEntry("untrusted", untrustedClientKeys.getPrivate(), PASSWORD, new X509Certificate[]{untrustedClientCert});
        clientKeystorePath = tempDir.resolve("client_certs.p12").toString();
        try (FileOutputStream fos = new FileOutputStream(clientKeystorePath)) {
            clientKs.store(fos, PASSWORD);
        }

        // Trust store so the HTTP client trusts the self-signed server certificate
        serverCertTrustStore = KeyStore.getInstance("PKCS12");
        serverCertTrustStore.load(null, null);
        serverCertTrustStore.setCertificateEntry("server", serverCert);

        // HTTPS server that requires a client certificate and only trusts the "trusted" one
        KeyStore serverKs = KeyStore.getInstance("PKCS12");
        serverKs.load(null, null);
        serverKs.setKeyEntry("server", serverKeys.getPrivate(), PASSWORD, new X509Certificate[]{serverCert});
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(serverKs, PASSWORD);
        KeyStore clientTrust = KeyStore.getInstance("PKCS12");
        clientTrust.load(null, null);
        clientTrust.setCertificateEntry("client", trustedClientCert);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(clientTrust);
        SSLContext serverCtx = SSLContext.getInstance("TLS");
        serverCtx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);

        server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setHttpsConfigurator(new HttpsConfigurator(serverCtx) {
            @Override
            public void configure(HttpsParameters params) {
                SSLParameters sslParams = getSSLContext().getDefaultSSLParameters();
                sslParams.setNeedClientAuth(true);
                params.setSSLParameters(sslParams);
            }
        });
        server.createContext("/as2", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {
                byte[] body = "OK".getBytes();
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(body);
                }
            }
        });
        server.setExecutor(null);
        server.start();
        url = "https://127.0.0.1:" + server.getAddress().getPort() + "/as2";
    }

    @AfterAll
    public void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private X509Certificate selfSignedCert(KeyPair keyPair, String dn, boolean withLocalhostSan) throws Exception {
        X500Name subject = new X500Name(dn);
        JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject, BigInteger.valueOf(System.nanoTime()),
                new Date(System.currentTimeMillis() - 3600_000L),
                new Date(System.currentTimeMillis() + 24 * 3600_000L),
                subject, keyPair.getPublic());
        if (withLocalhostSan) {
            builder.addExtension(Extension.subjectAlternativeName, false, new GeneralNames(new GeneralName[]{
                    new GeneralName(GeneralName.dNSName, "localhost"),
                    new GeneralName(GeneralName.iPAddress, "127.0.0.1")}));
        }
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA").build(keyPair.getPrivate());
        return new JcaX509CertificateConverter().getCertificate(builder.build(signer));
    }

    private Map<String, Object> baseOptions() {
        Map<String, Object> options = new HashMap<String, Object>();
        options.put(HTTPUtil.PARAM_CONNECT_TIMEOUT, "10000");
        options.put(HTTPUtil.PARAM_SOCKET_TIMEOUT, "10000");
        // Trust the self-signed server certificate via the custom trust store mechanism
        options.put(HttpSenderModule.PARAM_CUSTOM_SSL_TRUST_STORE, serverCertTrustStore);
        return options;
    }

    private Map<String, Object> mtlsOptions(String alias) {
        Map<String, Object> options = baseOptions();
        options.put(HTTPUtil.PARAM_HTTPS_CLIENT_KEYSTORE, clientKeystorePath);
        options.put(HTTPUtil.PARAM_HTTPS_CLIENT_KEYSTORE_PASSWORD, new String(PASSWORD));
        if (alias != null) {
            options.put(HTTPUtil.PARAM_HTTPS_CLIENT_CERT_ALIAS, alias);
        }
        return options;
    }

    @Test
    public void presentsClientCertificateWhenConfigured() throws Exception {
        ResponseWrapper resp = HTTPUtil.execRequest(HTTPUtil.Method.GET, url, null, null, null, mtlsOptions("trusted"), 0L, false);
        assertEquals(200, resp.getStatusCode());
    }

    @Test
    public void presentsClientCertificateWithOverriddenSslChecks() throws Exception {
        Map<String, Object> options = mtlsOptions("trusted");
        options.put(HTTPUtil.HTTP_PROP_OVERRIDE_SSL_CHECKS, "true");
        ResponseWrapper resp = HTTPUtil.execRequest(HTTPUtil.Method.GET, url, null, null, null, options, 0L, false);
        assertEquals(200, resp.getStatusCode());
    }

    @Test
    public void handshakeFailsWithoutClientCertificate() {
        assertThrows(IOException.class, () -> HTTPUtil.execRequest(HTTPUtil.Method.GET, url, null, null, null, baseOptions(), 0L, false));
    }

    @Test
    public void aliasSelectsTheKeyPresentedToTheServer() {
        // The untrusted key is a valid entry in the same keystore but the server rejects it,
        // proving the alias parameter controls which certificate is presented
        assertThrows(IOException.class, () -> HTTPUtil.execRequest(HTTPUtil.Method.GET, url, null, null, null, mtlsOptions("untrusted"), 0L, false));
    }

    @Test
    public void unknownAliasFailsWithClearError() {
        Exception e = assertThrows(Exception.class, () -> HTTPUtil.execRequest(HTTPUtil.Method.GET, url, null, null, null, mtlsOptions("no-such-alias"), 0L, false));
        org.hamcrest.MatcherAssert.assertThat(e.getMessage(), org.hamcrest.CoreMatchers.containsString("no-such-alias"));
    }
}
