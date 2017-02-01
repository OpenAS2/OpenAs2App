package org.openas2.processor.receiver;

public class AS2MDNReceiverModule extends NetModule {

    
     protected NetModuleHandler getHandler() {
        return new AS2MDNReceiverHandler(this);
    }

}
