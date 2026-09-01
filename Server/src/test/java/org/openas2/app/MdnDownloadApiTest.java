package org.openas2.app;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.openas2.cmd.processor.restapi.AuthenticationRequestFilter;
import org.openas2.util.Properties;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the API endpoint that downloads the stored MDN for a message.
 * <p>
 * The endpoint resolves an AS2 message ID against the tracking database and streams back the file
 * that the MDN storage module recorded. As well as the download itself these tests cover the
 * not-found paths that matter operationally: an unknown message ID, a message that has no recorded
 * MDN, and a recorded MDN whose file has since been removed from disk.
 */
@TestInstance(Lifecycle.PER_CLASS)
public class MdnDownloadApiTest extends BaseServerSetup {

    private static final String REST_HOST = "http://127.0.0.1:8085";
    private static final String BASE_URL = REST_HOST + "/api/";
    private static final String AUTH_USER = "admin";
    private static final String AUTH_PWD = "admin";

    private static final String MSG_ID = "<MSG-WITH-MDN@openas2test>";
    private static final String MDN_ID = "<MDN-FOR-MSG@openas2test>";
    private static final String MSG_ID_FILE_GONE = "<MSG-MDN-FILE-GONE@openas2test>";
    private static final String MSG_ID_NO_MDN = "<MSG-WITHOUT-MDN@openas2test>";

    private static final String MDN_CONTENT = "Headers:\nMessage-ID: <MDN-FOR-MSG@openas2test>\n\nText:\nMessage received OK.";

    private static OpenAS2Server serverInstance;
    private static Connection dbConn;
    private static File mdnFile;

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
            // aid for debugging JUnit tests
            System.err.println("ERROR occurred: " + ExceptionUtils.getStackTrace(e));
            throw new Exception(e);
        }

        // The MDN the endpoint should hand back
        mdnFile = new File(configDir, "stored-mdn-001.mdn");
        Files.write(mdnFile.toPath(), MDN_CONTENT.getBytes(StandardCharsets.UTF_8));

        // Seed the tracking table the endpoint reads. The server holds the embedded H2 file open, so
        // this connects to the same database from within the same JVM rather than through the module.
        dbConn = DriverManager.getConnection("jdbc:h2:" + configDir.getAbsolutePath() + "/DB/openas2", "sa", "OpenAS2");
        // The tracking tables are not created automatically, so apply the shipped schema first
        try (Statement s = dbConn.createStatement()) {
            String ddl = new String(Files.readAllBytes(Paths.get("src", "config", "db_ddl.sql")), StandardCharsets.UTF_8);
            for (String statement : ddl.split(";")) {
                if (!statement.trim().isEmpty()) {
                    s.execute(statement);
                }
            }
        }
        insertTrackingRow(MSG_ID, MDN_ID, mdnFile.getAbsolutePath());
        insertTrackingRow(MSG_ID_FILE_GONE, "<MDN-FILE-GONE@openas2test>",
                new File(configDir, "deleted-by-housekeeping.mdn").getAbsolutePath());
        insertTrackingRow(MSG_ID_NO_MDN, null, null);
    }

    @AfterAll
    public void tearDown() throws Exception {
        if (dbConn != null) {
            dbConn.close();
        }
        if (serverInstance != null) {
            serverInstance.shutdown();
        }
        System.clearProperty(Properties.OPENAS2_PROPERTIES_FILE_PROP);
    }

    private void insertTrackingRow(String msgId, String mdnId, String mdnFilePath) throws Exception {
        try (PreparedStatement s = dbConn.prepareStatement(
                "INSERT INTO msg_metadata (MSG_ID, MDN_ID, SENDER_ID, RECEIVER_ID, MDN_FILE_PATH) VALUES (?, ?, ?, ?, ?)")) {
            s.setString(1, msgId);
            s.setString(2, mdnId);
            s.setString(3, "MyCompany_OID");
            s.setString(4, "PartnerA_OID");
            s.setString(5, mdnFilePath);
            s.executeUpdate();
        }
    }

    @Test
    public void downloadsTheMdnByMessageId() throws Exception {
        ApiResponse response = downloadMdn(MSG_ID, true);

        assertEquals(200, response.status);
        assertEquals(MDN_CONTENT, response.body, "the endpoint must stream back the stored MDN unchanged");
        assertEquals("attachment; filename=\"stored-mdn-001.mdn\"", response.header("Content-Disposition"));
        assertEquals("nosniff", response.header("X-Content-Type-Options"));
        assertEquals(1, response.headerCount("Content-Length"),
                "a duplicated Content-Length is rejected by some clients");
        assertEquals(String.valueOf(MDN_CONTENT.length()), response.header("Content-Length"));
    }

    @Test
    public void unknownMessageIdIsNotFound() throws Exception {
        ApiResponse response = downloadMdn("<NO-SUCH-MESSAGE@openas2test>", true);

        assertEquals(404, response.status);
        assertTrue(response.body.contains("No stored MDN found"),
                "the error body should say the message ID matched nothing: " + response.body);
    }

    @Test
    public void anMdnIdIsNotAcceptedAsAMessageId() throws Exception {
        // The endpoint is keyed on the message ID only: the MDN's own ID must not resolve
        assertEquals(404, downloadMdn(MDN_ID, true).status);
    }

    @Test
    public void messageWithNoRecordedMdnIsNotFound() throws Exception {
        ApiResponse response = downloadMdn(MSG_ID_NO_MDN, true);

        assertEquals(404, response.status, "a tracked message that has no MDN recorded is not a download");
    }

    @Test
    public void recordedMdnMissingFromDiskIsNotFound() throws Exception {
        ApiResponse response = downloadMdn(MSG_ID_FILE_GONE, true);

        assertEquals(404, response.status);
        assertTrue(response.body.contains("no longer available on disk"),
                "an archived or cleaned up MDN must be distinguishable from an unknown ID: " + response.body);
    }

    @Test
    public void requiresAuthentication() throws Exception {
        ApiResponse response = downloadMdn(MSG_ID, false);

        assertTrue(response.body.contains(AuthenticationRequestFilter.ACCESS_DENIED_ERROR_MSG),
                "MDN content must not be served without credentials: " + response.body);
        assertNotEquals(MDN_CONTENT, response.body, "the MDN itself must not be in the body");
    }

    private ApiResponse downloadMdn(String msgId, boolean withAuth) throws IOException {
        String url = BASE_URL + "messages/mdn/" + encodePathSegment(msgId);
        HttpClientBuilder builder = HttpClientBuilder.create();
        if (withAuth) {
            CredentialsProvider provider = new BasicCredentialsProvider();
            provider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(AUTH_USER, AUTH_PWD));
            builder = builder.setDefaultCredentialsProvider(provider);
        }
        try (CloseableHttpClient client = builder.build();
             CloseableHttpResponse response = client.execute(new HttpGet(url))) {
            HttpEntity entity = response.getEntity();
            return new ApiResponse(response.getStatusLine().getStatusCode(),
                    entity == null ? "" : EntityUtils.toString(entity),
                    response);
        }
    }

    /** Message IDs contain characters such as angle brackets and "@" that must be escaped in a path. */
    private String encodePathSegment(String value) throws UnsupportedEncodingException {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private static final class ApiResponse {
        private final int status;
        private final String body;
        private final CloseableHttpResponse response;

        private ApiResponse(int status, String body, CloseableHttpResponse response) {
            this.status = status;
            this.body = body;
            this.response = response;
        }

        private String header(String name) {
            Header header = response.getFirstHeader(name);
            return header == null ? null : header.getValue();
        }

        private int headerCount(String name) {
            return response.getHeaders(name).length;
        }
    }

    /** Guards against the fixture silently not being set up, which would make every test vacuous. */
    @Test
    public void fixtureIsUsable() {
        assertNotNull(serverInstance, "the server must be running for these tests to mean anything");
        assertTrue(mdnFile.isFile(), "the stored MDN fixture file must exist");
    }
}
