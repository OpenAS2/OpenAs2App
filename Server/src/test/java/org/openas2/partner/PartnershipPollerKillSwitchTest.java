package org.openas2.partner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openas2.TestUtils;
import org.openas2.XMLSession;
import org.openas2.util.Properties;

/**
 * Verifies the "partnershipPollersDisabled" property, which globally suppresses every directory
 * poller declared in a partnership.
 * <p>
 * It exists so a single partnerships file or database can be shared between an instance that sends
 * AS2 messages and an instance that only receives them, rather than maintaining a second copy with
 * every outbound pollerConfig turned off. It must default to enabled so existing configurations are
 * unaffected, it must be settable from the OpenAS2 properties file as well as config.xml, and it
 * must not stop the partnerships themselves from loading because a receiving instance still needs
 * them to resolve inbound messages.
 */
public class PartnershipPollerKillSwitchTest {

    private static final Path SRC_CONFIG_DIR = Paths.get("src", "test", "resources", "config").toAbsolutePath();

    /** The property as it appears in the shipped config.xml. */
    private static final String PROPERTY_IN_CONFIG_XML =
            BasePartnershipFactory.PROP_PARTNERSHIP_POLLERS_DISABLED + "=\"false\"";

    private File configDir;

    @BeforeEach
    public void setUp() throws Exception {
        configDir = Files.createTempDirectory("partnership-poller-kill-switch").toFile();
        // Work against a copy of the real installer config so the shipped defaults are what is tested
        for (String f : SRC_CONFIG_DIR.toFile().list()) {
            Files.copy(SRC_CONFIG_DIR.resolve(f), configDir.toPath().resolve(f), StandardCopyOption.REPLACE_EXISTING);
        }
        resetGlobalState();
    }

    @AfterEach
    public void tearDown() throws Exception {
        // The properties container is a static singleton that is never cleared, so a value left
        // behind here would silently disable polling for every test class that runs afterwards
        resetGlobalState();
        TestUtils.deleteDirectory(configDir);
    }

    private void resetGlobalState() {
        Properties.getProperties().remove(BasePartnershipFactory.PROP_PARTNERSHIP_POLLERS_DISABLED);
        System.clearProperty(Properties.OPENAS2_PROPERTIES_FILE_PROP);
    }

    @Test
    public void pollersStartWhenTheFlagIsFalse() throws Exception {
        Snapshot snapshot = loadSession();

        assertFalse(snapshot.pollingDisabled, "the shipped config ships the flag turned off");
        assertTrue(snapshot.pollerCount > 0,
                "the shipped partnerships declare pollers so they must be registered when the flag is off");
    }

    @Test
    public void pollersStartWhenThePropertyIsNotDefinedAtAll() throws Exception {
        // An upgraded config.xml will not have the new property: it must behave exactly as before
        replaceInConfigXml(PROPERTY_IN_CONFIG_XML, "");
        Snapshot snapshot = loadSession();

        assertFalse(snapshot.pollingDisabled, "an undefined property must default to polling enabled");
        assertTrue(snapshot.pollerCount > 0, "an undefined property must not suppress pollers");
    }

    @Test
    public void pollersAreSuppressedWhenTheFlagIsTrue() throws Exception {
        setFlagInConfigXml("true");
        Snapshot snapshot = loadSession();

        assertTrue(snapshot.pollingDisabled);
        assertEquals(0, snapshot.pollerCount,
                "no partnership declared poller may be registered when the flag is on");
    }

    @Test
    public void theFlagCanBeSetFromTheOpenAS2PropertiesFile() throws Exception {
        // This is how a containerised deployment sets it: config.xml stays shared and untouched
        // while the per-instance properties file decides whether that instance sends
        File propsFile = new File(configDir, "test.openas2.properties");
        Files.write(propsFile.toPath(),
                (BasePartnershipFactory.PROP_PARTNERSHIP_POLLERS_DISABLED + "=true\n").getBytes(StandardCharsets.UTF_8));
        System.setProperty(Properties.OPENAS2_PROPERTIES_FILE_PROP, propsFile.getAbsolutePath());

        Snapshot snapshot = loadSession();

        assertTrue(snapshot.pollingDisabled, "the properties file must override the config.xml value");
        assertEquals(0, snapshot.pollerCount);
    }

    @Test
    public void theFlagIsCaseInsensitive() throws Exception {
        setFlagInConfigXml("TRUE");

        assertTrue(loadSession().pollingDisabled, "\"TRUE\" must be accepted the same as \"true\"");
    }

    @Test
    public void anyValueOtherThanTrueLeavesPollingEnabled() throws Exception {
        setFlagInConfigXml("no");
        Snapshot snapshot = loadSession();

        assertFalse(snapshot.pollingDisabled, "only \"true\" disables polling");
        assertTrue(snapshot.pollerCount > 0, "only \"true\" disables polling");
    }

    @Test
    public void partnershipsStillLoadWhenPollingIsDisabled() throws Exception {
        int partnershipsWithPollingOn = loadSession().partnershipCount;

        setFlagInConfigXml("true");
        Snapshot pollingOff = loadSession();

        assertTrue(partnershipsWithPollingOn > 0, "the shipped config must load some partnerships");
        assertEquals(partnershipsWithPollingOn, pollingOff.partnershipCount,
                "disabling polling must not stop partnerships loading: a receiving only instance still needs them");
    }

    @Test
    public void databaseDeclaredPollersStartWhenTheFlagIsFalse() throws Exception {
        XMLSession session = newSession();
        try {
            // Loading the DB factory replaces the partnership pollers registered from the XML file,
            // so the single partnership seeded into the database is the only poller left registered
            attachDbPartnershipFactory(session, "kill_switch_db_on");

            assertEquals(1, session.getPolledDirectories().size(),
                    "the partnership stored in the database declares a poller so it must be registered");
        } finally {
            session.stop();
        }
    }

    @Test
    public void databaseDeclaredPollersAreSuppressedWhenTheFlagIsTrue() throws Exception {
        setFlagInConfigXml("true");
        XMLSession session = newSession();
        try {
            attachDbPartnershipFactory(session, "kill_switch_db_off");

            assertEquals(0, session.getPolledDirectories().size(),
                    "the flag must suppress pollers declared in the database as well as in the partnerships file");
        } finally {
            session.stop();
        }
    }

    /**
     * Loads a DbPartnershipFactory against the supplied session using an in-memory database holding a
     * single partnership with an enabled pollerConfig. Loading the factory is what triggers the poller
     * to be registered, so the assertion is made on the session's polled directories afterwards.
     */
    private void attachDbPartnershipFactory(XMLSession session, String dbName) throws Exception {
        String connectString = "jdbc:h2:mem:" + dbName + ";DB_CLOSE_DELAY=-1";
        try (Connection seedConn = DriverManager.getConnection(connectString, "sa", "");
             Statement s = seedConn.createStatement()) {
            String ddl = new String(Files.readAllBytes(Paths.get("src", "config", "db_ddl.sql")), StandardCharsets.UTF_8);
            for (String statement : ddl.split(";")) {
                if (!statement.trim().isEmpty()) {
                    s.execute(statement);
                }
            }
            s.executeUpdate("INSERT INTO partner (ID, NAME) VALUES (1, 'DbSender')");
            s.executeUpdate("INSERT INTO partner (ID, NAME) VALUES (2, 'DbReceiver')");
            // AS2 IDs that no partnership in the shipped partnerships.xml uses, so the outbox
            // directory derived from the receiver AS2 ID cannot collide with an existing poller
            s.executeUpdate(partnerAttr(1, "as2_id", "DbSender_OID"));
            s.executeUpdate(partnerAttr(2, "as2_id", "DbReceiver_OID"));
            s.executeUpdate("INSERT INTO partnership (ID, NAME, SENDER_PARTNER_ID, RECEIVER_PARTNER_ID) VALUES (1, 'DbSender-to-DbReceiver', 1, 2)");
            s.executeUpdate("INSERT INTO partnership_attribute (PARTNERSHIP_ID, CATEGORY, ATTRIBUTE_NAME, ATTRIBUTE_VALUE) "
                    + "VALUES (1, 'pollerConfig', 'enabled', 'true')");

            DbPartnershipFactory factory = new DbPartnershipFactory();
            Map<String, String> params = new HashMap<String, String>();
            params.put(DbPartnershipFactory.PARAM_USE_EMBEDDED_DB, "true");
            params.put("tcp_server_start", "false");
            params.put(DbPartnershipFactory.PARAM_DB_USER, "sa");
            params.put(DbPartnershipFactory.PARAM_DB_PWD, "");
            params.put(DbPartnershipFactory.PARAM_JDBC_CONNECT_STRING, connectString);
            try {
                factory.init(session, params);
            } finally {
                factory.destroy();
            }
        }
    }

    private String partnerAttr(int partnerId, String name, String value) {
        return "INSERT INTO partner_attribute (PARTNER_ID, ATTRIBUTE_NAME, ATTRIBUTE_VALUE) VALUES ("
                + partnerId + ", '" + name + "', '" + value + "')";
    }

    private XMLSession newSession() throws Exception {
        return new XMLSession(configDir.getAbsolutePath() + File.separator + "config.xml");
    }

    /**
     * Builds a session from the config directory, captures what the test needs and shuts it down again
     * so that no scheduler threads or pollers leak between tests.
     */
    private Snapshot loadSession() throws Exception {
        XMLSession session = newSession();
        try {
            return new Snapshot(new XMLPartnershipFactory().isPartnershipPollingDisabled(),
                    session.getPolledDirectories().size(),
                    session.getPartnershipFactory().getPartnerships().size());
        } finally {
            session.stop();
        }
    }

    private void setFlagInConfigXml(String value) throws IOException {
        replaceInConfigXml(PROPERTY_IN_CONFIG_XML,
                BasePartnershipFactory.PROP_PARTNERSHIP_POLLERS_DISABLED + "=\"" + value + "\"");
    }

    private void replaceInConfigXml(String find, String replaceWith) throws IOException {
        Path configXml = configDir.toPath().resolve("config.xml");
        String content = new String(Files.readAllBytes(configXml), StandardCharsets.UTF_8);
        assertTrue(content.contains(find),
                "the shipped config.xml no longer contains \"" + find + "\" so this test needs updating");
        Files.write(configXml, content.replace(find, replaceWith).getBytes(StandardCharsets.UTF_8));
    }

    private static final class Snapshot {
        private final boolean pollingDisabled;
        private final int pollerCount;
        private final int partnershipCount;

        private Snapshot(boolean pollingDisabled, int pollerCount, int partnershipCount) {
            this.pollingDisabled = pollingDisabled;
            this.pollerCount = pollerCount;
            this.partnershipCount = partnershipCount;
        }
    }
}
