package org.openas2.processor.resender;

import org.openas2.processor.ProcessorModule;

public interface ResenderModule extends ProcessorModule {
    String DO_RESEND = "resend";
    String DO_RESENDMDN = "resendmdn";
    String OPTION_CAUSE = "cause";
    String OPTION_INITIAL_SENDER = "initial_sender";
    String OPTION_RESEND_METHOD = "resend_method";
    String OPTION_RETRIES = "retries";
    String OPTION_RETRY_COUNT = "retry_count";
    String OPTION_MAX_RETRY_COUNT = "max_retry_count";

    int DEFAULT_RETRIES = -1;    // Infinite
}
