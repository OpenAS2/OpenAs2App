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
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Verifies that the MDN file path is written to the tracking record and can be looked up by the
 * message's payload filename via DbTrackingModule.getMdnFilePath (which backs the messages/mdnpath
 * command and its REST endpoint).
 */
@TestInstance(Lifecycle.PER_CLASS)
public class DbTrackingModuleMdnPathTest extends BaseServerSetup {

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
        }
    }

    private void persist(String msgId, String fileName, String sentFileName, String mdnPath) {
        Map<String, String> map = new HashMap<String, String>();
        map.put(DbTrackingModule.FIELDS.MSG_ID, msgId);
        map.put(DbTrackingModule.FIELDS.SENDER_ID, "SENDER");
        map.put(DbTrackingModule.FIELDS.RECEIVER_ID, "RECEIVER");
        if (fileName != null) {
            map.put(DbTrackingModule.FIELDS.FILE_NAME, fileName);
        }
        if (sentFileName != null) {
            map.put(DbTrackingModule.FIELDS.SENT_FILE_NAME, sentFileName);
        }
        if (mdnPath != null) {
            map.put(DbTrackingModule.FIELDS.MDN_FILE_PATH, mdnPath);
        }
        Message msg = new AS2Message();
        db.persist(msg, map);
    }

    @Test
    public void mdnPathIsPersistedAndLookedUpByReceivedFileName() {
        persist("<m1>", "invoice-001.edi", null, "/data/mdn/2026/invoice-001.mdn");
        assertEquals("/data/mdn/2026/invoice-001.mdn", db.getMdnFilePath("invoice-001.edi"));
    }

    @Test
    public void mdnPathIsLookedUpBySentFileName() {
        persist("<m2>", null, "order-777.x12", "/data/mdn/2026/order-777.mdn");
        assertEquals("/data/mdn/2026/order-777.mdn", db.getMdnFilePath("order-777.x12"));
    }

    @Test
    public void unknownFilenameReturnsNull() {
        assertNull(db.getMdnFilePath("does-not-exist.edi"));
    }

    @Test
    public void filenameWithoutStoredMdnPathReturnsNull() {
        persist("<m3>", "no-mdn-yet.edi", null, null);
        assertNull(db.getMdnFilePath("no-mdn-yet.edi"),
                "a tracked message with no recorded MDN path should not be returned");
    }
}
