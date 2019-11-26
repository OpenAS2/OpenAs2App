package org.openas2.processor;

import org.openas2.OpenAS2Exception;

import java.util.List;


public interface ActiveModule extends ProcessorModule {
    boolean isRunning();

    void start() throws OpenAS2Exception;

    void stop() throws OpenAS2Exception;

    /**
     * When invoked, the module must run a self check to verify it is functioning correctly.
     * Any failures must be reported in the failures list passed in to the method by the callee
     *
     * @param failures - a list of failures if any occur
     * @return - true if module has no problems otherwise false ith failure messages in passed in List
     */
    boolean healthcheck(List<String> failures);

}
