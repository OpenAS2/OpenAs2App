package org.openas2.schedule;

import org.openas2.OpenAS2Exception;

import java.util.concurrent.ScheduledExecutorService;

/**
 * An optional extension of {@link org.openas2.Component} which allow to schedule tasks on scheduler.
 * In case when a component requires to do a periodical work, e.g. directory polling, file change monitoring,
 * this interface could be used instead of threads or timers.
 */
public interface HasSchedule {

    void schedule(ScheduledExecutorService executor) throws OpenAS2Exception;

}
