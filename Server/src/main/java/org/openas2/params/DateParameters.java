package org.openas2.params;

import org.openas2.util.DateUtil;

public class DateParameters extends ParameterParser {
    public void setParameter(String key, String value) throws InvalidParameterException {
        throw new InvalidParameterException("Set not supported", this, key, value);
    }

    public String getParameter(String key) throws InvalidParameterException {
        if (key == null) {
            throw new InvalidParameterException("Invalid key", this, key, null);
        }

        return DateUtil.formatDate(key);
    }
}