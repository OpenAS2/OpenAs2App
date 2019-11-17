package org.openas2.params;

import org.openas2.Component;

public class ComponentParameters extends ParameterParser {
    public static final String KEY_COMPONENT_PARAMETER = "component";
    private Component target;

    public ComponentParameters(Component target) {
        super();
        this.target = target;
    }

    public void setParameter(String key, String value) throws InvalidParameterException {
        throw new InvalidParameterException("Set not supported", this, key, value);
    }

    public String getParameter(String key) throws InvalidParameterException {
        if (key != null) {
            return getTarget().getParameters().get(key);
        } else {
            throw new InvalidParameterException("Invalid area in key", this, key, null);
        }
    }

    public void setTarget(Component component) {
        target = component;
    }

    public Component getTarget() {
        return target;
    }

}
