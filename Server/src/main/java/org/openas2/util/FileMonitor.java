package org.openas2.util;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class FileMonitor {
    public List<FileMonitorListener> listeners;
    private Date lastModified;
    private File file;
    private Timer timer;
    private boolean busy;
    private int interval;

    public FileMonitor(File file, int interval) {
        super();
        this.file = file;
        this.interval = interval;
        start();
    }

    public void setBusy(boolean busy) {
        this.busy = busy;
    }

    public boolean isBusy() {
        return busy;
    }

    public void setFile(File file) {
        this.file = file;
    }

    public File getFile() {
        return file;
    }

    public String getFilename() {
        if (getFile() != null) {
            return getFile().getAbsolutePath();
        }

        return null;
    }

    public void setInterval(int interval) {
        this.interval = interval;
        restart();
    }

    public int getInterval() {
        return interval;
    }

    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public void setListeners(List<FileMonitorListener> listeners) {
        this.listeners = listeners;
    }

    public List<FileMonitorListener> getListeners() {
        if (listeners == null) {
            listeners = new ArrayList<FileMonitorListener>();
        }

        return listeners;
    }

    public void addListener(FileMonitorListener listener) {
        getListeners().add(listener);
    }

    public void restart() {
        stop();
        start();
    }

    public void start() {
        timer = getTimer();
        timer.scheduleAtFixedRate(new TimerTick(), 0, getInterval() * 1000);
    }

    public void stop() {
        if (getTimer() != null) {
            getTimer().cancel();
        }
    }

    protected boolean isModified() {
        Date lastModified = getLastModified();

        if (lastModified != null) {
            Date currentModified = new Date(getFile().lastModified());

            return !currentModified.equals(getLastModified());
        }
        updateModified();
        return false;
    }

    protected Timer getTimer() {
        if (timer == null) {
            timer = new Timer(true);
        }

        return timer;
    }

    protected void updateListeners() {
        if (isModified()) {
            updateModified();
            updateListeners(FileMonitorListener.EVENT_MODIFIED);
        }
    }

    protected void updateListeners(int eventID) {
        List<FileMonitorListener> listeners = getListeners();

        Iterator<FileMonitorListener> iterator = (Iterator<FileMonitorListener>) listeners.iterator();
		for (Iterator<FileMonitorListener> it = iterator; it.hasNext();) {
            (it.next()).handle(this, getFile(), eventID);
        }
    }

    protected void updateModified() {
        setLastModified(new Date(getFile().lastModified()));
    }

    private class TimerTick extends TimerTask {
        public void run() {
            if (!isBusy()) {
                setBusy(true);
                updateListeners();
                setBusy(false);
            } else {
                updateListeners(FileMonitorListener.EVENT_MISSED_TICK);
            }
        }
    }
}