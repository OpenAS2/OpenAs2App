package org.openas2.processor.msgtracking;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.openas2.app.BaseServerSetup;
import org.openas2.message.AS2Message;
import org.openas2.message.Message;
import org.openas2.processor.ProcessorModule;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that DbTrackingModule.persist is not vulnerable to SQL injection through the message ID,
 * which for inbound messages is the partner-controlled Message-ID header. A crafted value must be
 * stored and matched as an opaque literal, not interpreted as SQL.
 */
@TestInstance(Lifecycle.PER_CLASS)
public class DbTrackingModulePersistInjectionTest extends BaseServerSetup {

    private DbTrackingModule db;

    @BeforeAll
    public void setUp() throws Exception {
        super.createFileSystemResources(this.getClass().getName());
        super.setStartActiveModules(true);
        super.setup();

        List<ProcessorModule> mpl = session.getProcessor().getModulesSupportingAction(TrackingModule.DO_TRACK_MSG);
        db = (DbTrackingModule) mpl.get(0);

        try (Connection conn = db.dbHandler.getConnection()) {
            Statement s = conn.createStatement();
            String ddl = new String(Files.readAllBytes(Paths.get("src", "config", "db_ddl.sql")));
            for (String statement : ddl.split(";")) {
                if (!statement.trim().isEmpty()) {
                    s.execute(statement);
                }
            }
            // A pre-existing legitimate record that an injection would try to match/mangle
            s.executeUpdate("INSERT INTO msg_metadata (msg_id, sender_id, receiver_id, state) VALUES "
                    + "('<legit-existing>', 'SENDER', 'RECEIVER', 'ORIGINAL_STATE')");
        }
    }

    private void persist(String msgId, String state) {
        Map<String, String> map = new HashMap<String, String>();
        map.put(DbTrackingModule.FIELDS.MSG_ID, msgId);
        map.put(DbTrackingModule.FIELDS.SENDER_ID, "SENDER");
        map.put(DbTrackingModule.FIELDS.RECEIVER_ID, "RECEIVER");
        map.put(DbTrackingModule.FIELDS.STATE, state);
        Message msg = new AS2Message();
        db.persist(msg, map);
    }

    private String stateOf(String msgId) throws Exception {
        try (Connection conn = db.dbHandler.getConnection();
                java.sql.PreparedStatement ps = conn.prepareStatement("SELECT state FROM msg_metadata WHERE msg_id = ?")) {
            ps.setString(1, msgId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    private long rowCount() throws Exception {
        try (Connection conn = db.dbHandler.getConnection();
                Statement s = conn.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM msg_metadata")) {
            rs.next();
            return rs.getLong(1);
        }
    }

    @Test
    public void injectionInMessageIdIsStoredAsLiteralNotInterpreted() throws Exception {
        long before = rowCount();
        String malicious = "<x' OR '1'='1>";

        persist(malicious, "NEW_STATE");

        // A new row must have been inserted with the exact literal ID (before the fix the OR '1'='1
        // matched the existing row, so persist would UPDATE it instead of inserting a new one).
        assertEquals(before + 1, rowCount(), "malicious message ID should insert a new row, not match existing rows");
        assertEquals("NEW_STATE", stateOf(malicious), "record must be stored under the exact literal message ID");
        assertEquals("ORIGINAL_STATE", stateOf("<legit-existing>"), "the pre-existing record must be untouched");
    }

    @Test
    public void messageIdWithQuoteRoundTrips() throws Exception {
        String withQuote = "<a'b>";
        persist(withQuote, "QUOTED");
        assertEquals("QUOTED", stateOf(withQuote), "a message ID containing a quote must persist and be retrievable");

        // And an update to the same record is matched correctly (exercises the WHERE clause of the UPDATE path)
        persist(withQuote, "QUOTED_UPDATED");
        assertEquals("QUOTED_UPDATED", stateOf(withQuote), "updating a quoted message ID must match the same record");
    }

    @Test
    public void normalInsertAndUpdateStillWork() throws Exception {
        persist("<plain-id>", "S1");
        assertEquals("S1", stateOf("<plain-id>"));
        persist("<plain-id>", "S2");
        assertEquals("S2", stateOf("<plain-id>"), "second persist of the same ID should update in place");
        assertTrue(rowCount() >= 1);
    }
}
