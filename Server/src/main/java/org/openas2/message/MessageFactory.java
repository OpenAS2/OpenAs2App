package org.openas2.message;

import org.openas2.Component;

import java.util.Map;

public interface MessageFactory extends Component {
    String COMPID_MESSAGE_FACTORY = "messagefactory";

    Map<String, Object> getMessages();

    
}
