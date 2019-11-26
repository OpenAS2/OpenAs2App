/**
 *
 */
package org.openas2.logging;

import org.apache.commons.logging.LogFactory;
import org.openas2.Session;

import java.io.InputStream;
import java.util.Properties;


/**
 * @author joseph mcverry
 *
 */
public class Log implements org.apache.commons.logging.Log {

    LogManager lm;
    String clazzname;

    /** All system properties used by <code>OpenAS2</code> start with this */
    static protected final String systemPrefix = "org.openas2.logging.";

    /** Properties loaded from simplelog.properties */
    static protected final Properties openas2LogProps = new Properties();

    /** "Trace" level logging. */
    public static final int LOG_LEVEL_TRACE = 1;
    /** "Debug" level logging. */
    public static final int LOG_LEVEL_DEBUG = 2;
    /** "Info" level logging. */
    public static final int LOG_LEVEL_INFO = 3;
    /** "Warn" level logging. */
    public static final int LOG_LEVEL_WARN = 4;
    /** "Error" level logging. */
    public static final int LOG_LEVEL_ERROR = 5;
    /** "Fatal" level logging. */
    public static final int LOG_LEVEL_FATAL = 6;

    /** Enable all logging levels */
    public static final int LOG_LEVEL_ALL = LOG_LEVEL_TRACE - 1;

    /** Enable no logging levels */
    public static final int LOG_LEVEL_OFF = LOG_LEVEL_FATAL + 1;

    /** The configured log level */
    protected volatile int configuredLogLevel;

    /** The current log level */
    protected volatile int currentLogLevel;

    static {
        // Load properties file, if found.
        // Override with system properties.
        // Add props from the resource simplelog.properties
        String logPropsFiile = System.getProperty("openas2log.properties", "openas2log.properties");
        InputStream in = getResourceAsStream(logPropsFiile);
        if (null != in) {
            try {
                openas2LogProps.load(in);
                in.close();
            } catch (java.io.IOException e) {
                // ignored
            }
        }
    }

    public Log(String inName) {
        lm = LogManager.getLogManager();
        lm.addRequestors(inName);
        clazzname = inName;

        // Set initial log level
        // Used to be: set default log level to ERROR
        // IMHO it should be lower, but at least info ( costin ).
        setLevel(Log.LOG_LEVEL_INFO);

        // Set log level from properties
        String lvl = getStringProperty(systemPrefix + "log." + clazzname);
        int i = String.valueOf(inName).lastIndexOf(".");
        while (null == lvl && i > -1) {
            inName = inName.substring(0, i);
            lvl = getStringProperty(systemPrefix + "log." + inName);
            i = inName.lastIndexOf(".");
        }

        if (null == lvl) {
            lvl = getStringProperty(systemPrefix + "defaultlog");
        }
        /* Still not found so use the commons one if there is one */
        if (null == lvl) {
            lvl = (String) LogFactory.getFactory().getAttribute("level");
        }
        if (null != lvl) {
            setLevel(getIntLogLevel(lvl));
        }
        configuredLogLevel = getLevel();

    }

    private static int getIntLogLevel(String lvl) {
        if ("all".equalsIgnoreCase(lvl)) {
            return (Log.LOG_LEVEL_ALL);
        } else if ("trace".equalsIgnoreCase(lvl)) {
            return (Log.LOG_LEVEL_TRACE);
        } else if ("debug".equalsIgnoreCase(lvl)) {
            return (Log.LOG_LEVEL_DEBUG);
        } else if ("info".equalsIgnoreCase(lvl)) {
            return (Log.LOG_LEVEL_INFO);
        } else if ("warn".equalsIgnoreCase(lvl)) {
            return (Log.LOG_LEVEL_WARN);
        } else if ("error".equalsIgnoreCase(lvl)) {
            return (Log.LOG_LEVEL_ERROR);
        } else if ("fatal".equalsIgnoreCase(lvl)) {
            return (Log.LOG_LEVEL_FATAL);
        } else if ("off".equalsIgnoreCase(lvl)) {
            return (Log.LOG_LEVEL_OFF);
        }
        return -1;
    }

    private static String getStringProperty(String name) {
        String prop = null;
        try {
            prop = System.getProperty(name);
        } catch (SecurityException e) {
            // Ignore
        }
        return prop == null ? openas2LogProps.getProperty(name) : prop;
    }

    /**
     * Reset logging level to congiured level.
     *
     *
     */
    public void resetLevel() {
        this.currentLogLevel = configuredLogLevel;
    }

    /**
     * Set logging level.
     *
     * @param currentLogLevel new logging level
     */
    public void setLevel(int currentLogLevel) {
        this.currentLogLevel = currentLogLevel;
    }

    /**
     * Get logging level.
     * @return the current logging level
     */
    public int getLevel() {
        return currentLogLevel;
    }

    protected boolean isLevelEnabled(int logLevel) {
        String overrideSetting = System.getProperty(Session.LOG_LEVEL_OVERRIDE_KEY + "." + clazzname, "");

        if ("".equals(overrideSetting)) {
            overrideSetting = System.getProperty(Session.LOG_LEVEL_OVERRIDE_KEY, "");
        }
        if (!"".equals(overrideSetting)) {
            int overrideLevel = getIntLogLevel(overrideSetting);
            if (overrideLevel >= 0) {
                return logLevel >= overrideLevel;
            }
        }
        // log level are numerically ordered so can use simple numeric
        // comparison
        return logLevel >= currentLogLevel;
    }

    /* (non-Javadoc)
     * @see org.apache.commons.logging.Log#trace(java.lang.Object)
     */
    public void trace(Object message) {
        trace(message, null);
    }

    public void trace(Object message, Throwable t) {
        if (isLevelEnabled(LOG_LEVEL_TRACE)) {
            lm.log(Level.FINEST, clazzname, message, t);
        }
    }

    /* (non-Javadoc)
     * @see org.apache.commons.logging.Log#debug(java.lang.Object)
     */
    public void debug(Object message) {
        debug(message, null);
    }

    public void debug(Object message, Throwable t) {
        if (isLevelEnabled(LOG_LEVEL_DEBUG)) {
            lm.log(Level.FINER, clazzname, message, t);
        }
    }

    /* (non-Javadoc)
     * @see org.apache.commons.logging.Log#info(java.lang.Object)
     */
    public void info(Object message) {
        info(message, null);

    }

    public void info(Object message, Throwable t) {
        if (isLevelEnabled(LOG_LEVEL_INFO)) {
            lm.log(Level.FINE, clazzname, message, t);
        }
    }

    /* (non-Javadoc)
     * @see org.apache.commons.logging.Log#warn(java.lang.Object)
     */
    public void warn(Object message) {
        warn(message, null);

    }

    public void warn(Object message, Throwable t) {
        if (isLevelEnabled(LOG_LEVEL_WARN)) {
            lm.log(Level.WARNING, clazzname, message, t);
        }
    }


    /* (non-Javadoc)
     * @see org.apache.commons.logging.Log#error(java.lang.Object)
     */
    public void error(Object message) {
        error(message, null);

    }

    /* (non-Javadoc)
     * @see org.apache.commons.logging.Log#error(java.lang.Object, java.lang.Throwable)
     */
    public void error(Object message, Throwable t) {
        if (isLevelEnabled(LOG_LEVEL_ERROR)) {
            lm.log(Level.ERROR, clazzname, message, t);
        }

    }

    /* (non-Javadoc)
     * @see org.apache.commons.logging.Log#fatal(java.lang.Object)
     */
    public void fatal(Object message) {
        fatal(message, null);

    }

    public void fatal(Object message, Throwable t) {
        if (isLevelEnabled(LOG_LEVEL_FATAL)) {
            lm.log(Level.ERROR, clazzname, message, t);
        }
    }

    /* (non-Javadoc)
     * @see org.apache.commons.logging.Log#isDebugEnabled()
     */
    public boolean isDebugEnabled() {
        return isLevelEnabled(Log.LOG_LEVEL_DEBUG);
    }

    /* (non-Javadoc)
     * @see org.apache.commons.logging.Log#isErrorEnabled()
     */
    public boolean isErrorEnabled() {
        return isLevelEnabled(Log.LOG_LEVEL_ERROR);
    }

    /* (non-Javadoc)
     * @see org.apache.commons.logging.Log#isFatalEnabled()
     */
    public boolean isFatalEnabled() {
        return isLevelEnabled(Log.LOG_LEVEL_FATAL);
    }

    /* (non-Javadoc)
     * @see org.apache.commons.logging.Log#isInfoEnabled()
     */
    public boolean isInfoEnabled() {
        return isLevelEnabled(Log.LOG_LEVEL_INFO);
    }

    /* (non-Javadoc)
     * @see org.apache.commons.logging.Log#isTraceEnabled()
     */
    public boolean isTraceEnabled() {
        return isLevelEnabled(Log.LOG_LEVEL_TRACE);
    }

    /* (non-Javadoc)
     * @see org.apache.commons.logging.Log#isWarnEnabled()
     */
    public boolean isWarnEnabled() {
        return isLevelEnabled(Log.LOG_LEVEL_WARN);
    }

    private static InputStream getResourceAsStream(final String name) {
        return ClassLoader.getSystemResourceAsStream(name);
    }

    public static String getExceptionMsg(Throwable e) {
        String msg = null;
        if (e.getCause() != null) {
            msg = e.getCause().getMessage();
        }
        if (msg == null || msg.length() == 0) {
            msg = e.getMessage();
            if (msg == null || msg.length() == 0) {
                msg = e.toString();
            }
        }
        return msg;
    }
}
