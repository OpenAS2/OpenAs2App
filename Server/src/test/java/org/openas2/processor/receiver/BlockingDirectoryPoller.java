package org.openas2.processor.receiver;

import org.openas2.Session;
import org.openas2.message.AS2Message;
import org.openas2.message.Message;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A directory poller whose "send" is held until the test releases it, so a test can stop or shut down
 * the server while a file is part way through being sent and see what is waited for.
 * <p>
 * Only the sending is replaced: finding files, deciding when they are ready and the stopping logic are
 * the real poller's.
 */
public class BlockingDirectoryPoller extends DirectoryPollingModule {

    private final AtomicInteger sendsStarted = new AtomicInteger();
    private final AtomicInteger sendsFinished = new AtomicInteger();
    private final CountDownLatch firstSendStarted = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);

    public static BlockingDirectoryPoller create(Session session, File outbox, boolean parallel) throws Exception {
        BlockingDirectoryPoller poller = new BlockingDirectoryPoller();
        Map<String, String> options = new HashMap<String, String>();
        options.put(PARAM_OUTBOX_DIRECTORY, outbox.getAbsolutePath());
        options.put(PARAM_ERROR_DIRECTORY, new File(outbox, "error").getAbsolutePath());
        options.put("interval", "1");
        options.put(PARAM_PROCESS_IN_PARALLEL, String.valueOf(parallel));
        // One thread so the other files have to queue behind the one being sent
        options.put(PARAM_MAX_PARALLEL_FILES, "1");
        poller.init(session, options);
        return poller;
    }

    @Override
    protected void processFile(File file) {
        sendsStarted.incrementAndGet();
        firstSendStarted.countDown();
        try {
            release.await();
            sendsFinished.incrementAndGet();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    protected Message createMessage() {
        return new AS2Message();
    }

    public void awaitFirstSend() throws InterruptedException {
        if (!firstSendStarted.await(15, TimeUnit.SECONDS)) {
            throw new AssertionError("the poller never started sending a file");
        }
    }

    /** Lets every send in progress, and any started later, complete. */
    public void release() {
        release.countDown();
    }

    public int getSendsStarted() {
        return sendsStarted.get();
    }

    public int getSendsFinished() {
        return sendsFinished.get();
    }
}
