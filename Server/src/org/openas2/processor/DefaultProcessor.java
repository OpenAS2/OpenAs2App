package org.openas2.processor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.message.Message;

public class DefaultProcessor extends BaseProcessor {
    private List<ProcessorModule> modules;
	private Log logger = LogFactory.getLog(DefaultProcessor.class.getSimpleName());

    public List<ProcessorModule> getActiveModules() {
        List<ProcessorModule> activeMods = new ArrayList<ProcessorModule>();
        Iterator<ProcessorModule> moduleIt = getModules().iterator();
        ProcessorModule procMod;

        while (moduleIt.hasNext()) {
            procMod = (ProcessorModule) moduleIt.next();

            if (procMod instanceof ActiveModule) {
                activeMods.add(procMod);
            }
        }

        return activeMods;
    }

    public void setModules(List<ProcessorModule> modules) {
        this.modules = modules;
    }

    public List<ProcessorModule> getModules() {
        if (modules == null) {
            modules = new ArrayList<ProcessorModule>();
        }

        return modules;
    }

    public void handle(String action, Message msg, Map<Object, Object> options)
        throws OpenAS2Exception {
        Iterator<ProcessorModule> moduleIt = getModules().iterator();
        ProcessorModule module;
        ProcessorException pex = null;
        boolean moduleFound = false;

		if (logger.isDebugEnabled())
		{
			logger.debug("Processor searching for module handler for action: " + action);
		}
		
        while (moduleIt.hasNext()) {
            module = (ProcessorModule) moduleIt.next();

            if (module.canHandle(action, msg, options)) {
                try {
                    moduleFound = true;
                    module.handle(action, msg, options);
                } catch (OpenAS2Exception oae) {
                    if (pex == null) {
                        pex = new ProcessorException(this);
                        pex.getCauses().add(oae);
                    }
                }
            }
        }

        if (pex != null) {
            throw pex;
        } else if (!moduleFound) {
            logger.error("No handler found for action: " + action);
            throw new NoModuleException(action, msg, options);
        }
    }

    public void startActiveModules() {
        Iterator<ProcessorModule> activeIt = getActiveModules().iterator();

        while (activeIt.hasNext()) {
            try {
                ((ActiveModule) activeIt.next()).start();
            } catch (OpenAS2Exception e) {
                e.terminate();
            }
        }
    }

    public void stopActiveModules() {
        Iterator<ProcessorModule> activeIt = getActiveModules().iterator();

        while (activeIt.hasNext()) {
            try {
                ((ActiveModule) activeIt.next()).stop();
            } catch (OpenAS2Exception e) {
                e.terminate();
            }
        }
    }
}
