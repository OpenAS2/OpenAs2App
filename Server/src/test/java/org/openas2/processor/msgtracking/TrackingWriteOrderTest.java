package org.openas2.processor.msgtracking;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.openas2.app.BaseServerSetup;
import org.openas2.message.AS2Message;
import org.openas2.message.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that tracking events are written in the order they happen (issue #517).
 * <p>
 * Each event used to be written on a new thread of its own, and the writes are synchronized, so when
 * a write was slow the events behind it queued on the lock and were written in whatever order the
 * threads got it. Under load a message's early "sending started" state could be written after its
 * later "MDN received" state, leaving a delivered message recorded as still being sent. They are now
 * written in order on one thread of the module's own, and shutting down waits for the ones queued.
 */
@TestInstance(Lifecycle.PER_CLASS)
public class TrackingWriteOrderTest extends BaseServerSetup {

    @BeforeAll
    public void setUp() throws Exception {
        super.createFileSystemResources(this.getClass().getName());
        super.setup();
    }

    @AfterAll
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void eventsAreWrittenInTheOrderTheyHappened() throws Exception {
        // The first write is slow, as a database round trip can be, so the rest queue up behind it
        RecordingTracker tracker = new RecordingTracker(200, 0);
        List<String> expected = new ArrayList<String>();
        for (int i = 0; i < 200; i++) {
            expected.add("state-" + i);
            track(tracker, "state-" + i);
        }

        // Counted rather than waited for with awaitIdle, so this checks only the order the writes happened in
        waitForWrites(tracker, expected.size());
        assertEquals(expected, tracker.written, "events written out of order record a stale final state");
    }

    @Test
    public void handlingAnEventDoesNotWaitForItToBeWritten() throws Exception {
        RecordingTracker tracker = new RecordingTracker(0, 300);

        long started = System.nanoTime();
        track(tracker, "sent");
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertTrue(elapsedMillis < 250, "processing a message must not wait on the database, took " + elapsedMillis + "ms");
        assertTrue(tracker.awaitIdle(5000));
        assertEquals(Collections.singletonList("sent"), tracker.written);
    }

    @Test
    public void waitingForIdleWaitsForTheEventsAlreadyHandled() throws Exception {
        RecordingTracker tracker = new RecordingTracker(0, 50);
        for (int i = 0; i < 5; i++) {
            track(tracker, "state-" + i);
        }

        assertTrue(tracker.awaitIdle(5000));
        assertEquals(5, tracker.written.size(), "every event handled before the wait must be written when it returns");
    }

    @Test
    public void waitingForIdleGivesUpAtTheTimeout() throws Exception {
        RecordingTracker tracker = new RecordingTracker(0, 300);
        for (int i = 0; i < 5; i++) {
            track(tracker, "state-" + i);
        }

        assertFalse(tracker.awaitIdle(200), "five 300ms writes cannot finish within 200ms");
        assertTrue(tracker.awaitIdle(5000));
    }

    @Test
    public void anEventAfterTheWriterIsStoppedIsWrittenRatherThanLost() throws Exception {
        RecordingTracker tracker = new RecordingTracker(0, 0);
        tracker.stopWriter();

        track(tracker, "late");

        assertEquals(Collections.singletonList("late"), tracker.written, "it should have been written directly");
    }

    @Test
    public void aRestartedModuleWritesInTheBackgroundAgain() throws Exception {
        RecordingTracker tracker = new RecordingTracker(0, 300);
        tracker.stopWriter();
        tracker.startWriter();

        long started = System.nanoTime();
        track(tracker, "after-restart");
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

        assertTrue(elapsedMillis < 250, "a restarted module should not be left writing on the caller's thread");
        assertTrue(tracker.awaitIdle(5000));
    }

    @Test
    public void shuttingDownWaitsForTheEventsQueued() throws Exception {
        RecordingTracker tracker = new RecordingTracker(0, 100);
        session.getProcessor().getModules().add(tracker);
        try {
            for (int i = 0; i < 5; i++) {
                track(tracker, "state-" + i);
            }

            assertTrue(session.drainWorkInProgress(5000));
            assertEquals(5, tracker.written.size(), "shutting down must not lose the record of what was just processed");
        } finally {
            session.getProcessor().getModules().remove(tracker);
        }
    }

    private void waitForWrites(RecordingTracker tracker, int count) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20000;
        while (tracker.written.size() < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(20);
        }
        assertEquals(count, tracker.written.size(), "every event should have been written");
    }

    private void track(RecordingTracker tracker, String state) throws Exception {
        Map<String, Object> options = new HashMap<String, Object>();
        options.put("STATE", state);
        Message msg = new AS2Message();
        msg.setMessageID("<order-test@openas2>");
        tracker.handle(TrackingModule.DO_TRACK_MSG, msg, options);
    }

    /** Records the state of each event in the order it is written. */
    private static class RecordingTracker extends BaseMsgTrackingModule {
        private final List<String> written = Collections.synchronizedList(new ArrayList<String>());
        private final long firstWriteMillis;
        private final long everyWriteMillis;

        RecordingTracker(long firstWriteMillis, long everyWriteMillis) {
            this.firstWriteMillis = firstWriteMillis;
            this.everyWriteMillis = everyWriteMillis;
        }

        // Synchronized as DbTrackingModule.persist is
        @Override
        protected synchronized void persist(Message msg, Map<String, String> map) {
            try {
                Thread.sleep(written.isEmpty() ? Math.max(firstWriteMillis, everyWriteMillis) : everyWriteMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            written.add(map.get(FIELDS.STATE));
        }

        @Override
        public boolean isRunning() {
            return true;
        }

        @Override
        public void start() {
            startWriter();
        }

        @Override
        public void stop() {
            stopWriter();
        }

        @Override
        public boolean healthcheck(List<String> failures) {
            return true;
        }
    }
}
