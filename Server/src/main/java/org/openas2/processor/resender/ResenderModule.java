package org.openas2.processor.resender;

import org.openas2.processor.ProcessorModule;

public interface ResenderModule extends ProcessorModule {
	public static final String DO_RESEND = "resend";
	public static final String DO_RESENDMDN = "resendmdn";
    public static final String OPTION_CAUSE = "cause";
    public static final String OPTION_INITIAL_SENDER = "initial_sender";
    public static final String OPTION_RESEND_METHOD = "resend_method";
    public static final String OPTION_RETRIES = "retries";
}
