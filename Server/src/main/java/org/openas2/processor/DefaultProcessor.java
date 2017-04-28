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
    private List<ProcessorModule> modules = new ArrayList<ProcessorModule>();
    private Log logger = LogFactory.getLog(DefaultProcessor.class.getSimpleName());

    public List<ActiveModule> getActiveModules()
    {
        List<ActiveModule> activeMods = new ArrayList<ActiveModule>();
        Iterator<ProcessorModule> moduleIt = getModules().iterator();
        ProcessorModule procMod;

        while (moduleIt.hasNext())
        {
            procMod = moduleIt.next();

            if (procMod instanceof ActiveModule)
            {
                activeMods.add((ActiveModule)procMod);
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

        if (logger.isDebugEnabled())
        {
            logger.debug("Processor searching for module handler for action: " + action);
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
            logger.error(msg);
            throw new NoModuleException(action, msg, options);
        }
    }

    public void startActiveModules() throws OpenAS2Exception
    {

        List<ActiveModule> activeModules = getActiveModules();
        for (ActiveModule activeModule : activeModules)
        {
            try
            {
            	activeModule.start();
                logger.info(ClassUtils.getSimpleName(activeModule.getClass()) + " started.");
            } catch (OpenAS2Exception e)
            {
                e.terminate();
                throw e;
            }
        }
        logger.info(activeModules.size() + " active module(s) started.");
    }

    public void stopActiveModules()
    {

        List<ActiveModule> activeModules = getActiveModules();
        int stopCnt = 0;
        for (ActiveModule activeModule : activeModules)
        {
        	try
            {
        		if (activeModule.isRunning())
        		{
        			activeModule.stop();
        			stopCnt++;
                    logger.info(ClassUtils.getSimpleName(activeModule.getClass()) + " stopped.");
        		}
            } catch (OpenAS2Exception e)
            {
                e.terminate();
            }
        }

        if (logger.isInfoEnabled())
        {
        	if (stopCnt >0) logger.info(stopCnt + " active module(s) stopped.");
        	else logger.info("No active module(s) are running.");
        }
    }

    @Override
    public void destroy() throws Exception
    {
        stopActiveModules();
    }
}
