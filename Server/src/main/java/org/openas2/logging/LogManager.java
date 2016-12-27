package org.openas2.logging;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.openas2.message.Message;


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
     * @param clazzName - the name of the class that the log was generated in
     * @param message - the logging object to create the message from
     */
    public void log(Level level, String clazzName, Object msg) {
        Iterator<?> loggerIt = getLoggers().iterator();

        if (loggerIt.hasNext()) {
            while (loggerIt.hasNext()) {
                Logger logger = (Logger) loggerIt.next();
                if (msg instanceof Message) logger.log(level, clazzName + ": " + ((Message)msg).getLogMsg(), (Message) msg);
                else logger.log(level, clazzName + ": " + msg.toString(), null);
            }
        } else {
            System.out.println(level.getName() + " " + msg.toString());
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
