package org.openas2.processor.storage;

import org.openas2.OpenAS2Exception;
import org.openas2.WrappedException;
import org.openas2.message.Message;
import org.openas2.message.MessageMDN;
import org.openas2.params.CompositeParameters;
import org.openas2.params.DateParameters;
import org.openas2.params.InvalidParameterException;
import org.openas2.params.MessageMDNParameters;
import org.openas2.params.ParameterParser;
import org.openas2.params.RandomParameters;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;

public class MDNFileModule extends BaseStorageModule {

    public void handle(String action, Message msg, Map<Object, Object> options) throws OpenAS2Exception {
        // store mdn data
        if (msg.getMDN() == null) {
            throw new OpenAS2Exception("Message has no MDN");
        }

        try {
            File mdnFile = getFile(msg, getParameter(PARAM_FILENAME, true), "");
            InputStream in = getMDNStream(msg.getMDN());
            store(mdnFile, in);
        } catch (IOException ioe) {
            throw new WrappedException(ioe);
        }
    }

    protected String getModuleAction() {
        return DO_STOREMDN;
    }


    /**
     * @since 2007-06-01
     */
    protected String getFilename(Message msg, String fileParam, String action) throws InvalidParameterException {
        MessageMDN mdn = msg.getMDN();
        CompositeParameters compParams = new CompositeParameters(false).add("date", new DateParameters()).add("mdn", new MessageMDNParameters(mdn)).add("rand", new RandomParameters());

        return ParameterParser.parse(fileParam, compParams);
    }

    protected InputStream getMDNStream(MessageMDN mdn) throws IOException {
        StringBuffer mdnBuf = new StringBuffer();

        // write headers to the string buffer
        mdnBuf.append("Headers:" + System.getProperty("line.separator"));

        Enumeration<String> headers = mdn.getHeaders().getAllHeaderLines();
        String header;

        while (headers.hasMoreElements()) {
            header = headers.nextElement();
            mdnBuf.append(header).append(System.getProperty("line.separator"));
        }

        mdnBuf.append(System.getProperty("line.separator"));

        // write attributes to the string buffer
        mdnBuf.append("Attributes:" + System.getProperty("line.separator"));

        Iterator<Map.Entry<String, String>> attrIt = mdn.getAttributes().entrySet().iterator();
        Map.Entry<?, String> attrEntry;

        while (attrIt.hasNext()) {
            attrEntry = attrIt.next();
            mdnBuf.append(attrEntry.getKey()).append(": ");
            mdnBuf.append(attrEntry.getValue()).append(System.getProperty("line.separator"));
        }
        // finaly, write the MDN text
        mdnBuf.append("Text:" + System.getProperty("line.separator"));
        mdnBuf.append(mdn.getText());

        return new ByteArrayInputStream(mdnBuf.toString().getBytes());
    }
}
