package org.openas2.processor.storage;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.DispositionException;
import org.openas2.OpenAS2Exception;
import org.openas2.WrappedException;
import org.openas2.message.Message;
import org.openas2.params.CompositeParameters;
import org.openas2.params.DateParameters;
import org.openas2.params.InvalidParameterException;
import org.openas2.params.MessageParameters;
import org.openas2.params.ParameterParser;
import org.openas2.params.RandomParameters;
import org.openas2.partner.Partnership;
import org.openas2.processor.ProcessorModule;
import org.openas2.processor.msgtracking.DbTrackingModule;
import org.openas2.processor.msgtracking.TrackingModule;
import org.openas2.processor.receiver.AS2ReceiverModule;
import org.openas2.util.DispositionType;

import java.io.*;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class MessageFileModule extends BaseStorageModule {
    public static final String PARAM_HEADER = "header";

    private Log logger = LogFactory.getLog(MessageFileModule.class.getSimpleName());


    public void handle(String action, Message msg, Map<String, Object> options) throws OpenAS2Exception {
        // store message content
        try {
            // Check if the location to store the received message is specified in the partnership
            String store_message_to = (String)options.get(Partnership.PA_STORE_RECEIVED_FILE_TO);
            if (store_message_to == null) {
                // Fetch the global storage string
                store_message_to = getParameter(PARAM_FILENAME, true);
            }
            File msgFile = getFile(msg, store_message_to, action);
            InputStream in = msg.getData().getInputStream();
            store(msgFile, in);

            //String fileContent = new String(Files.readAllBytes(msgFile.toPath()), StandardCharsets.UTF_8);
            in.reset();
            StringBuilder fileContentBuilder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    fileContentBuilder.append(line).append("\n");
                }
            }
            logger.debug("msg.getMessageID()="+msg.getMessageID());
            logger.debug("msg.getLogMsg()="+msg.getLogMsg());
            logger.debug(fileContentBuilder.toString());

            ///dbtracking update the message entry with the payload


            // Convert StringBuilder to String
            String fileContent = fileContentBuilder.toString();

            List<ProcessorModule> mpl = getSession().getProcessor().getModulesSupportingAction(TrackingModule.DO_TRACK_MSG);

            DbTrackingModule db = (DbTrackingModule) mpl.get(0);

            db.setParameter("msg_id",msg.getMessageID());
            db.setParameter("payload",fileContent);
            db.persistPayload(msg.getMessageID(),fileContent);
            logger.info("content persisted for msg_id="+msg.getMessageID());
            // Log the content of msgFile
           // logger.debug("stored message to " + msgFile.getAbsolutePath() + msg.getLogMsgID() + ". Content: " + fileContent);


        } catch (Exception e) {
            throw new DispositionException(new DispositionType("automatic-action", "MDN-sent-automatically", "processed", "Error", "Error storing transaction"), AS2ReceiverModule.DISP_STORAGE_FAILED, e);
        }

        String headerFilename = getParameter(PARAM_HEADER, false);

        if (headerFilename != null) {
            try {
                File headerFile = getFile(msg, headerFilename, action);
                InputStream in = getHeaderStream(msg);
                store(headerFile, in);
                logger.info("stored headers to " + headerFile.getAbsolutePath() + msg.getLogMsgID());
            } catch (IOException ioe) {
                throw new WrappedException(ioe);
            }
        }
    }

    /** TODO: Remove this when module config enforces setting the action so that the super method does all the work
    *
    */
    public String getModuleAction() {
        String action = super.getModuleAction();
        if (action == null) {
            return DO_STORE;
        }
        return action;
    }


    /**
     * @since 2007-06-01
     */
    protected String getFilename(Message msg, String fileParam, String action) throws InvalidParameterException {
        CompositeParameters compParams = new CompositeParameters(false).add("date", new DateParameters()).add("msg", new MessageParameters(msg)).add("rand", new RandomParameters());

        return ParameterParser.parse(fileParam, compParams);
    }

    protected InputStream getHeaderStream(Message msg) throws IOException {
        StringBuffer headerBuf = new StringBuffer();

        // write headers to the string buffer
        headerBuf.append("Headers:" + System.getProperty("line.separator"));

        Enumeration<String> headers = msg.getHeaders().getAllHeaderLines();
        String header;

        while (headers.hasMoreElements()) {
            header = headers.nextElement();
            headerBuf.append(header).append(System.getProperty("line.separator"));
        }

        headerBuf.append(System.getProperty("line.separator"));

        // write attributes to the string buffer
        headerBuf.append("Attributes:" + System.getProperty("line.separator"));

        Iterator<Map.Entry<String, String>> attrIt = msg.getAttributes().entrySet().iterator();
        Map.Entry<String, String> attrEntry;

        while (attrIt.hasNext()) {
            attrEntry = attrIt.next();
            headerBuf.append(attrEntry.getKey()).append(": ");
            headerBuf.append(attrEntry.getValue()).append(System.getProperty("line.separator"));
        }

        return new ByteArrayInputStream(headerBuf.toString().getBytes());
    }
}
