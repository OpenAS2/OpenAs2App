package org.openas2.processor;

import java.util.Map;

import org.openas2.Component;
import org.openas2.OpenAS2Exception;
import org.openas2.message.Message;

public interface ProcessorModule extends Component {

	public boolean canHandle(String action, Message msg, Map<Object, Object> options);

	public void handle(String action, Message msg, Map<Object, Object> options) throws OpenAS2Exception;
}
