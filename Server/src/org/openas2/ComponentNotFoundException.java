package org.openas2;

public class ComponentNotFoundException extends OpenAS2Exception {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private String componentName;

    public ComponentNotFoundException(String componentName) {
        super(componentName);
        this.componentName = componentName;
    }

    public String getComponentName() {
        return componentName;
    }

    public void setComponentName(String componentName) {
        this.componentName = componentName;
    }
}