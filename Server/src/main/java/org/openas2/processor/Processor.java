package org.openas2.processor;

import java.util.List;
import java.util.Map;

import org.openas2.Component;
import org.openas2.OpenAS2Exception;
import org.openas2.message.Message;

public interface Processor extends Component {
    String COMPID_PROCESSOR = "processor";

    void handle(String action, Message msg, Map<Object, Object> options) throws OpenAS2Exception;

    List<ProcessorModule> getModules();

    void startActiveModules() throws OpenAS2Exception;

    void stopActiveModules() throws OpenAS2Exception;

    List<ActiveModule> getActiveModules();
    
    List<ActiveModule> getActiveModulesByClass(Class<?> clazz);

    public boolean checkActiveModules(List<String> failures);
}
