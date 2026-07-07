package org.openas2.processor.msgtracking;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.openas2.app.BaseServerSetup;
import org.openas2.processor.ProcessorModule;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that DbTrackingModule.listMessages(from, to) only returns rows whose CREATE_DT
 * falls within the requested bound, rather than the whole table (the OOM fix).
 */
@TestInstance(Lifecycle.PER_CLASS)
public class DbTrackingModuleListMessagesTest extends BaseServerSetup {

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
            // Apply the same schema used in production (Server/src/config/db_ddl.sql) so this
            // test exercises listMessages() against the real msg_metadata table shape.
            String ddl = new String(Files.readAllBytes(Paths.get("src", "config", "db_ddl.sql")));
            for (String statement : ddl.split(";")) {
                if (!statement.trim().isEmpty()) {
                    s.execute(statement);
                }
            }
            s.executeUpdate(insertSql("msg-before-range", "2020-01-01 10:00:00"));
            s.executeUpdate(insertSql("msg-in-range-1", "2026-06-15 09:00:00"));
            s.executeUpdate(insertSql("msg-in-range-2", "2026-06-20 23:59:00"));
            s.executeUpdate(insertSql("msg-after-range", "2030-01-01 00:00:00"));
        }
    }

    private String insertSql(String msgId, String createDt) {
        return "INSERT INTO msg_metadata (msg_id, sender_id, receiver_id, state, create_dt) VALUES ('"
                + msgId + "', 'SENDER', 'RECEIVER', 'MSG_STATUS_MSG_SENT', '" + createDt + "')";
    }

    @AfterAll
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void onlyReturnsRowsWithinTheRequestedDateRange() {
        ArrayList<HashMap<String, String>> rows = db.listMessages(
                Timestamp.valueOf("2026-06-01 00:00:00"),
                Timestamp.valueOf("2026-06-30 23:59:59"));

        List<String> ids = new ArrayList<String>();
        for (HashMap<String, String> row : rows) {
            ids.add(row.get("MSG_ID"));
        }

        assertEquals(2, rows.size(), "Only the two in-range rows should be returned, not the whole table");
        assertTrue(ids.contains("msg-in-range-1"));
        assertTrue(ids.contains("msg-in-range-2"));
        assertFalse(ids.contains("msg-before-range"));
        assertFalse(ids.contains("msg-after-range"));
    }

    @Test
    public void returnsNoRowsWhenRangeMatchesNothing() {
        ArrayList<HashMap<String, String>> rows = db.listMessages(
                Timestamp.valueOf("2024-01-01 00:00:00"),
                Timestamp.valueOf("2024-01-31 23:59:59"));

        assertTrue(rows.isEmpty());
    }

    @Test
    public void returnsEveryRowWhenNoBoundsAreGiven() {
        ArrayList<HashMap<String, String>> rows = db.listMessages(null, null);

        assertEquals(4, rows.size(), "With no from/to, every row in the table should come back");
    }

    @Test
    public void aFromWithNoToIsOpenEnded() {
        ArrayList<HashMap<String, String>> rows = db.listMessages(
                Timestamp.valueOf("2026-01-01 00:00:00"), null);

        List<String> ids = new ArrayList<String>();
        for (HashMap<String, String> row : rows) {
            ids.add(row.get("MSG_ID"));
        }

        assertEquals(3, rows.size());
        assertTrue(ids.contains("msg-in-range-1"));
        assertTrue(ids.contains("msg-in-range-2"));
        assertTrue(ids.contains("msg-after-range"));
        assertFalse(ids.contains("msg-before-range"));
    }
}
