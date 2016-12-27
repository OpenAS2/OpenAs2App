package org.openas2.logging;

import java.io.OutputStream;

import org.openas2.OpenAS2Exception;


public interface Formatter {
	
	public String format(Level level, String msg);
	
	public String format(OpenAS2Exception exception, boolean terminated);
	
	public void format(Level level, String msg, OutputStream out);
	
	public void format(OpenAS2Exception exception, boolean terminated, OutputStream out);
}
