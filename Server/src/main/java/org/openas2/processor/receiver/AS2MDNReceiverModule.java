package org.openas2.processor.receiver;

public class AS2MDNReceiverModule extends NetModule {
    private NetModuleHandler module;


    protected NetModuleHandler getHandler() {
        module = new AS2MDNReceiverHandler(this);
        return module;
    }


}
