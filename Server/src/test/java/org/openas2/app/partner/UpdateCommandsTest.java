package org.openas2.app.partner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openas2.TestUtils;
import org.openas2.XMLSession;
import org.openas2.cmd.CommandResult;
import org.openas2.partner.DbPartnershipFactory;
import org.openas2.partner.Partnership;
import org.openas2.partner.PartnershipFactory;
import org.openas2.partner.XMLPartnershipFactory;
import org.openas2.util.Properties;

/**
 * Verifies the partner and partnership update commands against both partnership stores.
 * <p>
 * These exist so a caller does not have to delete an entry to change it: "add" refuses to overwrite,
 * so the only previous route was delete followed by recreate, which loses the definition outright if
 * the recreate fails. The behaviour that matters is therefore that an update changes what was asked
 * for, leaves everything else alone, and refuses rather than inventing an entry that is not there.
 */
public class UpdateCommandsTest {

    private static final Path SRC_CONFIG_DIR = Paths.get("src", "test", "resources", "config").toAbsolutePath();

    private File configDir;
    private XMLSession session;
    private String connectString;
    private Connection seedConn;

    @BeforeEach
    public void setUp() throws Exception {
        configDir = Files.createTempDirectory("update-commands").toFile();
        for (String f : SRC_CONFIG_DIR.toFile().list()) {
            Files.copy(SRC_CONFIG_DIR.resolve(f), configDir.toPath().resolve(f), StandardCopyOption.REPLACE_EXISTING);
        }
        System.clearProperty(Properties.OPENAS2_PROPERTIES_FILE_PROP);
    }

    @AfterEach
    public void tearDown() throws Exception {
        if (session != null) {
            session.stop();
            session = null;
        }
        if (seedConn != null) {
            seedConn.close();
            seedConn = null;
        }
        TestUtils.deleteDirectory(configDir);
    }

    /* ------------------------------------------------------------------ XML store */

    @Test
    public void xmlPartnerUpdateChangesOnlyWhatWasSupplied() throws Exception {
        PartnershipFactory partFx = xmlFactory();
        Map<String, Object> before = partnerAttributes(partFx, "PartnerA");
        assertNotNull(before.get("as2_id"), "fixture partner should have an as2_id");

        CommandResult result = new UpdatePartnerCommand().execute(partFx, new Object[]{"PartnerA", "email=new@example.com"});

        assertEquals(CommandResult.TYPE_OK, result.getType(), result.getResult());
        Map<String, Object> after = partnerAttributes(partFx, "PartnerA");
        assertEquals("new@example.com", after.get("email"));
        assertEquals(before.get("as2_id"), after.get("as2_id"), "an attribute that was not supplied must not change");
        assertEquals(before.get("x509_alias"), after.get("x509_alias"), "an attribute that was not supplied must not change");
    }

    @Test
    public void xmlPartnerUpdatePropagatesToPartnershipsThatInheritFromIt() throws Exception {
        PartnershipFactory partFx = xmlFactory();

        new UpdatePartnerCommand().execute(partFx, new Object[]{"PartnerA", "x509_alias=partnera_rotated"});

        Partnership partnership = partnership(partFx, "MyCompany-to-PartnerA");
        assertEquals("partnera_rotated", partnership.getReceiverIDs().get("x509_alias"),
                "the partnership inherits its receiver IDs from the partner so it must see the change");
    }

    @Test
    public void xmlPartnershipUpdateReplacesAnAttributeInsteadOfAddingASecond() throws Exception {
        PartnershipFactory partFx = xmlFactory();

        CommandResult result = new UpdatePartnershipCommand().execute(partFx,
                new Object[]{"MyCompany-to-PartnerA", "subject=Replaced subject"});

        assertEquals(CommandResult.TYPE_OK, result.getType(), result.getResult());
        assertEquals("Replaced subject", partnership(partFx, "MyCompany-to-PartnerA").getAttribute("subject"));
        assertEquals(1, countAttributeElements("MyCompany-to-PartnerA", "subject"),
                "updating must not leave two attribute elements with the same name behind");
    }

    @Test
    public void xmlPartnershipUpdateAddsAnAttributeThatWasNotThere() throws Exception {
        PartnershipFactory partFx = xmlFactory();

        new UpdatePartnershipCommand().execute(partFx,
                new Object[]{"MyCompany-to-PartnerA", "content_transfer_encoding_receive=base64"});

        assertEquals("base64",
                partnership(partFx, "MyCompany-to-PartnerA").getAttribute("content_transfer_encoding_receive"));
    }

    @Test
    public void xmlPartnershipUpdateMergesPollerConfig() throws Exception {
        PartnershipFactory partFx = xmlFactory();

        new UpdatePartnershipCommand().execute(partFx,
                new Object[]{"MyCompany-to-PartnerA", "pollerConfig.interval=30"});

        assertEquals("30", pollerConfigAttribute("MyCompany-to-PartnerA", "interval"));
        assertEquals("true", pollerConfigAttribute("MyCompany-to-PartnerA", "enabled"),
                "the poller config attribute that was already there must survive");
    }

    @Test
    public void xmlPartnershipCanBeRepointedAtADifferentPartner() throws Exception {
        PartnershipFactory partFx = xmlFactory();

        CommandResult result = new UpdatePartnershipCommand().execute(partFx,
                new Object[]{"MyCompany-to-PartnerA", "receiver.name=PartnerB"});

        assertEquals(CommandResult.TYPE_OK, result.getType(), result.getResult());
        Partnership partnership = partnership(partFx, "MyCompany-to-PartnerA");
        assertEquals("PartnerB", partnership.getReceiverIDs().get(Partnership.PID_NAME));
    }

    @Test
    public void xmlUpdateOfAnUnknownEntryIsRejectedRatherThanCreatingIt() throws Exception {
        PartnershipFactory partFx = xmlFactory();

        assertTrue(errorMessage(new UpdatePartnerCommand(), partFx, "NoSuchPartner", "email=x@y.com")
                .contains("Unknown partner name"));
        assertTrue(errorMessage(new UpdatePartnershipCommand(), partFx, "NoSuchPartnership", "subject=x")
                .contains("Partnership not found"));
    }

    @Test
    public void xmlRepointingAtAnUnknownPartnerIsRejected() throws Exception {
        PartnershipFactory partFx = xmlFactory();

        assertTrue(errorMessage(new UpdatePartnershipCommand(), partFx, "MyCompany-to-PartnerA", "sender.name=Ghost")
                .contains("undefined sender"));
    }

    /* ------------------------------------------------------------------ DB store */

    @Test
    public void dbPartnerUpdateChangesOnlyWhatWasSupplied() throws Exception {
        DbPartnershipFactory partFx = dbFactory();
        try {
            CommandResult result = new UpdatePartnerCommand().execute(partFx,
                    new Object[]{"CompanyA", "email=changed@example.com"});

            assertEquals(CommandResult.TYPE_OK, result.getType(), result.getResult());
            assertEquals("changed@example.com", partnerAttributeRow("CompanyA", "email"));
            assertEquals("A_OID", partnerAttributeRow("CompanyA", "as2_id"), "the untouched attribute must survive");
        } finally {
            partFx.destroy();
        }
    }

    @Test
    public void dbPartnerUpdateAddsAnAttributeThatWasNotThere() throws Exception {
        DbPartnershipFactory partFx = dbFactory();
        try {
            new UpdatePartnerCommand().execute(partFx, new Object[]{"CompanyA", "x509_alias=companya"});

            assertEquals("companya", partnerAttributeRow("CompanyA", "x509_alias"));
        } finally {
            partFx.destroy();
        }
    }

    @Test
    public void dbPartnershipUpdateMergesAttributesAndPollerConfig() throws Exception {
        DbPartnershipFactory partFx = dbFactory();
        try {
            CommandResult result = new UpdatePartnershipCommand().execute(partFx,
                    new Object[]{"A-to-B", "subject=Changed", "pollerConfig.interval=45"});

            assertEquals(CommandResult.TYPE_OK, result.getType(), result.getResult());
            assertEquals("Changed", partnershipAttributeRow("A-to-B", DbPartnershipFactory.CATEGORY_ATTRIBUTE, "subject"));
            assertEquals("as2", partnershipAttributeRow("A-to-B", DbPartnershipFactory.CATEGORY_ATTRIBUTE, "protocol"),
                    "the untouched attribute must survive");
            assertEquals("45", partnershipAttributeRow("A-to-B", DbPartnershipFactory.CATEGORY_POLLER_CONFIG, "interval"));
            assertEquals("true", partnershipAttributeRow("A-to-B", DbPartnershipFactory.CATEGORY_POLLER_CONFIG, "enabled"),
                    "the poller config attribute that was already there must survive");
        } finally {
            partFx.destroy();
        }
    }

    @Test
    public void dbPartnershipCanBeRepointedAtADifferentPartner() throws Exception {
        DbPartnershipFactory partFx = dbFactory();
        try {
            new UpdatePartnershipCommand().execute(partFx, new Object[]{"A-to-B", "receiver.name=CompanyC"});

            assertEquals("CompanyC", partnership(partFx, "A-to-B").getReceiverIDs().get(Partnership.PID_NAME));
        } finally {
            partFx.destroy();
        }
    }

    @Test
    public void dbUpdateOfAnUnknownEntryIsRejectedRatherThanCreatingIt() throws Exception {
        DbPartnershipFactory partFx = dbFactory();
        try {
            assertTrue(errorMessage(new UpdatePartnerCommand(), partFx, "NoSuchPartner", "email=x@y.com")
                    .contains("Unknown partner name"));
            assertTrue(errorMessage(new UpdatePartnershipCommand(), partFx, "NoSuchPartnership", "subject=x")
                    .contains("Partnership not found"));
            assertEquals(0, countRows("partner WHERE NAME = 'NoSuchPartner'"));
        } finally {
            partFx.destroy();
        }
    }

    /* ------------------------------------------------------------------ shared rules */

    @Test
    public void renamingIsRefusedBecauseOtherRecordsReferenceTheName() throws Exception {
        PartnershipFactory partFx = xmlFactory();

        assertTrue(errorMessage(new UpdatePartnerCommand(), partFx, "PartnerA", "name=PartnerRenamed")
                .contains("cannot be renamed"));
        assertTrue(errorMessage(new UpdatePartnershipCommand(), partFx, "MyCompany-to-PartnerA", "name=Renamed")
                .contains("cannot be renamed"));
    }

    @Test
    public void anUpdateWithNothingToChangeIsRejected() throws Exception {
        PartnershipFactory partFx = xmlFactory();

        CommandResult result = new UpdatePartnershipCommand().execute(partFx, new Object[]{"MyCompany-to-PartnerA"});
        assertEquals(CommandResult.TYPE_INVALID_PARAM_COUNT, result.getType());
    }

    @Test
    public void malformedParametersAreRejected() throws Exception {
        PartnershipFactory partFx = xmlFactory();

        assertEquals(CommandResult.TYPE_ERROR,
                new UpdatePartnerCommand().execute(partFx, new Object[]{"PartnerA", "novalue"}).getType());
        assertEquals(CommandResult.TYPE_ERROR,
                new UpdatePartnerCommand().execute(partFx, new Object[]{"PartnerA", "=noname"}).getType());
    }

    /* ------------------------------------------------------------------ helpers */

    private PartnershipFactory xmlFactory() throws Exception {
        session = new XMLSession(configDir.getAbsolutePath() + File.separator + "config.xml");
        return session.getPartnershipFactory();
    }

    private DbPartnershipFactory dbFactory() throws Exception {
        connectString = "jdbc:h2:mem:update_commands_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        seedConn = DriverManager.getConnection(connectString, "sa", "");
        try (Statement s = seedConn.createStatement()) {
            String ddl = new String(Files.readAllBytes(Paths.get("src", "config", "db_ddl.sql")), StandardCharsets.UTF_8);
            for (String statement : ddl.split(";")) {
                if (!statement.trim().isEmpty()) {
                    s.execute(statement);
                }
            }
            s.executeUpdate("INSERT INTO partner (ID, NAME) VALUES (1, 'CompanyA')");
            s.executeUpdate("INSERT INTO partner (ID, NAME) VALUES (2, 'CompanyB')");
            s.executeUpdate("INSERT INTO partner (ID, NAME) VALUES (3, 'CompanyC')");
            s.executeUpdate("INSERT INTO partner_attribute (PARTNER_ID, ATTRIBUTE_NAME, ATTRIBUTE_VALUE) VALUES (1, 'as2_id', 'A_OID')");
            s.executeUpdate("INSERT INTO partner_attribute (PARTNER_ID, ATTRIBUTE_NAME, ATTRIBUTE_VALUE) VALUES (1, 'email', 'a@example.com')");
            s.executeUpdate("INSERT INTO partner_attribute (PARTNER_ID, ATTRIBUTE_NAME, ATTRIBUTE_VALUE) VALUES (2, 'as2_id', 'B_OID')");
            s.executeUpdate("INSERT INTO partner_attribute (PARTNER_ID, ATTRIBUTE_NAME, ATTRIBUTE_VALUE) VALUES (3, 'as2_id', 'C_OID')");
            s.executeUpdate("INSERT INTO partnership (ID, NAME, SENDER_PARTNER_ID, RECEIVER_PARTNER_ID) VALUES (1, 'A-to-B', 1, 2)");
            s.executeUpdate("INSERT INTO partnership_attribute (PARTNERSHIP_ID, CATEGORY, ATTRIBUTE_NAME, ATTRIBUTE_VALUE) VALUES (1, 'attribute', 'protocol', 'as2')");
            s.executeUpdate("INSERT INTO partnership_attribute (PARTNERSHIP_ID, CATEGORY, ATTRIBUTE_NAME, ATTRIBUTE_VALUE) VALUES (1, 'attribute', 'subject', 'Original')");
            s.executeUpdate("INSERT INTO partnership_attribute (PARTNERSHIP_ID, CATEGORY, ATTRIBUTE_NAME, ATTRIBUTE_VALUE) VALUES (1, 'pollerConfig', 'enabled', 'true')");
        }
        DbPartnershipFactory factory = new DbPartnershipFactory();
        Map<String, String> params = new HashMap<String, String>();
        params.put(DbPartnershipFactory.PARAM_USE_EMBEDDED_DB, "true");
        params.put("tcp_server_start", "false");
        params.put(DbPartnershipFactory.PARAM_DB_USER, "sa");
        params.put(DbPartnershipFactory.PARAM_DB_PWD, "");
        params.put(DbPartnershipFactory.PARAM_JDBC_CONNECT_STRING, connectString);
        factory.init(null, params);
        return factory;
    }

    private String errorMessage(AliasedPartnershipsCommand command, PartnershipFactory partFx, String... params) throws Exception {
        Object[] args = new Object[params.length];
        System.arraycopy(params, 0, args, 0, params.length);
        CommandResult result = command.execute(partFx, args);
        assertEquals(CommandResult.TYPE_ERROR, result.getType(), "expected an error but got: " + result.getResult());
        return result.getResult();
    }

    /** The factory's own lookup by name is protected, so find it in the loaded list instead. */
    private Partnership partnership(PartnershipFactory partFx, String name) {
        for (Partnership p : partFx.getPartnerships()) {
            if (name.equals(p.getName())) {
                return p;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> partnerAttributes(PartnershipFactory partFx, String name) {
        return (Map<String, Object>) partFx.getPartners().get(name);
    }

    /** Counts attribute elements with a given name so a duplicate append would be caught. */
    private int countAttributeElements(String partnershipName, String attributeName) throws Exception {
        org.w3c.dom.NodeList partnerships =
                ((XMLPartnershipFactory) session.getPartnershipFactory()).getPartnershipsXml()
                        .getDocumentElement().getChildNodes();
        for (int i = 0; i < partnerships.getLength(); i++) {
            org.w3c.dom.Node node = partnerships.item(i);
            if (!"partnership".equals(node.getNodeName())) {
                continue;
            }
            org.w3c.dom.Node nameAttrib = node.getAttributes().getNamedItem("name");
            if (nameAttrib == null || !partnershipName.equals(nameAttrib.getNodeValue())) {
                continue;
            }
            int count = 0;
            org.w3c.dom.NodeList children = node.getChildNodes();
            for (int j = 0; j < children.getLength(); j++) {
                org.w3c.dom.Node child = children.item(j);
                if ("attribute".equals(child.getNodeName())) {
                    org.w3c.dom.Node childName = child.getAttributes().getNamedItem("name");
                    if (childName != null && attributeName.equals(childName.getNodeValue())) {
                        count++;
                    }
                }
            }
            return count;
        }
        return 0;
    }

    private String pollerConfigAttribute(String partnershipName, String attributeName) throws Exception {
        org.w3c.dom.NodeList partnerships =
                ((XMLPartnershipFactory) session.getPartnershipFactory()).getPartnershipsXml()
                        .getDocumentElement().getChildNodes();
        for (int i = 0; i < partnerships.getLength(); i++) {
            org.w3c.dom.Node node = partnerships.item(i);
            if (!"partnership".equals(node.getNodeName())) {
                continue;
            }
            org.w3c.dom.Node nameAttrib = node.getAttributes().getNamedItem("name");
            if (nameAttrib == null || !partnershipName.equals(nameAttrib.getNodeValue())) {
                continue;
            }
            org.w3c.dom.Node poller = org.openas2.util.XMLUtil.findChildNode(node, Partnership.PCFG_POLLER);
            if (poller == null) {
                return null;
            }
            org.w3c.dom.Node attrib = poller.getAttributes().getNamedItem(attributeName);
            return attrib == null ? null : attrib.getNodeValue();
        }
        return null;
    }

    private String partnerAttributeRow(String partnerName, String attributeName) throws Exception {
        try (PreparedStatement s = seedConn.prepareStatement(
                "SELECT pa.ATTRIBUTE_VALUE FROM partner_attribute pa JOIN partner p ON p.ID = pa.PARTNER_ID"
                        + " WHERE p.NAME = ? AND pa.ATTRIBUTE_NAME = ?")) {
            s.setString(1, partnerName);
            s.setString(2, attributeName);
            try (ResultSet rs = s.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private String partnershipAttributeRow(String partnershipName, String category, String attributeName) throws Exception {
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

    private long countRows(String tableAndWhere) throws Exception {
        try (PreparedStatement s = seedConn.prepareStatement("SELECT COUNT(*) FROM " + tableAndWhere);
             ResultSet rs = s.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
