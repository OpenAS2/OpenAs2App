package org.openas2.app;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyStore;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLHandshakeException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mockito;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.openas2.util.AS2Util;
import org.openas2.util.HTTPUtil;
import org.openas2.util.Properties;
import org.openas2.util.ResponseWrapper;

import jakarta.mail.internet.InternetHeaders;
import jakarta.mail.internet.MimeBodyPart;

import org.openas2.cert.X509CertificateFactory;
import org.openas2.message.Message;
import org.openas2.message.NetAttribute;
import org.openas2.processor.sender.AS2SenderModule;
import org.openas2.processor.sender.HttpSenderModule;

@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class CertificatesTest extends BaseServerSetup {
    private static char[] password = "testas2".toCharArray();
    private X509CertificateFactory certFx = null;
    private String alias = null;
    private static final String receiverPort = "10443";
    private static final String url = "https://localhost:" + receiverPort + "/";
    private File customPropsFile = null;
    private File sslCertsFile = null; // The private key and certificate for the HTTPS
    private String sslTrustCertsFilePath = null; // The public key for the SSL private key
    private X509CertificateFactory trustFx = null; // The trust certificates 
    protected static Message testMsg; // Created just once for all tests to minimise runtime
    private InternetHeaders ih = null;
    Map<String, Object> httpOptions = null;
    InputStream securedDataInputStream = null;
    

    public X509CertificateFactory genSelfSignedCert(
            String alias,
            String keystorePath,
            String keyAlg,
            String hashAlg,
            int keySize,
            String certHostName) throws Exception {
        X509CertificateFactory certFx = new X509CertificateFactory();
        // Create and initialise the KeyStore object
        KeyStore ks = AS2Util.getCryptoHelper().getKeyStore();
        ks.load(null, null);
        // Now make it available to factory
        certFx.setKeyStore(ks);
        certFx.setFilename(keystorePath);
        certFx.setPassword(password);
        String dn = "CN=" + certHostName + ",O=OpenAS2 Foundation,L=London,C=UK";
        int validDays = 365;
        certFx.genSelfSignedCertificate(alias, dn, hashAlg, keyAlg, keySize, validDays);
        certFx.save();
        return certFx;
    }   

    @BeforeAll
    public void setUp() throws Exception {
        super.createFileSystemResources();
        String tmpDirAbsolutePath = tmpDir.getAbsolutePath();
        sslCertsFile = Files.createFile(Paths.get(tmpDirAbsolutePath, "ssl_certs.p12")).toFile();
        String tgtHostName = "test.openas2.org";
        this.alias = tgtHostName;
        // Create the SSL file for the server to use
        // Switch to forward slash to avoid backslash being dropped when creating property string when on Windows
        String sslCertsFilePath = sslCertsFile.getAbsolutePath().replace("\\", "/");
        this.certFx = genSelfSignedCert(alias, sslCertsFilePath, "RSA", "SHA256", 2048, tgtHostName);
        // Create the trust store with the public key so the certificate returned from the server is trusted
        File sslTrustCertsFile = Files.createFile(Paths.get(tmpDirAbsolutePath, "ssl_trust_certs.p12")).toFile();
        // Switch to forward slash to avoid backslash being dropped when creating property string when on Windows
        sslTrustCertsFilePath = sslTrustCertsFile.getAbsolutePath().replace("\\", "/");
        String trustAlias = "trust-" + tgtHostName;
        this.certFx.exportPublicKey(sslTrustCertsFilePath, this.alias, trustAlias, password);
        this.trustFx = new X509CertificateFactory();
        this.trustFx.setFilename(sslTrustCertsFilePath);
        this.trustFx.setPassword(password);
        this.trustFx.setKeyStore(AS2Util.getCryptoHelper().getKeyStore());
        this.trustFx.load();

        customPropsFile = Files.createFile(Paths.get(tmpDirAbsolutePath, "openas2.properties")).toFile();
        System.setProperty(Properties.OPENAS2_PROPERTIES_FILE_PROP, customPropsFile.getAbsolutePath());
        FileOutputStream fos = new FileOutputStream(customPropsFile);
        // Switch to forward slash to avoid backslash being dropped when creating property string when on Windows
        fos.write(("ssl_keystore=" + sslCertsFilePath + "\n").getBytes());
        fos.write(("ssl_keystore_password=" + new String(password) + "\n").getBytes());
        //fos.write(("ssl_trust_keystore=" + sslTrustCertsFile.getAbsolutePath() + "\n").getBytes());
        fos.write(("module.AS2ReceiverModule.http.enabled=false\n").getBytes());
        fos.write(("module.AS2ReceiverModule.https.enabled=true\n").getBytes());
        fos.write(("module.AS2ReceiverModule.https.port=" + receiverPort + "\n").getBytes());
        fos.write(("module.HealthCheckModule.enabled=false\n").getBytes());
        fos.write(("module.HealthCheckModule.protocol=https\n").getBytes());
        fos.write(("module.DbTrackingModule.enabled=false\n").getBytes());
        fos.close();
        super.setStartActiveModules(true);
        super.setup();
        testMsg = getSimpleTestMsg();
        addSendPayloadStuffToMsg("fake.txt", testMsg);
        AS2SenderModule sm = new AS2SenderModule();
        AS2SenderModule smSpy = Mockito.spy(sm);
        Mockito.doReturn(session).when(smSpy).getSession();
        MimeBodyPart securedData = smSpy.secure(testMsg);
        securedDataInputStream = securedData.getInputStream();
        ih = sm.getHttpHeaders(testMsg, securedData);
        testMsg.setAttribute(NetAttribute.MA_DESTINATION_IP, "localhost");
        testMsg.setAttribute(NetAttribute.MA_DESTINATION_PORT, receiverPort);
        //Map<String, Object> httpOptions = new HashMap<String, Object>();
        httpOptions = smSpy.getHttpOptions();
        httpOptions.put(HttpSenderModule.PARAM_CUSTOM_SSL_TRUST_STORE, trustFx.getKeyStore());
    }

    @AfterAll
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void a1_shouldFailSSLConnect() throws Exception {
        // No access to the trust keystore means it should fail
        Map<String, Object> httpOptions = new HashMap<String, Object>();
        assertThrows(
                SSLHandshakeException.class,
                ()->{
                    HTTPUtil.execRequest(HTTPUtil.Method.POST, url, null, null, null, httpOptions, -1, false);
                }
        );
    }

    @Test
    public void a2_shouldConnect() throws Exception {
        FileOutputStream fos = new FileOutputStream(customPropsFile, true);
        fos.write(("ssl_trust_keystore.enabled=true\n").getBytes());
        fos.write(("ssl_trust_keystore=" + sslTrustCertsFilePath + "\n").getBytes());
        fos.write(("ssl_trust_keystore_password=" + new String(password) + "\n").getBytes());
        fos.close();
        super.refresh();

        ResponseWrapper resp = null;
        try {
            resp = HTTPUtil.execRequest(HTTPUtil.Method.POST, url, ih, null, securedDataInputStream, httpOptions, -1, false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        assertTrue(200 == resp.getStatusCode(), "Check default is mapping off.");
    }
}