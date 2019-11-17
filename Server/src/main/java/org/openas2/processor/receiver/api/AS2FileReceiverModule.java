package org.openas2.processor.receiver.api;

import org.openas2.message.AS2Message;
import org.openas2.processor.receiver.MessageBuilderModule;

import java.util.List;

public class AS2FileReceiverModule extends MessageBuilderModule {

    protected AS2Message createMessage() {
        return new AS2Message();
    }

    public void doStart() {
        return;
    }

    public void doStop() {
        return;
    }

    @Override
    public boolean healthcheck(List<String> failures) {
        // TODO Auto-generated method stub
        return true;
    }

}
