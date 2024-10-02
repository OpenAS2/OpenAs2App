package org.openas2.processor;

import org.openas2.Component;
import org.openas2.OpenAS2Exception;
import org.openas2.message.Message;

import java.util.List;
import java.util.Map;

public interface Processor extends Component {
    String COMPID_PROCESSOR = "processor";

    String PENDING_MDN_INFO_DIRECTORY_IDENTIFIER = "pendingmdninfo";
    String PENDING_MDN_MSG_DIRECTORY_IDENTIFIER = "pendingmdn";

    void handle(String action, Message msg, Map<String, Object> options) throws OpenAS2Exception;

    List<ProcessorModule> getModules();

    void startActiveModules() throws OpenAS2Exception;

    void stopActiveModules() throws OpenAS2Exception;

    List<ActiveModule> getActiveModules();

    List<ActiveModule> getActiveModulesByClass(Class<?> clazz);

    boolean checkActiveModules(List<String> failures);

    public List<ProcessorModule> getModulesSupportingAction(String action);
}
