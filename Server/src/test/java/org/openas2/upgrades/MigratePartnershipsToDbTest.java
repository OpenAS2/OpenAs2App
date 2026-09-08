package org.openas2.upgrades;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openas2.OpenAS2Exception;
import org.openas2.TestUtils;
import org.openas2.XMLSession;
import org.openas2.partner.DbPartnershipFactory;
import org.openas2.partner.Partnership;
import org.openas2.util.Properties;

/**
 * Verifies that migrating a partnerships XML file into the database produces a database the
 * DbPartnershipFactory loads into the same partnerships the XMLPartnershipFactory loaded from the
 * file. The round trip against the shipped partnerships.xml is the test that matters most: a
 * migration that silently drops an attribute or an override would break sending for that partner
 * without any error.
 */
public class MigratePartnershipsToDbTest {

    private static final Path SRC_CONFIG_DIR = Paths.get("src", "test", "resources", "config").toAbsolutePath();

    private String connectString;
    private Connection seedConn;
    private File workDir;

    @BeforeEach
    public void setUp() throws Exception {
        workDir = Files.createTempDirectory("migrate-partnerships").toFile();
        connectString = "jdbc:h2:mem:migrate_partnerships_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        seedConn = DriverManager.getConnection(connectString, "sa", "");
        try (Statement s = seedConn.createStatement()) {
            String ddl = new String(Files.readAllBytes(Paths.get("src", "config", "db_ddl.sql")), StandardCharsets.UTF_8);
            for (String statement : ddl.split(";")) {
                if (!statement.trim().isEmpty()) {
                    s.execute(statement);
                }
            }
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (seedConn != null) {
            seedConn.close();
        }
        TestUtils.deleteDirectory(workDir);
    }

    @Test
    public void migratedDatabaseLoadsTheSamePartnershipsAsTheXmlFile() throws Exception {
        // Load the shipped config first: this is the reference result, and it also populates the
        // static properties container that both factories use to resolve $properties.x$ placeholders
        Map<String, Partnership> fromXml = loadPartnershipsFromXmlConfig();

        MigratePartnershipsToDb migrator = new MigratePartnershipsToDb();
        migrator.parse(new File(workDir, "partnerships.xml"));
        MigratePartnershipsToDb.Result result = migrator.write(seedConn, false);

        assertTrue(result.getPartners() > 0, "the shipped file defines partners");
        assertTrue(result.getPartnerships() > 0, "the shipped file defines partnerships");

        Map<String, Partnership> fromDb = loadPartnershipsFromDb();

        assertEquals(fromXml.keySet(), fromDb.keySet(), "the same partnerships must load from the database");
        for (Map.Entry<String, Partnership> entry : fromXml.entrySet()) {
            Partnership xml = entry.getValue();
            Partnership db = fromDb.get(entry.getKey());
            String where = " for partnership " + entry.getKey();
            assertEquals(sorted(xml.getSenderIDs()), sorted(db.getSenderIDs()), "sender IDs differ" + where);
            assertEquals(sorted(xml.getReceiverIDs()), sorted(db.getReceiverIDs()), "receiver IDs differ" + where);
            assertEquals(sorted(xml.getAttributes()), sortedWithoutPollerConfig(db.getAttributes()),
                    "attributes differ" + where);
        }
    }

    @Test
    public void senderAndReceiverOverridesBecomeCategorisedRows() throws Exception {
        File xml = writePartnershipsXml(""
                + "<partner name='CompanyA' as2_id='A_OID' x509_alias='companya'/>"
                + "<partner name='CompanyB' as2_id='B_OID' x509_alias='companyb'/>"
                + "<partnership name='A-to-B'>"
                + "  <sender name='CompanyA' x509_alias='companya_signing'/>"
                + "  <receiver name='CompanyB' as2_id='B_OID_ALT'/>"
                + "  <attribute name='protocol' value='as2'/>"
                + "</partnership>");

        MigratePartnershipsToDb migrator = new MigratePartnershipsToDb();
        migrator.parse(xml);
        MigratePartnershipsToDb.Result result = migrator.write(seedConn, false);

        assertEquals(1, result.getSenderOverrides());
        assertEquals(1, result.getReceiverOverrides());
        assertEquals("companya_signing", attributeValue("A-to-B", DbPartnershipFactory.CATEGORY_SENDER, "x509_alias"));
        assertEquals("B_OID_ALT", attributeValue("A-to-B", DbPartnershipFactory.CATEGORY_RECEIVER, "as2_id"));
        assertEquals("as2", attributeValue("A-to-B", DbPartnershipFactory.CATEGORY_ATTRIBUTE, "protocol"));
        // The name identifies the partner row and must not be duplicated as an override
        assertEquals(null, attributeValue("A-to-B", DbPartnershipFactory.CATEGORY_SENDER, "name"));
    }

    @Test
    public void pollerConfigBecomesPollerConfigRows() throws Exception {
        File xml = writePartnershipsXml(""
                + "<partner name='CompanyA' as2_id='A_OID'/>"
                + "<partner name='CompanyB' as2_id='B_OID'/>"
                + "<partnership name='A-to-B'>"
                + "  <sender name='CompanyA'/><receiver name='CompanyB'/>"
                + "  <pollerConfig enabled='true' interval='10'/>"
                + "</partnership>");

        MigratePartnershipsToDb migrator = new MigratePartnershipsToDb();
        migrator.parse(xml);
        assertEquals(2, migrator.write(seedConn, false).getPollerConfigAttributes());

        assertEquals("true", attributeValue("A-to-B", DbPartnershipFactory.CATEGORY_POLLER_CONFIG, "enabled"));
        assertEquals("10", attributeValue("A-to-B", DbPartnershipFactory.CATEGORY_POLLER_CONFIG, "interval"));
    }

    @Test
    public void placeholdersAreStoredUnresolved() throws Exception {
        // Both factories resolve these when loading, so resolving them here would freeze a value
        // that is meant to follow the configuration
        File xml = writePartnershipsXml(""
                + "<partner name='CompanyA' as2_id='A_OID'/>"
                + "<partner name='CompanyB' as2_id='B_OID'/>"
                + "<partnership name='A-to-B'>"
                + "  <sender name='CompanyA'/><receiver name='CompanyB'/>"
                + "  <attribute name='as2_url' value='http://localhost:$properties.port$/HttpReceiver'/>"
                + "</partnership>");

        MigratePartnershipsToDb migrator = new MigratePartnershipsToDb();
        migrator.parse(xml);
        migrator.write(seedConn, false);

        assertEquals("http://localhost:$properties.port$/HttpReceiver",
                attributeValue("A-to-B", DbPartnershipFactory.CATEGORY_ATTRIBUTE, "as2_url"));
    }

    @Test
    public void partnerNameIsNotDuplicatedAsAnAttributeRow() throws Exception {
        File xml = writePartnershipsXml(""
                + "<partner name='CompanyA' as2_id='A_OID' email='a@example.com'/>"
                + "<partner name='CompanyB' as2_id='B_OID'/>"
                + "<partnership name='A-to-B'><sender name='CompanyA'/><receiver name='CompanyB'/></partnership>");

        MigratePartnershipsToDb migrator = new MigratePartnershipsToDb();
        migrator.parse(xml);
        migrator.write(seedConn, false);

        // CompanyA contributes as2_id and email, CompanyB contributes as2_id: the names live in
        // the partner rows themselves
        assertEquals(3, countRows("partner_attribute"));
        try (PreparedStatement s = seedConn.prepareStatement(
                "SELECT COUNT(*) FROM partner_attribute WHERE ATTRIBUTE_NAME = 'name'");
             ResultSet rs = s.executeQuery()) {
            rs.next();
            assertEquals(0, rs.getLong(1), "the partner name must not be written as an attribute row");
        }
    }

    @Test
    public void abortsWhenTheTablesAlreadyHoldData() throws Exception {
        File xml = minimalXml();

        MigratePartnershipsToDb first = new MigratePartnershipsToDb();
        first.parse(xml);
        first.write(seedConn, false);

        MigratePartnershipsToDb second = new MigratePartnershipsToDb();
        second.parse(xml);
        OpenAS2Exception e = assertThrows(OpenAS2Exception.class, () -> second.write(seedConn, false));
        assertTrue(e.getMessage().contains("already contains"), e.getMessage());
        // The failed run must not have added anything on top of the first
        assertEquals(2, countRows("partner"));
        assertEquals(1, countRows("partnership"));
    }

    @Test
    public void replaceClearsTheExistingRowsFirst() throws Exception {
        MigratePartnershipsToDb first = new MigratePartnershipsToDb();
        first.parse(minimalXml());
        first.write(seedConn, false);

        File different = writePartnershipsXml(""
                + "<partner name='CompanyC' as2_id='C_OID'/>"
                + "<partner name='CompanyD' as2_id='D_OID'/>"
                + "<partnership name='C-to-D'><sender name='CompanyC'/><receiver name='CompanyD'/></partnership>",
                "other-partnerships.xml");
        MigratePartnershipsToDb second = new MigratePartnershipsToDb();
        second.parse(different);
        second.write(seedConn, true);

        assertEquals(2, countRows("partner"));
        assertEquals(1, countRows("partnership"));
        assertNotNull(findPartnershipId("C-to-D"), "the replacement partnership must be present");
        assertEquals(null, findPartnershipId("A-to-B"), "the replaced partnership must be gone");
    }

    @Test
    public void unknownSenderPartnerIsRejectedBeforeAnythingIsWritten() throws Exception {
        File xml = writePartnershipsXml(""
                + "<partner name='CompanyA' as2_id='A_OID'/>"
                + "<partnership name='A-to-Ghost'><sender name='CompanyA'/><receiver name='NoSuchPartner'/></partnership>");

        MigratePartnershipsToDb migrator = new MigratePartnershipsToDb();
        OpenAS2Exception e = assertThrows(OpenAS2Exception.class, () -> migrator.parse(xml));
        assertTrue(e.getMessage().contains("undefined receiver"), e.getMessage());
        assertEquals(0, countRows("partner"), "validation happens before any write");
    }

    @Test
    public void senderWithoutAPartnerNameIsRejectedWithAnActionableMessage() throws Exception {
        // Valid in the XML file (the IDs can be inline) but unrepresentable in the schema, which
        // requires every partnership to point at a partner row
        File xml = writePartnershipsXml(""
                + "<partner name='CompanyB' as2_id='B_OID'/>"
                + "<partnership name='Inline-to-B'><sender as2_id='INLINE_OID'/><receiver name='CompanyB'/></partnership>");

        MigratePartnershipsToDb migrator = new MigratePartnershipsToDb();
        OpenAS2Exception e = assertThrows(OpenAS2Exception.class, () -> migrator.parse(xml));
        assertTrue(e.getMessage().contains("has no sender element naming a partner"), e.getMessage());
        assertTrue(e.getMessage().contains("re-run"), "the message should say what to do: " + e.getMessage());
    }

    @Test
    public void duplicateDefinitionsAreRejected() throws Exception {
        File dupPartner = writePartnershipsXml(""
                + "<partner name='CompanyA' as2_id='A_OID'/>"
                + "<partner name='CompanyA' as2_id='A_OID_2'/>", "dup-partner.xml");
        assertTrue(assertThrows(OpenAS2Exception.class, () -> new MigratePartnershipsToDb().parse(dupPartner))
                .getMessage().contains("Partner is defined more than once"));

        File dupPartnership = writePartnershipsXml(""
                + "<partner name='CompanyA' as2_id='A_OID'/>"
                + "<partner name='CompanyB' as2_id='B_OID'/>"
                + "<partnership name='A-to-B'><sender name='CompanyA'/><receiver name='CompanyB'/></partnership>"
                + "<partnership name='A-to-B'><sender name='CompanyB'/><receiver name='CompanyA'/></partnership>",
                "dup-partnership.xml");
        assertTrue(assertThrows(OpenAS2Exception.class, () -> new MigratePartnershipsToDb().parse(dupPartnership))
                .getMessage().contains("Partnership is defined more than once"));
    }

    @Test
    public void overlongAttributeValueIsRejectedRatherThanTruncated() throws Exception {
        StringBuilder tooLong = new StringBuilder();
        for (int i = 0; i < 4001; i++) {
            tooLong.append('x');
        }
        File xml = writePartnershipsXml(""
                + "<partner name='CompanyA' as2_id='A_OID'/>"
                + "<partner name='CompanyB' as2_id='B_OID'/>"
                + "<partnership name='A-to-B'><sender name='CompanyA'/><receiver name='CompanyB'/>"
                + "  <attribute name='subject' value='" + tooLong + "'/>"
                + "</partnership>");

        OpenAS2Exception e = assertThrows(OpenAS2Exception.class, () -> new MigratePartnershipsToDb().parse(xml));
        assertTrue(e.getMessage().contains("exceeds the 4000 character"), e.getMessage());
    }

    /* ------------------------------------------------------------------ helpers */

    /**
     * Copies the shipped config to a temp dir, loads it with an XMLSession and returns the
     * partnerships the XML factory produced, keyed by name.
     */
    private Map<String, Partnership> loadPartnershipsFromXmlConfig() throws Exception {
        for (String f : SRC_CONFIG_DIR.toFile().list()) {
            Files.copy(SRC_CONFIG_DIR.resolve(f), workDir.toPath().resolve(f), StandardCopyOption.REPLACE_EXISTING);
        }
        System.clearProperty(Properties.OPENAS2_PROPERTIES_FILE_PROP);
        XMLSession session = new XMLSession(workDir.getAbsolutePath() + File.separator + "config.xml");
        try {
            return byName(session.getPartnershipFactory().getPartnerships());
        } finally {
            session.stop();
        }
    }

    /** Loads the migrated database through the real factory, keyed by name. */
    private Map<String, Partnership> loadPartnershipsFromDb() throws Exception {
        DbPartnershipFactory factory = new DbPartnershipFactory();
        Map<String, String> params = new HashMap<String, String>();
        params.put(DbPartnershipFactory.PARAM_USE_EMBEDDED_DB, "true");
        params.put("tcp_server_start", "false");
        params.put(DbPartnershipFactory.PARAM_DB_USER, "sa");
        params.put(DbPartnershipFactory.PARAM_DB_PWD, "");
        params.put(DbPartnershipFactory.PARAM_JDBC_CONNECT_STRING, connectString);
        // A null session means no partnership poller is launched, which is all this test needs
        factory.init(null, params);
        try {
            return byName(factory.getPartnerships());
        } finally {
            factory.destroy();
        }
    }

    private Map<String, Partnership> byName(List<Partnership> list) {
        Map<String, Partnership> map = new LinkedHashMap<String, Partnership>();
        for (Partnership p : list) {
            map.put(p.getName(), p);
        }
        return map;
    }

    private <V> Map<String, V> sorted(Map<String, V> map) {
        return new TreeMap<String, V>(map);
    }

    /**
     * The database factory also copies the poller config into the partnership attributes prefixed
     * with "pollerConfig." so the view command can render it. The XML factory does that at view
     * time instead, so those keys are dropped before comparing the two.
     */
    private Map<String, String> sortedWithoutPollerConfig(Map<String, String> map) {
        Map<String, String> filtered = new TreeMap<String, String>();
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (!entry.getKey().startsWith(Partnership.PCFG_POLLER + ".")) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return filtered;
    }

    private File minimalXml() throws Exception {
        return writePartnershipsXml(""
                + "<partner name='CompanyA' as2_id='A_OID'/>"
                + "<partner name='CompanyB' as2_id='B_OID'/>"
                + "<partnership name='A-to-B'><sender name='CompanyA'/><receiver name='CompanyB'/></partnership>");
    }

    private File writePartnershipsXml(String body) throws Exception {
        return writePartnershipsXml(body, "partnerships.xml");
    }

    private File writePartnershipsXml(String body, String fileName) throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><partnerships>"
                + body.replace('\'', '"') + "</partnerships>";
        File f = new File(workDir, fileName);
        Files.write(f.toPath(), xml.getBytes(StandardCharsets.UTF_8));
        return f;
    }

    private long countRows(String table) throws Exception {
        try (PreparedStatement s = seedConn.prepareStatement("SELECT COUNT(*) FROM " + table);
             ResultSet rs = s.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private Long findPartnershipId(String name) throws Exception {
        try (PreparedStatement s = seedConn.prepareStatement("SELECT ID FROM partnership WHERE NAME = ?")) {
            s.setString(1, name);
            try (ResultSet rs = s.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    private String attributeValue(String partnershipName, String category, String attributeName) throws Exception {
        try (PreparedStatement s = seedConn.prepareStatement(
                "SELECT pa.ATTRIBUTE_VALUE FROM partnership_attribute pa JOIN partnership p ON p.ID = pa.PARTNERSHIP_ID"
                        + " WHERE p.NAME = ? AND pa.CATEGORY = ? AND pa.ATTRIBUTE_NAME = ?")) {
            s.setString(1, partnershipName);
            s.setString(2, category);
            s.setString(3, attributeName);
            try (ResultSet rs = s.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }
}
