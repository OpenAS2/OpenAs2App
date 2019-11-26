package org.openas2.cmd;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class CommandResult {
    public static final String TYPE_OK = "OK";
    public static final String TYPE_ERROR = "ERROR";
    public static final String TYPE_WARNING = "WARNING";
    public static final String TYPE_INVALID_PARAM_COUNT = "INVALID PARAMETER COUNT";
    public static final String TYPE_COMMAND_NOT_SUPPORTED = "COMMAND NOT SUPPORTED";
    public static final String TYPE_EXCEPTION = "EXCEPTION";
    private String type;
    private List<Object> results;

    public CommandResult(String type, String msg) {
        super();
        this.type = type;
        getResults().add(msg);
    }

    public CommandResult(String type, Object item) {
        super();
        this.type = type;
        getResults().add(item);
    }

    public CommandResult(String type) {
        super();
        this.type = type;
    }

    public CommandResult(Exception e) {
        super();
        this.type = TYPE_EXCEPTION;
        getResults().add(e);
    }

    public List<Object> getResults() {
        if (results == null) {
            results = new ArrayList<Object>();
        }
        return results;
    }

    public String getResult() {
        Iterator<Object> resultIt = getResults().iterator();
        StringBuffer results = new StringBuffer();
        while (resultIt.hasNext()) {
            results.append(resultIt.next().toString()).append("\r\n");
        }
        return results.toString();
    }

    public void setResults(List<Object> list) {
        results = list;
    }


    public String getType() {
        return type;
    }

    public String toString() {
        StringBuffer buf = new StringBuffer();
        buf.append(getType()).append(":\r\n");
        Iterator<Object> resultIt = getResults().iterator();
        while (resultIt.hasNext()) {
            buf.append(resultIt.next().toString()).append("\r\n");
        }
        return buf.toString();
    }

    public String toXML() {
        StringBuffer buf = new StringBuffer();
        Iterator<Object> resultIt = getResults().iterator();
        while (resultIt.hasNext()) {
            buf.append("<result>");
            buf.append(resultIt.next().toString());
            buf.append("</result>");
        }
        return buf.toString();
    }

    public void setType(String string) {
        type = string;
    }

}
