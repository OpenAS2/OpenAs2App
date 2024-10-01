package org.openas2.app;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openas2.TestResource;
import org.openas2.TestUtils;
import org.openas2.cmd.processor.restapi.AuthenticationRequestFilter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.text.MatchesPattern.matchesPattern;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class RestApiTest {
    private static final TestResource RESOURCE = TestResource.forGroup("SingleServerTest");
    // private static File openAS2AHome;
    private static OpenAS2Server serverInstance;
    private static String TEST_PARTNER_NAME = "partnerX";
    private static String TEST_PARTNERSHIP_NAME = TEST_PARTNER_NAME + "-partnerA";
    @TempDir
    private static Path scratchpad;
    private static CloseableHttpClient httpclient;
    private static String restHostAddr = "http://127.0.0.1:8080";
    private static String baseUrl = restHostAddr + "/api/";
    private static String authUser = "admin";
    private static String authPwd = "admin";

    @BeforeAll
    public static void start_A_Server() throws Exception {
        // Set up some override properties so we can use the standard config in tests
        // to make sure the release package is fully tested
        scratchpad = Files.createTempDirectory("tempResources");
        File customPropsFile = Files.createFile(Paths.get(scratchpad.toString(), "openas2.properties")).toFile();
        System.setProperty("openas2.properties.file", customPropsFile.getAbsolutePath());
        FileOutputStream fos = new FileOutputStream(customPropsFile);
        fos.write("restapi.command.processor.enabled=true\n".getBytes());
        fos.write(("restapi.command.processor.baseuri=" + restHostAddr + "\n").getBytes());
        fos.write(("restapi.command.processor.userid=" + authUser + "\n").getBytes());
        fos.write(("restapi.command.processor.password=" + authPwd + "\n").getBytes());
        fos.close();

        try {
            System.setProperty("org.apache.commons.logging.Log", "org.openas2.logging.Log");
            //System.setProperty("org.openas2.logging.defaultlog", "TRACE");
            //executorService = Executors.newFixedThreadPool(20);

            RestApiTest.serverInstance = new OpenAS2Server.Builder().run(RESOURCE.get("MyCompany", "config", "config.xml").getAbsolutePath());
        } catch (Throwable e) {
            // aid for debugging JUnit tests
            System.err.println("ERROR occurred: " + ExceptionUtils.getStackTrace(e));
            throw new Exception(e);
        }

    }

    @BeforeAll
    public static void start_B_Client() throws Exception {
        RestApiTest.httpclient = HttpClients.createDefault();
    }

    @AfterAll
    public static void tearDown() throws Exception {
        serverInstance.shutdown();
        //    executorService.shutdown();
        System.clearProperty("openas2.properties.file");
        TestUtils.deleteDirectory(scratchpad.toFile());
        httpclient.close();
    }

    protected CredentialsProvider getCredentials() {
        CredentialsProvider provider = new BasicCredentialsProvider();
        provider.setCredentials(
                AuthScope.ANY,
                new UsernamePasswordCredentials(authUser, authPwd)
        );
        return provider;
    }

    protected String doGet(String uriSuffix, boolean withAuth) throws IOException {
        HttpGet request = new HttpGet(baseUrl + uriSuffix);
        HttpClientBuilder httpClientBuilder = HttpClientBuilder.create();
        if (withAuth) {
            httpClientBuilder = httpClientBuilder.setDefaultCredentialsProvider(getCredentials());
        }
        try (CloseableHttpClient httpClient = httpClientBuilder.build();
            CloseableHttpResponse response = httpClient.execute(request)) {
            HttpEntity entity = response.getEntity();
            return entity != null ? EntityUtils.toString(entity) : "";
        }
    }

    public String doPost(String uriSuffix, boolean withAuth, List<NameValuePair> params) throws IOException {
        final HttpPost httpPost = new HttpPost(baseUrl + uriSuffix);
        httpPost.setEntity(new UrlEncodedFormEntity(params));
        HttpClientBuilder httpClientBuilder = HttpClients.custom();
        if (withAuth) {
            httpClientBuilder = httpClientBuilder.setDefaultCredentialsProvider(getCredentials());
        }
        try (CloseableHttpClient httpClient = httpClientBuilder.build();
            CloseableHttpResponse response = httpClient.execute(httpPost)) {

            final int statusCode = response.getStatusLine().getStatusCode();
            assertThat(statusCode, equalTo(HttpStatus.SC_OK));
            HttpEntity entity = response.getEntity();
            return entity != null ? EntityUtils.toString(entity) : "";
        }
    }
    /*
    @Test
    public void shouldRespondWithVersion() throws Exception {
        String buffer = this.doRequest(new HttpGet("http://127.0.0.1:8080/api/"), true);
        assertThat("Getting API version and server info", buffer, containsString(serverInstance.getSession().getAppTitle()));
    }
    **/

    @Test
    public void shouldRespondWith_A_AccessDenied() throws Exception {
        String buffer = this.doGet("partner/list", false);
        assertThat("Getting Partner List without user/pass", buffer, containsString(AuthenticationRequestFilter.ACCESS_DENIED_ERROR_MSG));

        buffer = this.doGet("partnership/list", false);
        assertThat("Getting Partnership List without user/pass", buffer, containsString(AuthenticationRequestFilter.ACCESS_DENIED_ERROR_MSG));

        buffer = this.doGet("cert/list", false);
        assertThat("Getting Cert List without user/pass", buffer, containsString(AuthenticationRequestFilter.ACCESS_DENIED_ERROR_MSG));
    }

    @Test
    public void shouldRespondWith_B_Partners() throws Exception {
        String buffer = this.doGet("partner/list", true);
        assertThat("Getting Partners API ", buffer.replaceAll("[\\n\\r]+",  ":"), matchesPattern(".*\"type\"[ ]*:[ ]*\"OK\".*\"PartnerA\", \"MyCompany\", \"PartnerB\"[\\s\\S\\n]*"));
    }

    @Test
    public void shouldRespondWith_C_Partnerships() throws Exception {
        String buffer = this.doGet("partnership/list", true);
        assertThat("Getting Partnership API ", buffer.replaceAll("[\\n\\r]+",  ":"), matchesPattern(".*\"type\"[ ]*:[ ]*\"OK\".*\"MyCompany-to-PartnerA\".*"));
    }

    @Test
    public void shouldRespondWith_D_Certs() throws Exception {
        String buffer = this.doGet("cert/list", true);
        assertThat("Getting Certs API ", buffer.replaceAll("[\\n\\r]+",  ":"), matchesPattern(".*\"type\"[ ]*:[ ]*\"OK\".*\"partnera\", \"mycompany\".*"));
    }

    @Test
    public void shouldRespondWith_E_AddPartnerStored() throws Exception {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("0", TEST_PARTNER_NAME));
        params.add(new BasicNameValuePair("as2_id", "PX_OID"));
        String buffer = this.doPost("partner/add", true, params);
        assertThat("Add partner via API ", buffer.replaceAll("[\\n\\r]+",  ":"), matchesPattern(".*\"type\"[ ]*:[ ]*\"OK\".*\"Stored partnerships\".*"));
    }

    @Test
    public void shouldRespondWith_F_AddPartnerFailed() throws Exception {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("0", TEST_PARTNER_NAME));
        params.add(new BasicNameValuePair("as2_id", "PX_OID"));
        String buffer = this.doPost("partner/add", true, params);
        assertThat("Add partner fails via API ", buffer.replaceAll("[\\n\\r]+",  ":"), containsString("Partner is defined more than once"));
    }

    @Test
    public void shouldRespondWith_G_AddPartnerShipStored() throws Exception {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("0", TEST_PARTNERSHIP_NAME));
        params.add(new BasicNameValuePair("1", TEST_PARTNER_NAME)); // the sender
        params.add(new BasicNameValuePair("2", "PartnerA")); // the receiver
        params.add(new BasicNameValuePair("as2_url", "http://my.as2host.io:10080"));
        params.add(new BasicNameValuePair("pollerConfig.enabled", "true"));
        String buffer = this.doPost("partnership/add", true, params);
        assertThat("Add partnership via API ", buffer.replaceAll("[\\n\\r]+",  ":"), matchesPattern(".*\"type\"[ ]*:[ ]*\"OK\".*\"Stored partnerships\".*"));
    }

    @Test
    public void shouldRespondWith_H_DeletePartnershipStored() throws Exception {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("0", TEST_PARTNERSHIP_NAME));
        String buffer = this.doPost("partnership/delete", true, params);
        assertThat("Delete partnership via API ", buffer.replaceAll("[\\n\\r]+",  ":"), matchesPattern(".*\"type\"[ ]*:[ ]*\"OK\".*\"Stored partnerships\".*"));
    }

    @Test
    public void shouldRespondWith_I_DeletePartnerStored() throws Exception {
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new BasicNameValuePair("0", TEST_PARTNER_NAME));
        String buffer = this.doPost("partner/delete", true, params);
        assertThat("Delete partnership via API ", buffer.replaceAll("[\\n\\r]+",  ":"), matchesPattern(".*\"type\"[ ]*:[ ]*\"OK\".*\"Stored partnerships\".*"));
    }
}
