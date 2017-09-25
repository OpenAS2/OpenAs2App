package org.openas2.processor.receiver;

public class HealthCheckModule extends NetModule {

    
     protected NetModuleHandler getHandler() {
        return new HealthCheckHandler(this);
    }

}
