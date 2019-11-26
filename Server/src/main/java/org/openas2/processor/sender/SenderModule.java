package org.openas2.processor.sender;

import org.openas2.processor.ProcessorModule;


public interface SenderModule extends ProcessorModule {
    String DO_SEND = "send";
    String DO_SENDMDN = "sendmdn";

    String SOPT_RETRIES = "retries";

    String DEFAULT_RETRIES = "-1";    // Infinite
}
