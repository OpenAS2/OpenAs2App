package org.openas2.logging;

import org.openas2.Component;
import org.openas2.OpenAS2Exception;
import org.openas2.message.Message;


public interface Logger extends Component {
	public void log(OpenAS2Exception e, boolean terminated);

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
