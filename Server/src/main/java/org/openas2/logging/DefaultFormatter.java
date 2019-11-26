package org.openas2.logging;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.openas2.OpenAS2Exception;
import org.openas2.util.DateUtil;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Map;


public class DefaultFormatter extends BaseFormatter {
    public String getTerminatedMessage(OpenAS2Exception exception) {
        StringBuffer buf = new StringBuffer("Termination of exception:" + System.getProperty("line.separator"));

        // Write exception trace
        StringWriter strWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(strWriter);
        exception.printStackTrace(printWriter);
        buf.append(strWriter.toString());

        // Write sources
        Map<String, Object> sources = exception.getSources();
        Iterator<Map.Entry<String, Object>> sourceIt = sources.entrySet().iterator();
        Map.Entry<String, Object> source;

        while (sourceIt.hasNext()) {
            source = sourceIt.next();
            buf.append(source.getKey()).append(System.getProperty("line.separator"));
            buf.append(source.getValue().toString()).append(System.getProperty("line.separator")).append(System.getProperty("line.separator"));
        }

        return buf.toString();
    }

    public void format(Level level, String msg, OutputStream out) {
        PrintWriter writer = new PrintWriter(out);

        // Write timestamp
        writer.print(DateUtil.formatDate(dateFormat));

        // Write log level
        writer.print(" ");
        writer.print(level.getName().toUpperCase());

        // Write message
        writer.print(" ");
        writer.println(msg);
        writer.flush();
    }

    public void format(Throwable t, boolean terminated, OutputStream out) {
        PrintWriter writer = new PrintWriter(out);
        writer.println(format(t, terminated));
        writer.println();
        writer.flush();
    }

    @Override
    public String format(Throwable t, boolean terminated) {
        StringBuffer sb = new StringBuffer();

        if (terminated) {
            sb.append("Termination of exception:");
        }

        // Write exception trace
        sb.append(ExceptionUtils.getStackTrace(t));

        // Write sources if terminated
        if (terminated && t instanceof OpenAS2Exception) {
            OpenAS2Exception exception = (OpenAS2Exception) t;
            Map<String, Object> sources = exception.getSources();
            Iterator<Map.Entry<String, Object>> sourceIt = sources.entrySet().iterator();
            Map.Entry<String, Object> source;

            while (sourceIt.hasNext()) {
                source = sourceIt.next();
                if (source.getKey() != null) {
                    sb.append(source.getKey());
                } else {
                    sb.append("null key");
                }
                if (source.getValue() != null) {
                    sb.append(source.getValue().toString());
                } else {
                    sb.append("null value");
                }
            }
        }
        return sb.toString();
    }
}
