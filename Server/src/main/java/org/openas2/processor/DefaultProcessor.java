package org.openas2.processor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.BaseComponent;
import org.openas2.OpenAS2Exception;
import org.openas2.message.Message;

public class DefaultProcessor extends BaseComponent implements Processor {
    private static final Log LOGGER = LogFactory.getLog(DefaultProcessor.class.getSimpleName());
    private List<ProcessorModule> modules = new ArrayList<ProcessorModule>();

    public List<ProcessorModule> getActiveModules()
    {
        List<ProcessorModule> activeMods = new ArrayList<ProcessorModule>();
        Iterator<ProcessorModule> moduleIt = getModules().iterator();
        ProcessorModule procMod;

        while (moduleIt.hasNext())
        {
            procMod = moduleIt.next();

            if (procMod instanceof ActiveModule)
            {
                activeMods.add(procMod);
            }
        }

        return activeMods;
    }

    public List<ProcessorModule> getModules()
    {
        return modules;
    }

    public void handle(String action, Message msg, Map<Object, Object> options)
            throws OpenAS2Exception
    {
        Iterator<ProcessorModule> moduleIt = getModules().iterator();
        ProcessorModule module;
        ProcessorException pex = null;
        boolean moduleFound = false;

        if (LOGGER.isDebugEnabled())
        {
            LOGGER.debug("Processor searching for module handler for action: " + action);
        }

        while (moduleIt.hasNext())
        {
            module = moduleIt.next();

            if (module.canHandle(action, msg, options))
            {
                try
                {
                    moduleFound = true;
                    module.handle(action, msg, options);
                } catch (OpenAS2Exception oae)
                {
                    if (pex == null)
                    {
                        pex = new ProcessorException(this);
                        pex.getCauses().add(oae);
                    }
                }
            }
        }

        if (pex != null)
        {
            throw pex;
        } else if (!moduleFound)
        {
            if ("true".equalsIgnoreCase((String) options.get("OPTIONAL_MODULE")))
            {
                return;
            }
            msg.setLogMsg("No handler found for action: " + action);
            LOGGER.error(msg);
            throw new NoModuleException(action, msg, options);
        }
    }

    public void startActiveModules() throws OpenAS2Exception
    {

        List<ProcessorModule> activeModules = getActiveModules();
        for (ProcessorModule processorModule : activeModules)
        {
            try
            {
                ((ActiveModule) processorModule).start();
                LOGGER.info(ClassUtils.getSimpleName(processorModule.getClass()) + " started.");
            } catch (OpenAS2Exception e)
            {
                e.terminate();
                throw e;
            }
        }
        LOGGER.info(activeModules.size() + " active module(s) started.");
    }

    public void stopActiveModules()
    {

        List<ProcessorModule> activeModules = getActiveModules();
        for (ProcessorModule processorModule : activeModules)
        {
            try
            {
                if (processorModule instanceof ActiveModule)
                {
                    ActiveModule activeModule = ActiveModule.class.cast(processorModule);
                    if (activeModule.isRunning())
                    {
                        activeModule.stop();
                        LOGGER.info(ClassUtils.getSimpleName(processorModule.getClass()) + " stopped.");
                    }
                }

            } catch (OpenAS2Exception e)
            {
                e.terminate();
            }
        }

        LOGGER.info(activeModules.size() + " active module(s) stopped.");
    }

    @Override
    public void destroy() throws Exception
    {
        stopActiveModules();
    }
}
