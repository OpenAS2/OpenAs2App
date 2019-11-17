package org.openas2.processor;

import org.openas2.OpenAS2Exception;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


public class ProcessorException extends OpenAS2Exception {
    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private Processor processor;
    private List<Exception> causes;

    public ProcessorException(Processor processor) {
        super();
        this.processor = processor;
    }

    public List<Exception> getCauses() {
        if (causes == null) {
            causes = new ArrayList<Exception>();
        }
        return causes;
    }

    public Processor getProcessor() {
        return processor;
    }

    public void setCauses(List<Exception> list) {
        causes = list;
    }

    public void setProcessor(Processor processor) {
        this.processor = processor;
    }

    public String getMessage() {
        StringWriter strWriter = new StringWriter();
        PrintWriter writer = new PrintWriter(strWriter);
        writer.print(super.getMessage());

        Iterator<?> causesIt = getCauses().iterator();
        while (causesIt.hasNext()) {
            Exception e = (Exception) causesIt.next();
            writer.println();
            e.printStackTrace(writer);

        }
        writer.flush();
        return strWriter.toString();
    }

}
