package org.openas2.processor.resender;

import org.openas2.OpenAS2Exception;
import org.openas2.message.Message;
import org.openas2.processor.BaseActiveModule;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;


public abstract class BaseResenderModule extends BaseActiveModule implements ResenderModule {
    public static final int TICK_INTERVAL = 30 * 1000;
    private Timer timer;
    // Held for the whole of a resend pass so shutdown can wait for a message part way through being resent
    private final ReentrantLock resendLock = new ReentrantLock();
    private volatile boolean stopping = false;

    public abstract void resend();

    // The resender module, though permanently active, does need to be targeted to request handling a resend
    public boolean canHandle(String action, Message msg, Map<String, Object> options) {
        return action.equalsIgnoreCase(getModuleAction());
    }

    public void doStart() throws OpenAS2Exception {
        stopping = false;
        timer = new Timer(getName(), true);
        timer.scheduleAtFixedRate(new PollTask(), 0, TICK_INTERVAL);
    }

    public void doStop() throws OpenAS2Exception {
        stopTakingWork();
    }

    @Override
    public void stopTakingWork() {
        stopping = true;
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    @Override
    public boolean awaitIdle(long timeoutMillis) throws InterruptedException {
        if (!resendLock.tryLock(timeoutMillis, TimeUnit.MILLISECONDS)) {
            return false;
        }
        resendLock.unlock();
        return true;
    }

    /**
     * @return true once the resender has been told to stop, so a pass working through the queue can
     *         leave the rest of it for after the restart
     */
    protected boolean isStopping() {
        return stopping;
    }

    private class PollTask extends TimerTask {
        public void run() {
            resendLock.lock();
            try {
                if (!stopping) {
                    resend();
                }
            } finally {
                resendLock.unlock();
            }
        }
    }
}
