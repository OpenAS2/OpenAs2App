package org.openas2.processor;

import org.openas2.Component;
import org.openas2.OpenAS2Exception;
import org.openas2.message.Message;

import java.util.Map;


public interface ProcessorModule extends Component {

    boolean canHandle(String action, Message msg, Map<Object, Object> options);

    void handle(String action, Message msg, Map<Object, Object> options) throws OpenAS2Exception;
}
