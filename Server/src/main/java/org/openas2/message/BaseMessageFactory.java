package org.openas2.message;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.openas2.BaseComponent;

import java.util.*;

@SuppressFBWarnings("EI_EXPOSE_REP")
public abstract class BaseMessageFactory extends BaseComponent implements MessageFactory {

    private Map<String,Object> messages;

    @SuppressFBWarnings("UWF_UNWRITTEN_FIELD")
    public Map<String,Object> getMessages() {
        return messages;
    }

}
