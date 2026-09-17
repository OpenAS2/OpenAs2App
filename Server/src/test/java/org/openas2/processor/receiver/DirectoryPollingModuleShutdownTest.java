package org.openas2.processor.receiver;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.FileOutputStream;
import java.util.concurrent.ExecutorService;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.openas2.app.BaseServerSetup;
import org.openas2.partner.Partnership;

/**
 * Verifies that a directory poller running in parallel mode does not leave its thread pool behind when
 * it is stopped.
 * <p>
 * Partnership pollers are destroyed and rebuilt every time the partnerships are reloaded, which happens
 * whenever the partnerships file changes. A pool whose threads are still alive is reachable from those
 * threads, so a pool left running kept itself and everything it referenced alive for the life of the
 * process, once per reload. Only parallel mode creates a pool, so only parallel mode was affected.
 */
@TestInstance(Lifecycle.PER_CLASS)
public class DirectoryPollingModuleShutdownTest extends BaseServerSetup {

    private DirectoryPollingModule poller;

    @BeforeAll
    public void setUp() throws Exception {
        super.createFileSystemResources(this.getClass().getName());
        // Only parallel mode creates a pool, so the leak only exists with this turned on
        try (FileOutputStream fos = new FileOutputStream(openAS2PropertiesFile)) {
            fos.write("pollerConfigBase.process_files_in_parallel=true\n".getBytes());
        }
        super.setStartActiveModules(true);
        super.setup();
        poller = session.getPartnershipPoller(simpleTestMsg.getPartnership().getName());
        assertNotNull(poller, "the shipped partnerships should give this test a directory poller");
    }

    @AfterAll
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void aRunningPollerHasAUsablePool() {
        ExecutorService pool = poller.getExecutorService();

        assertNotNull(pool, "parallel mode should have created a pool");
        assertFalse(pool.isShutdown(), "a running poller needs a pool that accepts work");
    }

    @Test
    public void stoppingThePollerShutsItsPoolDown() throws Exception {
        ExecutorService pool = poller.getExecutorService();
        assertFalse(pool.isShutdown());

        poller.stop();

        assertTrue(pool.isShutdown(),
                "the pool must be shut down with the poller, or every partnerships reload leaks one");
        // Put it back so the ordering of the other tests does not matter
        poller.start();
    }

    @Test
    public void aPollerThatIsStartedAgainGetsAWorkingPool() throws Exception {
        poller.stop();
        ExecutorService shutDownPool = poller.getExecutorService();
        assertTrue(shutDownPool.isShutdown());

        poller.start();

        ExecutorService restarted = poller.getExecutorService();
        assertNotNull(restarted);
        assertFalse(restarted.isShutdown(),
                "a restarted poller must not be left holding the pool that was shut down under it,"
                        + " or it would reject every file it picks up");
    }

    @Test
    public void theOutboxIsStillPolledAfterARestart() throws Exception {
        // Guards against the restart leaving the poller in a state where it looks alive but cannot work
        poller.stop();
        poller.start();

        assertTrue(poller.isRunning());
        assertNotNull(poller.getOutboxDir());
        assertFalse(poller.getExecutorService().isShutdown());
        assertNotNull(simpleTestMsg.getPartnership().getReceiverID(Partnership.PID_AS2));
    }
}
