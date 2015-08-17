package org.openas2.logging;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class LogManager {
    private static LogManager defaultManager;
    private List<Logger> loggers;

    public static LogManager getLogManager() {
        if (defaultManager == null) {
            defaultManager = new LogManager();
        }

        return defaultManager;
    }
    
    private static boolean registeredWithApache = false;

    public void setLoggers(List<Logger> listeners) {
        this.loggers = listeners;
    }

    public List<Logger> getLoggers() {
        if (loggers == null) {
            loggers = new ArrayList<Logger>();
        }

        return loggers;
    }

    public void addLogger(Logger logger) {
        List<Logger> loggers = getLoggers();
        loggers.add(logger);
    }

    public void log(Throwable e, boolean terminated) {
        Iterator<?> loggerIt = getLoggers().iterator();

        if (loggerIt.hasNext()) {
            while (loggerIt.hasNext()) {
                Logger logger = (Logger) loggerIt.next();
                logger.log(e, Level.ERROR, terminated); // might want to pass LEVEL in from caller
            }
        } else {
            e.printStackTrace();
        }
    }

    /**
     * @param level
     * @param msg
     */
    public void log(Level level, String msg) {
        Iterator<?> loggerIt = getLoggers().iterator();

        if (loggerIt.hasNext()) {
            while (loggerIt.hasNext()) {
                Logger logger = (Logger) loggerIt.next();
                logger.log(level, msg, null);
            }
        } else {
            System.out.println(level.getName() + " " + msg);
        }
    }

    ArrayList<String> requestors = new ArrayList<String>();
	/**
	 * @param log
	 */
	public void addRequestors(String inName) {
		requestors.add(inName);
		setRegisteredWithApache(true);
	}

	/**
	 * @param registeredWithApache the registedWithApache to set
	 */
	public static void setRegisteredWithApache(boolean registedWithApache) {
		LogManager.registeredWithApache = registedWithApache;
	}

	/**
	 * @return the registeredWithApache
	 */
	public static boolean isRegisteredWithApache() {
		return registeredWithApache;
	}
    
    
    
    
}
