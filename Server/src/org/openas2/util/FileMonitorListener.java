package org.openas2.util;

import java.io.File;


public interface FileMonitorListener {
    public static int EVENT_UNDEFINED = 0;
    public static int EVENT_MODIFIED = 1;
    public static int EVENT_MISSED_TICK = -1;

    public void handle(FileMonitor monitor, File file, int eventID);
}
