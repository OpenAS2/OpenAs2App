package org.openas2.processor.monitor;

import org.openas2.processor.monitor.HealthCheckHandler;
import java.util.List;
import org.openas2.processor.receiver.NetModule;
import org.openas2.support.NetModuleHandler;

public class HealthCheckModule extends NetModule {

    
	protected NetModuleHandler getHandler() {
        return new HealthCheckHandler(this);
    }

    @Override
	public boolean healthcheck(List<String> failures)
	{
		return true;
	}

}
