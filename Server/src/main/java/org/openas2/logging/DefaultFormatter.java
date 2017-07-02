package org.openas2.logging;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Iterator;
import java.util.Map;

import org.openas2.OpenAS2Exception;
import org.openas2.util.DateUtil;


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
        Iterator< Map.Entry<String, Object>> sourceIt = sources.entrySet().iterator();
        Map.Entry<String, Object> source;

        while (sourceIt.hasNext()) {
            source = sourceIt.next();
            buf.append(source.getKey().toString()).append(System.getProperty("line.separator"));
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

    public void format(OpenAS2Exception exception, boolean terminated, OutputStream out) {
        PrintWriter writer = new PrintWriter(out);

        if (terminated) {
            writer.println("Termination of exception:");
        }

        // Write exception trace
        exception.printStackTrace(writer);

        // Write sources if terminated
        if (terminated) {
            Map<String, Object> sources = exception.getSources();
            Iterator<Map.Entry<String, Object>> sourceIt = sources.entrySet().iterator();
            Map.Entry<String, Object> source;

            while (sourceIt.hasNext()) {
                source = sourceIt.next();
                if (source.getKey() != null) {                
                	writer.println(source.getKey().toString());
                } else {
                	writer.println("null key");
                }
                if (source.getValue() != null) {                
                writer.println(source.getValue().toString());
                } else {
                	writer.println("null value");
                }
            }
        }
		writer.println();
		writer.flush();
    }
}
