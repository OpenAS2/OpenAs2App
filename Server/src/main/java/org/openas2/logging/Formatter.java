package org.openas2.logging;

import java.io.OutputStream;


public interface Formatter {

    String format(Level level, String msg);

    String format(Throwable t, boolean terminated);

    void format(Level level, String msg, OutputStream out);

    void format(Throwable t, boolean terminated, OutputStream out);

    void setDateFormat(String dateFormat);
}
