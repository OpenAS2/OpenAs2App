package org.openas2.logging;

import org.openas2.Component;
import org.openas2.message.Message;


public interface Logger extends Component {
	public void log(Throwable t, Level level, boolean terminated);

	/**
	 * 
	 * @param level The log level we are spewing out
	 * @param msgText The message to log
	 * @param message The context object that will provide additional information
	 */
	public void log(Level level, String msgText, Message message, Throwable t);
    
	public Formatter getFormatter();
	
	public void setFormatter(Formatter formatter);
}
