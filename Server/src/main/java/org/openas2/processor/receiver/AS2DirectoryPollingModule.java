package org.openas2.processor.receiver;

import org.openas2.message.AS2Message;

public class AS2DirectoryPollingModule extends DirectoryPollingModule {

    protected AS2Message createMessage() {
        return new AS2Message();
    }

}
