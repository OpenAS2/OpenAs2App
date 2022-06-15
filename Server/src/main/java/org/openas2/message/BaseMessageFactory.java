package org.openas2.message;

import org.openas2.BaseComponent;
import java.util.Map;

public abstract class BaseMessageFactory extends BaseComponent implements MessageFactory {
    
    private Map<String,Object> messages;

    public Map<String,Object> getMessages() {
        return messages;
    }
    
}
