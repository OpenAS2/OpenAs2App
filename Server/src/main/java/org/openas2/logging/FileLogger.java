package org.openas2.logging;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.message.Message;
import org.openas2.params.CompositeParameters;
import org.openas2.params.DateParameters;
import org.openas2.params.ParameterParser;

public class FileLogger extends BaseLogger {
    public static final String PARAM_FILENAME = "filename";

    public void init(Session session, Map<String, String> parameters) throws OpenAS2Exception {
        super.init(session, parameters);
        // check if log file can be created
        getLogFile();
    }

    public void doLog(Level level, String msgText, Message as2Msg) {
        appendToFile(getFormatter().format(level, msgText + (as2Msg == null?"":as2Msg.getLogMsgID())));
    }

    protected String getShowDefaults() {
        return VALUE_SHOW_ALL;
    }

    protected void appendToFile(String text) {
        try {
            File logFile = getLogFile();
            FileWriter writer = new FileWriter(logFile, true);
            writer.write(text);
            writer.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    protected File getLogFile() throws OpenAS2Exception {
        String filename = getParameter(PARAM_FILENAME, true);

        ParameterParser parser = createParser();
        filename = ParameterParser.parse(filename, parser);

        File logFile = new File(filename);

        if (!logFile.exists()) {
            File parentDir = logFile.getParentFile();
            if (!parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    String msg = "Could not create log directories for file \"" + logFile.getAbsolutePath()
                            + "\" for log file parameter \"" + filename + "\"";
                    throw new OpenAS2Exception(msg);
                }
            }
            try {
                if (!logFile.createNewFile()) {
                    String msg = "Could not create log file \"" + logFile.getAbsolutePath()
                            + "\" for log file parameter \"" + filename + "\"";
                    throw new OpenAS2Exception(msg);
                }
            } catch (IOException ioe) {
                String msg = "Could not create log file \"" + logFile.getAbsolutePath()
                        + "\" for log file parameter \"" + filename + "\": " + ioe.getMessage();
                throw new OpenAS2Exception(msg, ioe);
            }
        }

        return logFile;
    }

    protected ParameterParser createParser() {
        CompositeParameters compParams = new CompositeParameters(false);
        compParams.add("date", new DateParameters());

        return compParams;
    }

    protected void doLog(Throwable t, boolean terminated) {
        appendToFile(getFormatter().format(t, terminated));
    }
}