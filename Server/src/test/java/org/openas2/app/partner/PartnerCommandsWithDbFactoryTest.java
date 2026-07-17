package org.openas2.app.partner;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.openas2.cmd.CommandResult;
import org.openas2.partner.DbPartnershipFactory;
import org.openas2.partner.Partnership;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Verifies that the partner/partnership console and REST API commands manage entries
 * in the database when the session uses a DbPartnershipFactory.
 */
@TestInstance(Lifecycle.PER_CLASS)
public class PartnerCommandsWithDbFactoryTest {

    private static final String CONNECT_STRING = "jdbc:h2:mem:db_partnership_commands_test;DB_CLOSE_DELAY=-1";

    private DbPartnershipFactory factory;
    // Keeps the in-memory database alive independently of the factory's connection pool
    private Connection seedConn;

    @BeforeAll
    public void setUp() throws Exception {
        seedConn = DriverManager.getConnection(CONNECT_STRING, "sa", "");
        try (Statement s = seedConn.createStatement()) {
            String ddl = new String(Files.readAllBytes(Paths.get("src", "config", "db_ddl.sql")));
            for (String statement : ddl.split(";")) {
                if (!statement.trim().isEmpty()) {
                    s.execute(statement);
                }
            }
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

    @AfterAll
    public void tearDown() throws Exception {
        seedConn.close();
        factory.destroy();
    }

    private long countRows(String table) throws Exception {
        try (Statement s = seedConn.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    public void commandsManagePartnersAndPartnershipsInTheDatabase() throws Exception {
        CommandResult result = new AddPartnerCommand().execute(factory, new Object[]{"PartnerA", "as2_id=A_ID", "x509_alias=partnera"});
        assertEquals(CommandResult.TYPE_OK, result.getType());
        result = new AddPartnerCommand().execute(factory, new Object[]{"PartnerB", "as2_id=B_ID"});
        assertEquals(CommandResult.TYPE_OK, result.getType());
        assertEquals(2, countRows("partner"));

        result = new AddPartnershipCommand().execute(factory, new Object[]{"A-to-B", "PartnerA", "PartnerB", "protocol=as2", "subject=Test"});
        assertEquals(CommandResult.TYPE_OK, result.getType());
        assertEquals(1, countRows("partnership"));

        Partnership search = new Partnership();
        search.setName("A-to-B");
        Partnership ps = factory.getPartnership(search, false);
        assertNotNull(ps);
        assertEquals("A_ID", ps.getSenderID("as2_id"));
        assertEquals("B_ID", ps.getReceiverID("as2_id"));
        assertEquals("as2", ps.getAttribute("protocol"));

        // pollerConfig.* params are stored under the pollerConfig category
        result = new AddPartnershipCommand().execute(factory, new Object[]{"other", "PartnerA", "PartnerB", "pollerConfig.enabled=false", "pollerConfig.interval=30"});
        assertEquals(CommandResult.TYPE_OK, result.getType());
        try (Statement s = seedConn.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM partnership_attribute WHERE CATEGORY = 'pollerConfig'")) {
            rs.next();
            assertEquals(2, rs.getLong(1));
        }
        result = new DeletePartnershipCommand().execute(factory, new Object[]{"other"});
        assertEquals(CommandResult.TYPE_OK, result.getType());

        // A partner referenced by a partnership cannot be deleted
        result = new DeletePartnerCommand().execute(factory, new Object[]{"PartnerA"});
        assertEquals(CommandResult.TYPE_ERROR, result.getType());

        result = new DeletePartnershipCommand().execute(factory, new Object[]{"A-to-B"});
        assertEquals(CommandResult.TYPE_OK, result.getType());
        assertEquals(0, countRows("partnership"));
        assertEquals(0, countRows("partnership_attribute"));

        result = new DeletePartnerCommand().execute(factory, new Object[]{"PartnerA"});
        assertEquals(CommandResult.TYPE_OK, result.getType());
        assertEquals(1, countRows("partner"));

        result = new DeletePartnershipCommand().execute(factory, new Object[]{"A-to-B"});
        assertEquals(CommandResult.TYPE_ERROR, result.getType());
    }
}
