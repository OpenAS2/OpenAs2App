package org.openas2.processor;

import java.util.Map;

import org.openas2.BaseComponent;
import org.openas2.OpenAS2Exception;
import org.openas2.message.Message;
import org.openas2.params.InvalidParameterException;


public class BaseProcessorModule extends BaseComponent implements ProcessorModule {

    @Override
    public String getModuleAction() {
        try {
            return getParameter(MODULE_ACTION_ATTRIB, false);
        } catch (InvalidParameterException e) {
            // For now return null but will change so that all modules must set the parameter
            return null;
        }
    }

    @Override
    public boolean canHandle(String action, Message msg, Map<String, Object> options) {
        return action.equalsIgnoreCase(getModuleAction());
    }

    @Override
    public void handle(String action, Message msg, Map<String, Object> options) throws OpenAS2Exception {
        throw new OpenAS2Exception("Module must implement the handle() method: " + this.getClass().getName());
        
    }

}
