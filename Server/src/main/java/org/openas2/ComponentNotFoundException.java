package org.openas2;

public class ComponentNotFoundException extends OpenAS2Exception {

    private static final long serialVersionUID = 1L;
    private String componentName;

    ComponentNotFoundException(String componentName) {
        this.componentName = componentName;
    }

    @Override
    public String getMessage() {
        return "Component '" + componentName + "' not found.";
    }
}
