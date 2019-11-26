package org.openas2.support;

import org.openas2.OpenAS2Exception;

import java.io.File;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public abstract class FileMonitorAdapter implements FileMonitorListener {


    public static final int MINIMAL_SCHEDULE_INTERVAL = 1;

    /**
     * Schedule a watch for file changes.
     * If schedule interval less then {@link #MINIMAL_SCHEDULE_INTERVAL} no tasks will be scheduled
     *
     * @param executor        a executor
     * @param file            an existing file
     * @param refreshInterval refresh interval
     * @param unit            a time unit
     */
    public void scheduleIfNeed(ScheduledExecutorService executor, File file, int refreshInterval, TimeUnit unit) {
        if (refreshInterval >= MINIMAL_SCHEDULE_INTERVAL) {
            executor.scheduleAtFixedRate(new FileMonitor(file, this), refreshInterval, refreshInterval, unit);
        }

    }

    @Override
    public void onFileEvent(File file, int eventID) {
        switch (eventID) {
            case FileMonitorListener.EVENT_MODIFIED:

                try {
                    onConfigFileChanged();
                } catch (OpenAS2Exception oae) {
                    oae.terminate();
                }
                break;
        }
    }

    /**
     * A template method which is triggered once observing file is changed.
     *
     * @throws OpenAS2Exception - an internally handled error has occurred
     */
    public abstract void onConfigFileChanged() throws OpenAS2Exception;
}
