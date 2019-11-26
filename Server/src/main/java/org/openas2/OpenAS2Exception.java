package org.openas2;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.HashMap;
import java.util.Map;


public class OpenAS2Exception extends Exception {
    public static final String SOURCE_MESSAGE = "message";
    public static final String SOURCE_FILE = "file";
    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private Map<String, Object> sources = new HashMap<String, Object>();
    private Log logger = LogFactory.getLog(OpenAS2Exception.class.getSimpleName());

    public OpenAS2Exception() {
        log(false);
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

    public void terminate() {
        log(true);
    }

    protected void log(boolean terminated) {
        logger.error("Error occurred:: " + org.openas2.logging.Log.getExceptionMsg(this) + "\n    Sources: " + this.getSources(), this);
    }
}
