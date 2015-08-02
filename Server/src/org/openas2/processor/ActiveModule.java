package org.openas2.processor;

import org.openas2.OpenAS2Exception;


public interface ActiveModule extends ProcessorModule {
    public boolean isRunning();

    public void start() throws OpenAS2Exception;

    public void stop() throws OpenAS2Exception;
}
