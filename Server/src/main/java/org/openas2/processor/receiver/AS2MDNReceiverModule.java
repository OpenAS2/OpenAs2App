package org.openas2.processor.receiver;

import org.openas2.support.NetModuleHandler;

public class AS2MDNReceiverModule extends NetModule {
	private NetModuleHandler module;

    
     protected NetModuleHandler getHandler()
     {
        module = new AS2MDNReceiverHandler(this);
        return module;
     }     
     

}
