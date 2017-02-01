package org.openas2.params;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.openas2.OpenAS2Exception;
import org.openas2.WrappedException;

public class ExceptionParameters extends ParameterParser {
    public static final String KEY_NAME = "name";
    public static final String KEY_MESSAGE = "message";
    public static final String KEY_TRACE = "trace";
    public static final String KEY_TERMINATED = "terminated";
    private OpenAS2Exception target;
    private boolean terminated;

    public ExceptionParameters(OpenAS2Exception target, boolean terminated) {
        super();
        this.target = target;
        this.terminated = terminated;
    }

    public void setParameter(String key, String value) throws InvalidParameterException {
        if (key == null) {
            throw new InvalidParameterException("Invalid key", this, key, value);
        }

        if (key.equals(KEY_NAME) || key.equals(KEY_MESSAGE) || key.equals(KEY_TRACE) || key.equals(KEY_TERMINATED)) {
            throw new InvalidParameterException("Parameter is read-only", this, key, value);
        }
        throw new InvalidParameterException("Invalid key", this, key, value);
    }

    public String getParameter(String key) throws InvalidParameterException {
        if (key == null) {
            throw new InvalidParameterException("Invalid key", this, key, null);
        }

        OpenAS2Exception target = getTarget();
        Exception unwrappedTarget;

        if (target instanceof WrappedException) {
            unwrappedTarget = ((WrappedException) target).getSource();

            if (unwrappedTarget == null) {
                unwrappedTarget = target;
            }
        } else {
            unwrappedTarget = target;
        }

        if (key.equals(KEY_NAME)) {
            return unwrappedTarget.getClass().getName();
        } else if (key.equals(KEY_MESSAGE)) {
            return unwrappedTarget.getMessage();
        } else if (key.equals(KEY_TRACE)) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            target.printStackTrace(pw);

            return sw.toString();
        } else if (key.equals(KEY_TERMINATED)) {
            if (isTerminated()) {
                return "terminated";
            }
            return "";
        } else {
            throw new InvalidParameterException("Invalid key", this, key, null);
        }
    }

    public void setTarget(OpenAS2Exception target) {
        this.target = target;
    }

    public OpenAS2Exception getTarget() {
        return target;
    }

    public boolean isTerminated() {
        return terminated;
    }
}