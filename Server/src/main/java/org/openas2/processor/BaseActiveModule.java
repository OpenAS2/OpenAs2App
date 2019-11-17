package org.openas2.processor;

import org.openas2.OpenAS2Exception;
import org.openas2.message.Message;

import java.util.List;
import java.util.Map;


public abstract class BaseActiveModule extends BaseProcessorModule implements ActiveModule {
    private boolean running;

    public boolean isRunning() {
        return running;
    }

    public abstract void doStart() throws OpenAS2Exception;

    public abstract void doStop() throws OpenAS2Exception;

    public abstract boolean healthcheck(List<String> failures);

    public boolean canHandle(String action, Message msg, Map<Object, Object> options) {
        return false;
    }

    public void forceStop(Exception cause) {
        try {
            throw new ForcedStopException(cause);
        } catch (ForcedStopException fse) {
            fse.terminate();
        }

        try {
            stop();
        } catch (OpenAS2Exception oae) {
            oae.terminate();
        }
    }

    public void handle(String action, Message msg, Map<Object, Object> options) throws OpenAS2Exception {
        throw new UnsupportedException("Active modules don't handle anything by default");
    }

    public void start() throws OpenAS2Exception {
        doStart();
        setRunning(true);
    }

    public void stop() throws OpenAS2Exception {
        doStop();
        setRunning(false);
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append(getClass().getName() + ": " + getParameters());

        return buf.toString();
    }

    private void setRunning(boolean running) {
        this.running = running;
    }
}
