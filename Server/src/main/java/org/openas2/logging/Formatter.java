package org.openas2.logging;

import java.io.OutputStream;


public interface Formatter {
	
	public String format(Level level, String msg);
	
	public String format(Throwable t, boolean terminated);
	
	public void format(Level level, String msg, OutputStream out);
	
	public void format(Throwable t, boolean terminated, OutputStream out);
	
	public void setDateFormat(String dateFormat);
}
