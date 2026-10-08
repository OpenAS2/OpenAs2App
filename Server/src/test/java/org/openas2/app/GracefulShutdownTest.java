package org.openas2.app;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.openas2.Session;
import org.openas2.processor.receiver.BlockingDirectoryPoller;
import org.openas2.processor.receiver.DirectoryPollingModule;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies what shutting the server down does to outbound work, which is what a Kubernetes rolling
 * restart triggers by sending SIGTERM to the JVM.
 * <p>
 * The shutdown hook used to stop only the modules the processor holds. Directory pollers are held by
 * the session instead, so they were never stopped, and nothing waited for a file being sent: the
 * process exited underneath the send, leaving the file stranded in the pending folder whether or not
 * the partner had received it. Shutdown now stops every poller taking new work, waits for the work in
 * progress within one overall time limit, and only then stops everything.
 */
@TestInstance(Lifecycle.PER_CLASS)
public class GracefulShutdownTest extends BaseServerSetup {

    // Comfortably longer than stopping everything else takes, so finishing first proves shutdown waited
    private static final long SEND_DURATION_MILLIS = 4000;

    private final List<BlockingDirectoryPoller> blockingPollers = new ArrayList<BlockingDirectoryPoller>();

    @BeforeEach
    public void startServer() throws Exception {
        super.createFileSystemResources(this.getClass().getName());
        super.setStartActiveModules(true);
        super.setup();
    }

    @AfterEach
    public void stopServer() throws Exception {
        for (BlockingDirectoryPoller poller : blockingPollers) {
            poller.release();
        }
        blockingPollers.clear();
        // Each test starts its own server; the inherited @AfterAll tidies up the rest once at the end
        session.stop();
    }

    @Test
    public void shutdownStopsEveryDirectoryPoller() throws Exception {
        List<DirectoryPollingModule> pollers = new ArrayList<DirectoryPollingModule>();
        for (Map<String, Object> meta : session.getPolledDirectories().values()) {
            pollers.add((DirectoryPollingModule) meta.get("pollerInstance"));
        }
        assertFalse(pollers.isEmpty(), "the shipped configuration should start at least one poller");
        for (DirectoryPollingModule poller : pollers) {
            assertTrue(poller.isRunning());
        }

        new OpenAS2Server(session).shutdown();

        for (DirectoryPollingModule poller : pollers) {
            assertFalse(poller.isRunning(), "shutdown must stop the poller for " + poller.getOutboxDir());
        }
    }

    @Test
    public void shutdownWaitsForAFileBeingSent() throws Exception {
        BlockingDirectoryPoller poller = registerBlockingPoller("sending");
        poller.awaitFirstSend();

        // The send completes a while after shutdown starts, as a real transfer would
        AtomicBoolean finishedBeforeShutdownReturned = new AtomicBoolean();
        Thread transfer = new Thread(() -> {
            sleep(SEND_DURATION_MILLIS);
            poller.release();
        });
        transfer.start();
        new OpenAS2Server(session).shutdown();
        finishedBeforeShutdownReturned.set(poller.getSendsFinished() == 1);
        transfer.join();

        assertTrue(finishedBeforeShutdownReturned.get(),
                "shutdown must not return, letting the process exit, while a file is still being sent");
    }

    @Test
    public void theShutdownCommandAlsoWaitsForAFileBeingSent() throws Exception {
        // The console and remote "shutdown" command stops the session directly, not through the hook
        BlockingDirectoryPoller poller = registerBlockingPoller("command");
        poller.awaitFirstSend();

        Thread transfer = new Thread(() -> {
            sleep(SEND_DURATION_MILLIS);
            poller.release();
        });
        transfer.start();
        session.stop();
        boolean finishedBeforeStopReturned = poller.getSendsFinished() == 1;
        transfer.join();

        assertTrue(finishedBeforeStopReturned, "the shutdown command must wait for a file being sent as well");
    }

    @Test
    public void theWaitIsOneLimitOverallNotOnePerBusyPoller() throws Exception {
        BlockingDirectoryPoller first = registerBlockingPoller("first");
        BlockingDirectoryPoller second = registerBlockingPoller("second");
        first.awaitFirstSend();
        second.awaitFirstSend();

        long started = System.nanoTime();
        boolean finished = session.drainWorkInProgress(1000);
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertFalse(finished, "neither send was allowed to finish, so the wait must time out");
        assertTrue(elapsedMillis < 1800,
                "two busy pollers must share the limit or shutdown overruns the grace period, took " + elapsedMillis + "ms");
        assertEquals(0, first.getSendsFinished() + second.getSendsFinished());
    }

    private BlockingDirectoryPoller registerBlockingPoller(String name) throws Exception {
        File outbox = Files.createTempDirectory(configDir.toPath(), name).toFile();
        Files.write(new File(outbox, name + ".edi").toPath(), "payload".getBytes(StandardCharsets.UTF_8));
        BlockingDirectoryPoller poller = BlockingDirectoryPoller.create(session, outbox, false);
        Map<String, Object> meta = new HashMap<String, Object>();
        meta.put("partnershipName", name);
        meta.put("configSource", Session.PARTNERSHIP_POLLER);
        meta.put("pollerInstance", poller);
        session.getPolledDirectories().put(outbox.getAbsolutePath(), meta);
        blockingPollers.add(poller);
        poller.start();
        return poller;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
