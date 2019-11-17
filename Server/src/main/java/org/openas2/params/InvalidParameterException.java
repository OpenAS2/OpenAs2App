package org.openas2.params;

import org.openas2.OpenAS2Exception;


public class InvalidParameterException extends OpenAS2Exception {
    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private Object target;
    private String key;
    private String value;

    public InvalidParameterException(String msg, Object target, String key, String value) {
        super(msg + " - " + toString(key, value));
        this.target = target;
        this.key = key;
        this.value = value;
    }

    public InvalidParameterException(String msg) {
        super(msg);
    }

    public void setKey(String string) {
        key = string;
    }

    public String getKey() {
        return key;
    }

    public void setTarget(Object object) {
        target = object;
    }

    public Object getTarget() {
        return target;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public static void checkValue(Object target, String valueName, Object value) throws InvalidParameterException {
        if (value == null) {
            throw new InvalidParameterException("Missing value for required parameter: " + valueName, target, valueName, null);
        }
    }

    public static String toString(String key, String value) {
        return "Key/Value pair: " + key + "/" + value;
    }

}
