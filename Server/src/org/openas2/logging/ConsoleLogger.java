package org.openas2.logging;

import org.openas2.OpenAS2Exception;
import org.openas2.message.Message;


public class ConsoleLogger extends BaseLogger {
    public void doLog(Level level, String msgText, Message message) {
        if (System.out != null) {
            getFormatter().format(level, msgText, System.out);
        }
    }

    protected String getShowDefaults() {
        return VALUE_SHOW_ALL;
    }

    protected void doLog(OpenAS2Exception exception, boolean terminated) {
        if (System.err != null) {
            getFormatter().format(exception, terminated, System.err);
        }
    }
}
