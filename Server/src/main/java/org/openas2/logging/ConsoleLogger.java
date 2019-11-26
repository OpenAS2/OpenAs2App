package org.openas2.logging;

import org.openas2.message.Message;


public class ConsoleLogger extends BaseLogger {
    public void doLog(Level level, String msgText, Message as2Msg) {
        if (System.out != null) {
            getFormatter().format(level, msgText + (as2Msg == null ? "" : as2Msg.getLogMsgID()), System.out);
        }
    }

    protected String getShowDefaults() {
        return VALUE_SHOW_ALL;
    }

    protected void doLog(Throwable t, boolean terminated) {
        if (System.err != null) {
            getFormatter().format(t, terminated, System.err);
        }
    }

}
