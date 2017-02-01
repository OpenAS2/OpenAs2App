package org.openas2;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
 ;


public class OpenAS2Exception extends Exception {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public static final String SOURCE_MESSAGE = "message";
    public static final String SOURCE_FILE = "file";
    private Map<String,Object> sources;
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
        Map<String,Object> sources = getSources();

        return sources.get(id);
    }

    public void setSources(Map<String,Object> sources) {
        this.sources = sources;
    }

    public Map<String,Object> getSources() {
        if (sources == null) {
            sources = new HashMap<String,Object>();
        }

        return sources;
    }

    public void addSource(String id, Object source) {
        Map<String,Object> sources = getSources();
        sources.put(id, source);
    }

    public void terminate() {
        log(true);
    }

    protected void log(boolean terminated) {
    	logger.error("Error occurred:: " + org.openas2.logging.Log.getExceptionMsg(this) + "\n    Sources: "+ this.getSources(), this);
    }
}
