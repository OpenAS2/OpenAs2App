package org.openas2.processor.receiver;

import java.util.List;

public class HealthCheckModule extends NetModule {


    protected NetModuleHandler getHandler() {
        return new HealthCheckHandler(this);
    }

    @Override
    public boolean healthcheck(List<String> failures) {
        return true;
    }

}
