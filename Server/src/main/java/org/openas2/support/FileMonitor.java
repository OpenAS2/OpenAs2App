package org.openas2.support;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;

/**
 * A watcher for a file. Changes are detected by {@link File#lastModified()}
 */
public class FileMonitor implements Runnable {

    private List<FileMonitorListener> listeners = new LinkedList<FileMonitorListener>();

    @Nonnull
    private Date lastModified;
    @Nonnull
    private File file;

    public FileMonitor(@Nonnull File file, FileMonitorListener listener) {
        if (!file.exists()) {
            throw new IllegalArgumentException("File " + file.getAbsolutePath() + " doesn't exist.");
        }

        if (!file.isFile()) {
            throw new IllegalArgumentException(file.getAbsolutePath() + " isn't a file.");
        }
        this.file = file;
        this.lastModified = getLastModifiedFromFile();
        listeners.add(listener);
    }

    public void addListener(FileMonitorListener listener) {
        listeners.add(listener);
    }

    private boolean isModified() {
        Date currentModified = getLastModifiedFromFile();
        return currentModified.after(lastModified);
    }

    @Nonnull
    private Date getLastModifiedFromFile() {
        return new Date(file.lastModified());
    }

    private void notifyListeners(int eventID) {
        for (FileMonitorListener listener : listeners) {
            listener.onFileEvent(file, eventID);
        }
    }

    @Override
    public void run() {
        if (isModified()) {
            lastModified = getLastModifiedFromFile();
            notifyListeners(FileMonitorListener.EVENT_MODIFIED);
        }
    }
}
