package org.openas2.params;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openas2.WrappedException;

public class ExceptionParameters extends ParameterParser {
    public static final String KEY_NAME = "name";
    public static final String KEY_MESSAGE = "message";
    public static final String KEY_TRACE = "trace";
    public static final String KEY_TERMINATED = "terminated";
    private Throwable target;
    private boolean terminated;

    public ExceptionParameters(Throwable target, boolean terminated) {
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

        Throwable target = getTarget();
        Throwable unwrappedTarget = target;

        if (target instanceof WrappedException) {
            unwrappedTarget = ((WrappedException) target).getSource();

            if (unwrappedTarget == null) {
                unwrappedTarget = target;
            }
        }

        if (key.equals(KEY_NAME)) {
            return unwrappedTarget.getClass().getName();
        } else if (key.equals(KEY_MESSAGE)) {
            return unwrappedTarget.getMessage();
        } else if (key.equals(KEY_TRACE)) {
            return ExceptionUtils.getStackTrace(target);
        } else if (key.equals(KEY_TERMINATED)) {
            if (isTerminated()) {
                return "terminated";
            }
            return "";
        } else {
            throw new InvalidParameterException("Invalid key", this, key, null);
        }
    }

    public void setTarget(Exception target) {
        this.target = target;
    }

    public Throwable getTarget() {
        return target;
    }

    public boolean isTerminated() {
        return terminated;
    }
}
