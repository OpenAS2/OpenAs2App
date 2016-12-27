package org.openas2.logging;

import org.openas2.Component;
import org.openas2.message.Message;


public interface Logger extends Component {
	public void log(Throwable t, Level level, boolean terminated);

	/**
	 * 
	 * @param level
	 * @param msgText
	 * @param message
	 */
	public void log(Level level, String msgText, Message message);
    
	public Formatter getFormatter();
	
	public void setFormatter(Formatter formatter);
}
