package org.openas2.partner;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that DbPartnershipFactory loads partners and partnerships from the database
 * tables with the same semantics as XMLPartnershipFactory: partner attributes feed the
 * sender/receiver ID maps, per-partnership sender/receiver rows override them and
 * "attribute" category rows become partnership attributes.
 */
@TestInstance(Lifecycle.PER_CLASS)
public class DbPartnershipFactoryTest {

    private static final String CONNECT_STRING = "jdbc:h2:mem:db_partnership_factory_test;DB_CLOSE_DELAY=-1";

    private DbPartnershipFactory factory;
    // Keeps the in-memory database alive independently of the factory's connection pool
    private Connection seedConn;

    @BeforeAll
    public void setUp() throws Exception {
        seedConn = DriverManager.getConnection(CONNECT_STRING, "sa", "");
        try (Statement s = seedConn.createStatement()) {
            // Apply the same schema used in production so the test exercises the real table shapes
            String ddl = new String(Files.readAllBytes(Paths.get("src", "config", "db_ddl.sql")));
            for (String statement : ddl.split(";")) {
                if (!statement.trim().isEmpty()) {
                    s.execute(statement);
                }
            }
            s.executeUpdate("INSERT INTO partner (ID, NAME) VALUES (1, 'OpenAS2A')");
            s.executeUpdate("INSERT INTO partner (ID, NAME) VALUES (2, 'OpenAS2B')");
            s.executeUpdate(partnerAttr(1, "as2_id", "OpenAS2A_ID"));
            s.executeUpdate(partnerAttr(1, "x509_alias", "openas2a"));
            s.executeUpdate(partnerAttr(1, "email", "partnerA@example.com"));
            s.executeUpdate(partnerAttr(2, "as2_id", "OpenAS2B_ID"));
            s.executeUpdate(partnerAttr(2, "x509_alias", "openas2b"));

            s.executeUpdate("INSERT INTO partnership (ID, NAME, SENDER_PARTNER_ID, RECEIVER_PARTNER_ID) VALUES (1, 'OpenAS2A-to-OpenAS2B', 1, 2)");
            s.executeUpdate(partnershipAttr(1, "attribute", "protocol", "as2"));
            s.executeUpdate(partnershipAttr(1, "attribute", "subject", "From OpenAS2A to OpenAS2B"));
            // Sender override: this partnership signs with a different certificate alias
            s.executeUpdate(partnershipAttr(1, "sender", "x509_alias", "openas2a_special"));

            s.executeUpdate("INSERT INTO partnership (ID, NAME, SENDER_PARTNER_ID, RECEIVER_PARTNER_ID) VALUES (2, 'OpenAS2B-to-OpenAS2A', 2, 1)");
            s.executeUpdate(partnershipAttr(2, "attribute", "protocol", "as2"));
            // Exercise the pollerConfig category parse path (no poller is launched: not enabled)
            s.executeUpdate(partnershipAttr(2, "pollerConfig", "enabled", "false"));
            // Seeding with explicit IDs does not advance the identity sequences; move them
            // past the seeded rows so the write tests can insert without colliding
            s.execute("ALTER TABLE partner ALTER COLUMN ID RESTART WITH 100");
            s.execute("ALTER TABLE partnership ALTER COLUMN ID RESTART WITH 100");
        }

        factory = new DbPartnershipFactory();
        Map<String, String> params = new HashMap<String, String>();
        params.put(DbPartnershipFactory.PARAM_USE_EMBEDDED_DB, "true");
        params.put("tcp_server_start", "false");
        params.put(DbPartnershipFactory.PARAM_DB_USER, "sa");
        params.put(DbPartnershipFactory.PARAM_DB_PWD, "");
        params.put(DbPartnershipFactory.PARAM_JDBC_CONNECT_STRING, CONNECT_STRING);
        factory.init(null, params);
    }

    private String partnerAttr(int partnerId, String name, String value) {
        return "INSERT INTO partner_attribute (PARTNER_ID, ATTRIBUTE_NAME, ATTRIBUTE_VALUE) VALUES (" + partnerId + ", '" + name + "', '" + value + "')";
    }

    private String partnershipAttr(int partnershipId, String category, String name, String value) {
        return "INSERT INTO partnership_attribute (PARTNERSHIP_ID, CATEGORY, ATTRIBUTE_NAME, ATTRIBUTE_VALUE) VALUES (" + partnershipId + ", '" + category + "', '" + name + "', '" + value + "')";
    }

    @AfterAll
    public void tearDown() throws Exception {
        seedConn.close();
        factory.destroy();
    }

    @Test
    public void loadsAllPartnersAndPartnerships() {
        assertEquals(2, factory.getPartnerships().size());
        assertEquals(2, factory.getPartners().size());
        @SuppressWarnings("unchecked")
        Map<String, String> partnerA = (Map<String, String>) factory.getPartners().get("OpenAS2A");
        assertNotNull(partnerA);
        assertEquals("OpenAS2A_ID", partnerA.get("as2_id"));
    }

    @Test
    public void findsPartnershipByName() throws Exception {
        Partnership search = new Partnership();
        search.setName("OpenAS2A-to-OpenAS2B");
        Partnership ps = factory.getPartnership(search, false);

        assertEquals("OpenAS2A_ID", ps.getSenderID("as2_id"));
        assertEquals("OpenAS2B_ID", ps.getReceiverID("as2_id"));
    }

    @Test
    public void findsPartnershipBySenderAndReceiverIds() {
        Map<String, Object> senderIDs = new HashMap<String, Object>();
        senderIDs.put("as2_id", "OpenAS2B_ID");
        Map<String, Object> receiverIDs = new HashMap<String, Object>();
        receiverIDs.put("as2_id", "OpenAS2A_ID");

        Partnership ps = factory.getPartnership(senderIDs, receiverIDs);

        assertNotNull(ps);
        assertEquals("OpenAS2B-to-OpenAS2A", ps.getName());
    }

    @Test
    public void partnerAttributesFlowIntoPartnershipAndOverridesApply() throws Exception {
        Partnership search = new Partnership();
        search.setName("OpenAS2A-to-OpenAS2B");
        Partnership ps = factory.getPartnership(search, false);

        // Partner-level attribute inherited by the partnership
        assertEquals("partnerA@example.com", ps.getSenderID("email"));
        // Sender-category attribute overrides the partner-level value for this partnership only
        assertEquals("openas2a_special", ps.getSenderID("x509_alias"));
        // The other partnership using the same partner keeps the partner-level value
        Partnership reverseSearch = new Partnership();
        reverseSearch.setName("OpenAS2B-to-OpenAS2A");
        Partnership reverse = factory.getPartnership(reverseSearch, false);
        assertEquals("openas2a", reverse.getReceiverID("x509_alias"));
    }

    @Test
    public void pollerConfigRowsSurfaceAsPrefixedAttributes() throws Exception {
        Partnership search = new Partnership();
        search.setName("OpenAS2B-to-OpenAS2A");
        Partnership ps = factory.getPartnership(search, false);

        assertEquals("false", ps.getAttribute("pollerConfig.enabled"));
    }

    @Test
    public void loadsPartnershipAttributes() throws Exception {
        Partnership search = new Partnership();
        search.setName("OpenAS2A-to-OpenAS2B");
        Partnership ps = factory.getPartnership(search, false);

        assertEquals("as2", ps.getAttribute("protocol"));
        assertEquals("From OpenAS2A to OpenAS2B", ps.getAttribute("subject"));
    }

    @Test
    public void unknownPartnershipThrows() {
        Partnership search = new Partnership();
        search.setName("no-such-partnership");
        assertThrows(PartnershipNotFoundException.class, () -> factory.getPartnership(search, false));
    }

    @Test
    public void writeMethodsPersistAndReloadState() throws Exception {
        Map<String, String> partnerAttrs = new HashMap<String, String>();
        partnerAttrs.put("name", "OpenAS2C");
        partnerAttrs.put("as2_id", "OpenAS2C_ID");
        factory.addPartner(partnerAttrs);
        assertNotNull(factory.getPartners().get("OpenAS2C"));

        // Duplicate partner names are rejected
        assertThrows(org.openas2.OpenAS2Exception.class, () -> factory.addPartner(partnerAttrs));

        Map<String, String> psAttrs = new HashMap<String, String>();
        psAttrs.put("protocol", "as2");
        factory.addPartnership("OpenAS2C-to-OpenAS2A", "OpenAS2C", "OpenAS2A", psAttrs);
        Partnership search = new Partnership();
        search.setName("OpenAS2C-to-OpenAS2A");
        Partnership ps = factory.getPartnership(search, false);
        assertEquals("OpenAS2C_ID", ps.getSenderID("as2_id"));
        assertEquals("OpenAS2A_ID", ps.getReceiverID("as2_id"));
        assertEquals("as2", ps.getAttribute("protocol"));

        // A partner referenced by a partnership cannot be deleted
        assertThrows(org.openas2.OpenAS2Exception.class, () -> factory.deletePartner("OpenAS2C"));

        // Partnerships referencing unknown partners are rejected
        assertThrows(org.openas2.OpenAS2Exception.class, () -> factory.addPartnership("bad", "no-such-partner", "OpenAS2A", new HashMap<String, String>()));

        factory.deletePartnership("OpenAS2C-to-OpenAS2A");
        assertThrows(PartnershipNotFoundException.class, () -> factory.getPartnership(search, false));
        factory.deletePartner("OpenAS2C");
        assertNull(factory.getPartners().get("OpenAS2C"));

        // Deleting things that no longer exist is an error
        assertThrows(org.openas2.OpenAS2Exception.class, () -> factory.deletePartner("OpenAS2C"));
        assertThrows(org.openas2.OpenAS2Exception.class, () -> factory.deletePartnership("OpenAS2C-to-OpenAS2A"));
    }

    @Test
    public void refreshPicksUpDatabaseChanges() throws Exception {
        try (Statement s = seedConn.createStatement()) {
            s.executeUpdate(partnershipAttr(2, "attribute", "subject", "Added after startup"));
        }
        try {
            Partnership search = new Partnership();
            search.setName("OpenAS2B-to-OpenAS2A");
            assertNull(factory.getPartnership(search, false).getAttribute("subject"));

            factory.refresh();

            assertEquals("Added after startup", factory.getPartnership(search, false).getAttribute("subject"));
        } finally {
            // Leave the table as the other tests expect it
            try (Statement s = seedConn.createStatement()) {
                s.executeUpdate("DELETE FROM partnership_attribute WHERE PARTNERSHIP_ID = 2 AND ATTRIBUTE_NAME = 'subject'");
            }
            factory.refresh();
        }
    }
}
