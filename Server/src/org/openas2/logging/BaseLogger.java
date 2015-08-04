package org.openas2.logging;

import java.util.StringTokenizer;

import org.openas2.BaseComponent;
import org.openas2.OpenAS2Exception;
import org.openas2.message.Message;


public abstract class BaseLogger extends BaseComponent implements Logger {
	public static final String PARAM_EXCEPTIONS = "exceptions";
	public static final String PARAM_SHOW = "show";
	public static final String VALUE_SHOW_ALL = "all"; // all exceptions (terminated or not) and info
	public static final String VALUE_SHOW_TERMINATED = "terminated"; // all terminated exceptions
	public static final String VALUE_SHOW_EXCEPTIONS = "exceptions"; // all non-terminated exceptions
	public static final String VALUE_SHOW_INFO = "info"; // all info log entries
	private Formatter formatter;

	public void setFormatter(Formatter formatter) {
		this.formatter = formatter;
	}

	public Formatter getFormatter() {
		if (formatter == null) {
			formatter = new DefaultFormatter();
		}

		return formatter;
	}

	public void log(OpenAS2Exception e, boolean terminated) {
		if (isLogging(e)) {
			if (terminated && isShowing(VALUE_SHOW_TERMINATED)) {
				doLog(e, terminated);
			} else if (!terminated && isShowing(VALUE_SHOW_EXCEPTIONS)) {
				doLog(e, terminated);
			}
		}
	}

	/**
	 * level
	 * msgText
	 * message
	 */
	public void log(Level level, String msgText, Message message) {
		if (isShowing(VALUE_SHOW_INFO)) {
			doLog(level, msgText, message);
		}
	}

	protected boolean isLogging(OpenAS2Exception exception) {
		try {
			String exceptionName = exception.getClass().getName();
			String exceptions = getParameter(PARAM_EXCEPTIONS, false);
            if (exceptions != null) {
            
			StringTokenizer exceptionTokens = new StringTokenizer(exceptions, ",", false);			

			while (exceptionTokens.hasMoreTokens()) {
				if (exceptionTokens.nextToken().equals(exceptionName)) {
					return true;
				}
			}
            } else {
                return true;
            }
		} catch (Exception e) {
			e.printStackTrace();
		}

		return false;
	}

	protected abstract String getShowDefaults();

	protected boolean isShowing(String value) {
		try {
			String showOptions = getParameter(PARAM_SHOW, false);

			if (showOptions == null) {
				showOptions = getShowDefaults();
			}

			if ((showOptions.indexOf(value) >= 0) || (showOptions.indexOf(VALUE_SHOW_ALL) >= 0)) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return false;
	}

	protected abstract void doLog(OpenAS2Exception exception, boolean terminated);

	protected abstract void doLog(Level level, String msgText, Message message);
}
