
package org.openas2.processor.sender;

import org.openas2.processor.ProcessorModule;


public interface SenderModule extends ProcessorModule {
	public static final String DO_SEND = "send";	
	public static final String DO_SENDMDN = "sendmdn";
	
	public static final String SOPT_RETRIES = "retries";
	
	public static final String DEFAULT_RETRIES = "-1";	// Infinite
}
