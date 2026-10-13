package org.openas2.processor.receiver;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.openas2.app.BaseServerSetup;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that a directory poller can be stopped without cutting off a file it is part way through
 * sending, and without starting on files it had not reached yet.
 * <p>
 * Stopping used to cancel the poller's timer and return at once, so a send in progress carried on only
 * until the process exited underneath it. Shutting down now stops the poller taking new work and then
 * waits for the work in progress. A poll working through several ready files stops after the one it is
 * on, leaving the rest in the outbox to be sent after the restart rather than holding up shutdown for
 * all of them.
 */
@TestInstance(Lifecycle.PER_CLASS)
public class PollerDrainTest extends BaseServerSetup {

    private final List<BlockingDirectoryPoller> pollers = new ArrayList<BlockingDirectoryPoller>();

    @BeforeAll
    public void setUp() throws Exception {
        super.createFileSystemResources(this.getClass().getName());
        // Only the session's configuration is needed, nothing is started
        super.setup();
    }

    @AfterEach
    public void stopPollers() throws Exception {
        for (BlockingDirectoryPoller poller : pollers) {
            poller.release();
            poller.stop();
        }
        pollers.clear();
    }

    @AfterAll
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void waitsForTheFileBeingSent() throws Exception {
        BlockingDirectoryPoller poller = startWithFiles(false, 1);
        poller.awaitFirstSend();

        poller.stopTakingWork();
        assertFalse(poller.awaitIdle(300), "the send in progress has not finished, so the poller is not idle");

        poller.release();
        assertTrue(poller.awaitIdle(5000), "once the send finishes the poller is idle");
        assertEquals(1, poller.getSendsFinished(), "the send in progress must be allowed to complete");
    }

    @Test
    public void filesNotYetStartedAreLeftForAfterTheRestart() throws Exception {
        BlockingDirectoryPoller poller = startWithFiles(false, 3);
        poller.awaitFirstSend();

        poller.stopTakingWork();
        poller.release();

        assertTrue(poller.awaitIdle(5000));
        assertEquals(1, poller.getSendsStarted(),
                "a stopping poller must not start on the files behind the one it was sending");
    }

    @Test
    public void inParallelModeQueuedFilesAreLeftForAfterTheRestart() throws Exception {
        // One thread, so two of the three files are queued behind the first when the poller stops
        BlockingDirectoryPoller poller = startWithFiles(true, 3);
        poller.awaitFirstSend();

        poller.stopTakingWork();
        assertFalse(poller.awaitIdle(300), "the pool's send in progress has not finished");

        poller.release();
        assertTrue(poller.awaitIdle(5000), "once the pool's send finishes the poller is idle");
        assertEquals(1, poller.getSendsStarted(), "files still queued in the pool must not be started");
        assertEquals(1, poller.getSendsFinished());
    }

    @Test
    public void aStoppedPollerPollsAgainWhenRestarted() throws Exception {
        BlockingDirectoryPoller poller = startWithFiles(false, 0);
        poller.stopTakingWork();
        poller.stop();

        File outbox = new File(poller.getOutboxDir());
        writeFile(outbox, "after-restart.edi");
        poller.release();
        poller.start();

        long deadline = System.currentTimeMillis() + 15000;
        while (poller.getSendsStarted() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertEquals(1, poller.getSendsStarted(), "a restarted poller must not still consider itself stopping");
    }

    private BlockingDirectoryPoller startWithFiles(boolean parallel, int files) throws Exception {
        File outbox = Files.createTempDirectory(configDir.toPath(), "outbox").toFile();
        for (int i = 0; i < files; i++) {
            writeFile(outbox, "invoice-" + i + ".edi");
        }
        BlockingDirectoryPoller poller = BlockingDirectoryPoller.create(session, outbox, parallel);
        pollers.add(poller);
        poller.start();
        return poller;
    }

    private void writeFile(File dir, String name) throws Exception {
        Files.write(new File(dir, name).toPath(), "payload".getBytes(StandardCharsets.UTF_8));
    }
}
