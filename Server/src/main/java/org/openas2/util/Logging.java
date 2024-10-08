package org.openas2.util;

public class Logging {
    public static String getExceptionMsg(Throwable e) {
        String msg = null;
        if (e.getCause() != null) {
            msg = e.getCause().getMessage();
        }
        if (msg == null || msg.length() == 0) {
            msg = e.getMessage();
            if (msg == null || msg.length() == 0) {
                msg = e.toString();
            }
        }
        return msg;
    }
}
