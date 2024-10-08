package org.openas2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;


public class OpenAS2Exception extends Exception {
    private static final long serialVersionUID = -2266872772193560354L;
    public static final String SOURCE_MESSAGE = "message";
    public static final String SOURCE_FILE = "file";
    private Map<String, Object> sources = new HashMap<String, Object>();
    private Logger logger = LoggerFactory.getLogger(OpenAS2Exception.class);

    public OpenAS2Exception() {
        super();
    }

    public OpenAS2Exception(String msg) {
        super(msg);
    }

    public OpenAS2Exception(String msg, Throwable cause) {
        super(msg, cause);
    }

    public OpenAS2Exception(Throwable cause) {
        super(cause);
    }

    public Object getSource(String id) {
        return sources.get(id);
    }

    public Map<String, Object> getSources() {
        return sources;
    }

    public void addSource(String id, Object source) {
        sources.put(id, source);
    }

    public void log() {
        logger.error("Error occurred:: " + org.openas2.util.Logging.getExceptionMsg(this) + "\n    Sources: " + this.getSources(), this);
    }
}
