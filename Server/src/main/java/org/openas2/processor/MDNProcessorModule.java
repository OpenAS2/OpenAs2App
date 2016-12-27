package org.openas2.processor;

import java.util.Map;

import org.openas2.Component;
import org.openas2.OpenAS2Exception;
import org.openas2.message.MessageMDN;


public interface MDNProcessorModule extends Component {
		
    public boolean canHandle(String action, MessageMDN msg, Map<String,String> options);

    public void handle(String action, MessageMDN msg, Map<String,String> options) throws OpenAS2Exception;
}
