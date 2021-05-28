package org.openas2.logging;

import io.sentry.Sentry;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.message.Message;

import java.util.Map;


public class SentryLogger extends BaseLogger {

    public static final String SENTRY_DSN = "dsn";

    public void init(Session session, Map<String, String> parameters) throws OpenAS2Exception {
        super.init(session, parameters);

        Sentry.init(getParameter(SENTRY_DSN, true));
    }

    protected String getShowDefaults() {
        return VALUE_SHOW_ALL;
    }

    public void doLog(Level level, String msgText, Message as2Msg) {
    }

    protected void doLog(Throwable t, boolean terminated) {
        Sentry.captureException(t);
    }
}
