package org.openas2.app;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.openas2.cert.CertificateFactory;
import org.openas2.cert.X509CertificateFactory;
import org.openas2.cmd.processor.restapi.AuthenticationRequestFilter;
import org.openas2.util.Properties;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.Key;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.util.Base64;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the PATCH endpoint that partially updates one partner, partnership or certificate.
 * <p>
 * PATCH exists so a caller no longer has to delete an entry and recreate it to change it, which
 * leaves nothing behind if the recreate fails. These tests go over real HTTP because the routing is
 * the part that was missing: the update commands are covered directly by UpdateCommandsTest.
 */
@TestInstance(Lifecycle.PER_CLASS)
public class PatchApiTest extends BaseServerSetup {

    private static final String REST_HOST = "http://127.0.0.1:8087";
    private static final String BASE_URL = REST_HOST + "/api/";
    private static final String AUTH_USER = "admin";
    private static final String AUTH_PWD = "admin";

    private static OpenAS2Server serverInstance;

    @BeforeAll
    public void setUp() throws Exception {
        super.createFileSystemResources(this.getClass().getName());
        try (FileOutputStream fos = new FileOutputStream(openAS2PropertiesFile)) {
            fos.write("restapi.command.processor.enabled=true\n".getBytes());
            fos.write(("restapi.command.processor.baseuri=" + REST_HOST + "\n").getBytes());
            fos.write(("restapi.command.processor.userid=" + AUTH_USER + "\n").getBytes());
            fos.write(("restapi.command.processor.password=" + AUTH_PWD + "\n").getBytes());
        }
        System.setProperty(Properties.OPENAS2_PROPERTIES_FILE_PROP, openAS2PropertiesFile.getAbsolutePath());
        try {
            serverInstance = new OpenAS2Server.Builder().run(configDir.getAbsolutePath() + "/config.xml");
        } catch (Throwable e) {
            System.err.println("ERROR occurred: " + ExceptionUtils.getStackTrace(e));
            throw new Exception(e);
        }
    }

    @AfterAll
    public void tearDown() throws Exception {
        if (serverInstance != null) {
            serverInstance.shutdown();
        }
        System.clearProperty(Properties.OPENAS2_PROPERTIES_FILE_PROP);
    }

    @Test
    public void patchUpdatesAPartnerAttribute() throws Exception {
        String body = patch("partner/PartnerA", true, param("email", "patched@example.com"));
        assertTrue(body.contains("\"OK\""), body);

        String view = doGet("partner/view/PartnerA");
        assertTrue(view.contains("patched@example.com"), "the change should be visible: " + view);
    }

    @Test
    public void patchUsesTheWholeNameFromThePath() throws Exception {
        // The item ID must not lose its first character on the way to the command, which is what
        // happens if the leading path separator handling is got wrong
        String body = patch("partner/PartnerB", true, param("email", "wholename@example.com"));
        assertTrue(body.contains("\"OK\""), body);
        assertTrue(body.contains("PartnerB"), "the result should name the partner that was patched: " + body);

        String view = doGet("partner/view/PartnerB");
        assertTrue(view.contains("wholename@example.com"), view);
        // If the first character were dropped the command would have been handed "artnerB", which is
        // not a partner at all, so asking for that name must still be an error
        assertTrue(doGet("partner/view/artnerB").contains("ERROR"),
                "a truncated partner name must not resolve to anything");
    }

    @Test
    public void patchLeavesAttributesThatWereNotSuppliedAlone() throws Exception {
        String before = doGet("partner/view/PartnerA");
        assertTrue(before.contains("PartnerA_OID"), "fixture should have an as2_id: " + before);

        patch("partner/PartnerA", true, param("email", "another@example.com"));

        String after = doGet("partner/view/PartnerA");
        assertTrue(after.contains("PartnerA_OID"), "an attribute that was not patched must survive: " + after);
    }

    @Test
    public void patchUpdatesAPartnershipAttributeAndPollerConfig() throws Exception {
        String body = patch("partnership/MyCompany-to-PartnerA", true,
                param("subject", "Patched subject"), param("pollerConfig.interval", "25"));
        assertTrue(body.contains("\"OK\""), body);

        String view = doGet("partnership/view/MyCompany-to-PartnerA");
        assertTrue(view.contains("Patched subject"), view);
    }

    @Test
    public void patchingAnUnknownPartnerReportsTheReason() throws Exception {
        String body = patch("partner/NoSuchPartner", true, param("email", "x@y.com"));

        assertTrue(body.contains("ERROR"), body);
        assertTrue(body.contains("Unknown partner name"), "the reason should come back to the caller: " + body);
    }

    @Test
    public void patchingWithNothingToChangeReportsTheReason() throws Exception {
        String body = patch("partnership/MyCompany-to-PartnerA", true);

        assertTrue(body.contains("ERROR") || body.contains("INVALID"), body);
    }

    @Test
    public void patchRequiresAuthentication() throws Exception {
        String body = patch("partner/PartnerA", false, param("email", "nope@example.com"));

        assertTrue(body.contains(AuthenticationRequestFilter.ACCESS_DENIED_ERROR_MSG),
                "an unauthenticated PATCH must not be applied: " + body);
        assertFalse(doGet("partner/view/PartnerA").contains("nope@example.com"), "the change must not have been applied");
    }

    @Test
    public void patchImportsACertificateUnderAnAlias() throws Exception {
        // Importing already overwrites the alias in place, so PATCH on a certificate routes to the
        // same import the POST endpoint uses rather than needing an update command of its own
        String encoded = certificateData("partnera");

        String body = patch("cert/patched_partner", true, param("data", encoded));

        assertTrue(body.contains("\"OK\""), body);
        assertTrue(doGet("cert/list").contains("patched_partner"), "the alias must now be present: " + body);
        assertTrue(doGet("cert/view/patched_partner").contains("\"data\""), "the certificate must be readable back");
    }

    @Test
    public void patchingACertificateOverAPrivateKeyAliasIsReportedNotAServerError() throws Exception {
        // The keystore will not replace the certificate of an alias holding a private key, because
        // that would orphan the key. The caller should be told, not handed a failed request.
        String encoded = certificateData("partnera");

        String body = patch("cert/partnerb", true, param("data", encoded));

        assertTrue(body.contains("ERROR"), body);
        assertTrue(body.contains("Could not replace the certificate"), body);
        assertFalse(body.contains("<html"), "the caller must get a result, not a server error page: " + body);
    }

    @Test
    public void patchReplacesTheCertificateAndKeyOfAnAliasHoldingAPrivateKey() throws Exception {
        // Rotating an identity of our own: the alias already holds a private key, so the certificate
        // and the key have to be replaced together
        String before = certificateData("mycompany");
        String replacementCert = certificateData("partnera");
        String p12 = keyStoreHolding("partnera", "rotationpwd");

        String body = patch("cert/mycompany", true, param("data", p12), param("password", "rotationpwd"));

        assertTrue(body.contains("\"OK\""), body);
        String after = certificateData("mycompany");
        assertNotEquals(before, after, "the certificate should have been replaced");
        assertEquals(replacementCert, after, "the alias should now hold the certificate that was imported");
        assertNotNull(privateKeyFor("mycompany"),
                "the private key must be readable with the keystore password after the rotation");
    }

    @Test
    public void aFailedKeyPairReplacementLeavesTheExistingIdentityUsable() throws Exception {
        // The important property for a master key: a rotation that does not go through must not cost
        // us the key we already had
        String certBefore = certificateData("partnerb");
        PrivateKey keyBefore = privateKeyFor("partnerb");
        String p12 = keyStoreHolding("partnera", "rightpwd");

        String body = patch("cert/partnerb", true, param("data", p12), param("password", "wrongpwd"));

        assertTrue(body.contains("ERROR"), body);
        assertEquals(certBefore, certificateData("partnerb"), "the certificate must be unchanged");
        assertEquals(keyBefore, privateKeyFor("partnerb"), "the original private key must still be there and usable");
    }

    @Test
    public void aKeyPairReplacementWithNoPayloadIsReported() throws Exception {
        String body = patch("cert/mycompany", true, param("password", "rotationpwd"));

        assertTrue(body.contains("ERROR"), body);
        assertTrue(body.contains("data"), body);
    }

    /** Builds a base64 PKCS12 holding the key pair of one of the test aliases under a new password. */
    private String keyStoreHolding(String sourceAlias, String newPassword) throws Exception {
        KeyStore src = KeyStore.getInstance("PKCS12");
        try (FileInputStream in = new FileInputStream(new File(configDir, "as2_certs.p12"))) {
            src.load(in, "testas2".toCharArray());
        }
        Key key = src.getKey(sourceAlias, "testas2".toCharArray());
        assertNotNull(key, "the fixture alias should hold a private key: " + sourceAlias);
        Certificate[] chain = src.getCertificateChain(sourceAlias);

        KeyStore out = KeyStore.getInstance("PKCS12");
        out.load(null, null);
        out.setKeyEntry(sourceAlias, key, newPassword.toCharArray(), chain);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        out.store(bos, newPassword.toCharArray());
        return Base64.getEncoder().encodeToString(bos.toByteArray());
    }

    /** Reads the key back through the server's own factory, which uses the keystore password. */
    private PrivateKey privateKeyFor(String alias) throws Exception {
        X509CertificateFactory certFx = (X509CertificateFactory) serverInstance.getSession()
                .getCertificateFactory(CertificateFactory.COMPID_AS2_CERTIFICATE_FACTORY);
        return certFx.getPrivateKey(alias);
    }

    /** Pulls the base64 certificate out of the view response so it can be sent straight back. */
    private String certificateData(String alias) throws Exception {
        String view = doGet("cert/view/" + alias);
        Matcher m = Pattern.compile("\"data\"\\s*:\\s*\"([^\"]+)\"").matcher(view);
        assertTrue(m.find(), "could not find the certificate data in: " + view);
        return m.group(1);
    }

    private NameValuePair param(String name, String value) {
        return new BasicNameValuePair(name, value);
    }

    private String patch(String uriSuffix, boolean withAuth, NameValuePair... params) throws IOException {
        HttpPatch request = new HttpPatch(BASE_URL + uriSuffix);
        List<NameValuePair> form = new ArrayList<NameValuePair>(Arrays.asList(params));
        request.setEntity(new UrlEncodedFormEntity(form));
        return execute(request, withAuth);
    }

    private String doGet(String uriSuffix) throws IOException {
        return execute(new HttpGet(BASE_URL + uriSuffix), true);
    }

    private String execute(org.apache.http.client.methods.HttpUriRequest request, boolean withAuth) throws IOException {
        HttpClientBuilder builder = HttpClientBuilder.create();
        if (withAuth) {
            CredentialsProvider provider = new BasicCredentialsProvider();
            provider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(AUTH_USER, AUTH_PWD));
            builder = builder.setDefaultCredentialsProvider(provider);
        }
        try (CloseableHttpClient client = builder.build();
             CloseableHttpResponse response = client.execute(request)) {
            HttpEntity entity = response.getEntity();
            return entity == null ? "" : EntityUtils.toString(entity);
        }
    }

    /** Guards against the fixture silently not starting, which would make every test vacuous. */
    @Test
    public void fixtureIsUsable() throws Exception {
        assertEquals(true, serverInstance != null);
        assertTrue(doGet("partner/list").contains("PartnerA"), "the server should be serving the API");
    }
}
