package org.openas2.schedule;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.BaseComponent;
import org.openas2.Component;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.params.InvalidParameterException;
import org.openas2.processor.ProcessorModule;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Scheduler module for periodic tasks.
 */
public class SchedulerComponent extends BaseComponent {

    public static final String PARAMETER_THREADS = "threads";
    //
    private static final int MIN_AMOUNT_OF_THREADS = 6;
    private Log logger = LogFactory.getLog(SchedulerComponent.class.getSimpleName());

    private ScheduledExecutorService executorService;

    @Override
    public void init(Session session, Map<String, String> parameters) throws OpenAS2Exception {
        super.init(session, parameters);
        createExecutor();
        scheduleComponentsTasks(session);
        scheduleModuleTasks(session);
    }

    private void createExecutor() throws InvalidParameterException {
        int configuredAmountOfThreads = getParameterInt(PARAMETER_THREADS, false);
        int amountOfThreads = configuredAmountOfThreads < MIN_AMOUNT_OF_THREADS ? MIN_AMOUNT_OF_THREADS : configuredAmountOfThreads;
        BasicThreadFactory threadFactory = new BasicThreadFactory.Builder().namingPattern(getName() + "-Thread-%d").build();

        this.executorService = Executors.newScheduledThreadPool(amountOfThreads, threadFactory);
        logger.debug("Scheduler module is ready.");
    }

    private void scheduleComponentsTasks(Session session) throws OpenAS2Exception {
        for (Component component : session.getComponents().values()) {
            if (HasSchedule.class.isAssignableFrom(component.getClass())) {
                //logger.trace("Loading scheduling for component: " + component.getName());
                ((HasSchedule) component).schedule(executorService);
            }
        }
    }

    private void scheduleModuleTasks(Session session) throws OpenAS2Exception {
        for (ProcessorModule module : session.getProcessor().getModules()) {
            if (HasSchedule.class.isAssignableFrom(module.getClass())) {
                //logger.trace("Loading scheduling for module: " + module.getName());
                ((HasSchedule) module).schedule(executorService);
            }
        }
    }

    @Override
    public void destroy() throws Exception {
        //graceful shutdown
        executorService.awaitTermination(3, TimeUnit.SECONDS);
        executorService.shutdownNow();
    }
}
