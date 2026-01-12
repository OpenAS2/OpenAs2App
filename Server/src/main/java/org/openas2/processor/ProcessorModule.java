package org.openas2.processor;

import org.openas2.Component;
import org.openas2.OpenAS2Exception;
import org.openas2.message.Message;

import java.util.Map;


public interface ProcessorModule extends Component {
    static final String MODULE_ACTION_ATTRIB = "module_action";

    boolean canHandle(String action, Message msg, Map<String, Object> options);

    String getModuleAction();

    void handle(String action, Message msg, Map<String, Object> options) throws OpenAS2Exception;
}
