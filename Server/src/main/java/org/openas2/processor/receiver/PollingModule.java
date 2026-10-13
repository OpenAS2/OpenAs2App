package org.openas2.processor.receiver;

import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.params.InvalidParameterException;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;


public abstract class PollingModule extends MessageBuilderModule {
    protected final String PARAM_POLLING_INTERVAL = "interval";
    private Timer timer;
    private boolean busy;
    // Held for the whole of a poll so shutdown can wait for a file that is part way through being sent
    private final ReentrantLock pollLock = new ReentrantLock();
    private volatile boolean stopping = false;
    private String outboxDir;

    public String getOutboxDir() {
        return outboxDir;
    }

    public void setOutboxDir(String outboxDir) {
        this.outboxDir = outboxDir;
    }

    public void init(Session session, Map<String, String> options) throws OpenAS2Exception {
        super.init(session, options);
        getParameter(PARAM_POLLING_INTERVAL, true);
    }

    private int getInterval() throws InvalidParameterException {
        return getParameterInt(PARAM_POLLING_INTERVAL, true);
    }

    public abstract void poll();

    public void doStart() throws OpenAS2Exception {
        stopping = false;
        timer = new Timer(getName(), false);
        timer.scheduleAtFixedRate(new PollTask(), 0, getInterval() * 1000);
    }

    public void doStop() throws OpenAS2Exception {
        stopTakingWork();
    }

    @Override
    public void stopTakingWork() {
        stopping = true;
        // Cancelling stops further polls but leaves one that is already running to finish
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    @Override
    public boolean awaitIdle(long timeoutMillis) throws InterruptedException {
        if (!pollLock.tryLock(timeoutMillis, TimeUnit.MILLISECONDS)) {
            return false;
        }
        pollLock.unlock();
        return true;
    }

    /**
     * @return true once the poller has been told to stop, so a poll working through several files can
     *         leave the rest where they are instead of starting on them
     */
    protected boolean isStopping() {
        return stopping;
    }

    private boolean isBusy() {
        return busy;
    }

    private void setBusy(boolean b) {
        busy = b;
    }

    private class PollTask extends TimerTask {
        public void run() {
            pollLock.lock();
            try {
                // A poll that was due as the poller stopped must not start once shutdown is waiting on it
                if (stopping) {
                    return;
                }
                if (!isBusy()) {
                    setBusy(true);
                    poll();
                    setBusy(false);
                } else {
                    System.out.println("Miss tick: " + getOutboxDir());
                }
            } finally {
                pollLock.unlock();
            }
        }
    }

}
