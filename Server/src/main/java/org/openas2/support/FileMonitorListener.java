package org.openas2.support;

import java.io.File;


public interface FileMonitorListener {

    int EVENT_MODIFIED = 1;

    void onFileEvent(File file, int eventID);
}
