package org.openas2.logging;

import org.openas2.message.Message;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


public class LogManager {
    /**
     * A default logger. It used when no loggers are configured in config.xml
     */
    private final static ConsoleLogger DEFAULT_LOGGER = new ConsoleLogger();
    private static boolean registeredWithApache = false;
    private final List<String> requestors = Collections.synchronizedList(new ArrayList<String>());
    private List<Logger> loggers = Collections.synchronizedList(new ArrayList<Logger>());

    public static LogManager getLogManager() {
        return DefaultManager.INSTANCE;
    }

    /**
     * Check to confirm the logger is registered with Apache library
     *
     * @return the registeredWithApache
     */
    public static boolean isRegisteredWithApache() {
        return registeredWithApache;
    }

    public void setLoggers(List<Logger> listeners) {
        this.loggers = listeners;
    }

    public void addLogger(Logger logger) {
        loggers.add(logger);
    }

    public void log(@Nonnull Throwable e, boolean terminated) {
        if (loggers.isEmpty()) {
            DEFAULT_LOGGER.log(e, Level.ERROR, terminated);
        } else {
            for (Logger logger : loggers) {
                logger.log(e, Level.ERROR, terminated); // might want to pass LEVEL in from caller
            }
        }
    }

    /**
     * Logs a message to the configured logging systems
     *
     * @param level     - current log level
     * @param clazzName - the name of the class that the log was generated in
     * @param msg       - the logging object to create the message from
     */
    public void log(Level level, String clazzName, @Nonnull Object msg, Throwable t) {
        if (loggers.isEmpty()) {
            //System.out.println("\n\t    WARNING!!!!\n\tNo loggers configured. Using default logger.");
            DEFAULT_LOGGER.log(level, clazzName + ": " + msg.toString(), null, t);
        } else {
            for (Logger logger : loggers) {
                if (msg instanceof Message) {
                    logger.log(level, clazzName + ": " + ((Message) msg).getLogMsg(), (Message) msg, t);
                } else {
                    logger.log(level, clazzName + ": " + msg.toString(), null, t);
                }
            }
        }
    }

    /**
     * @param inName
     */
    void addRequestors(String inName) {
        requestors.add(inName);
        LogManager.registeredWithApache = true;
    }

    private static class DefaultManager {
        private static final LogManager INSTANCE = new LogManager();
    }
}
