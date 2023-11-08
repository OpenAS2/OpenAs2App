package org.openas2.processor.receiver;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.WrappedException;
import org.openas2.lib.util.MimeUtil;
import org.openas2.message.FileAttribute;
import org.openas2.message.InvalidMessageException;
import org.openas2.message.Message;
import org.openas2.params.CompositeParameters;
import org.openas2.params.DateParameters;
import org.openas2.params.InvalidParameterException;
import org.openas2.params.MessageParameters;
import org.openas2.params.ParameterParser;
import org.openas2.params.RandomParameters;
import org.openas2.partner.Partnership;
import org.openas2.processor.resender.ResenderModule;
import org.openas2.processor.sender.SenderModule;
import org.openas2.util.AS2Util;
import org.openas2.util.FileUtil;
import org.openas2.util.IOUtil;
import org.openas2.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class MessageBuilderModule extends BaseReceiverModule {

    public static final String PARAM_ERROR_DIRECTORY = "errordir";
    public static final String PARAM_ERROR_FILENAME = "stored_error_filename";
    public static final String PARAM_SENT_DIRECTORY = "sentdir";
    public static final String PARAM_SENT_FILENAME = "stored_sent_filename";

    public static final String PARAM_FORMAT = "format";
    public static final String PARAM_DELIMITERS = "delimiters";
    public static final String PARAM_MERGE_EXTRA = "mergeextratokens";
    public static final String PARAM_DEFAULTS = "defaults";
    public static final String PARAM_MIMETYPE = "mimetype";
    public static final String PARAM_RESEND_MAX_RETRIES = "resend_max_retries";

    private Log logger = LogFactory.getLog(MessageBuilderModule.class.getSimpleName());

    public void init(Session session, Map<String, String> options) throws OpenAS2Exception {
        super.init(session, options);
    }

    protected CompositeParameters createParser(Message msg) {
        return new CompositeParameters(false).add("date", new DateParameters()).add("rand", new RandomParameters()).add("msg", new MessageParameters(msg));
    }


    /**
     * Move the file into the processing folder then invoke the sending process.
     * This method supports splitting files if configured to do so
     * @param fileToSend
     * @param filename
     * @return
     * @throws OpenAS2Exception
     * @throws FileNotFoundException
     */
    protected Message processDocument(File fileToSend, String filename) throws OpenAS2Exception, FileNotFoundException {       
        Message msg = buildBaseMessage(filename);
        String fileSizeThresholdStr = msg.getPartnership().getAttribute(Partnership.PA_SPLIT_FILE_THRESHOLD_SIZE_IN_BYTES);
        long fileSizeThreshold = 0;
        if (fileSizeThresholdStr != null && fileSizeThresholdStr.length() > 0) {
            fileSizeThreshold = Long.parseLong(fileSizeThresholdStr);
        }
        if (fileSizeThreshold > 0 && fileToSend.length() > fileSizeThreshold) {
            String newFileNamePrefix = msg.getPartnership().getAttribute(Partnership.PA_SPLIT_FILE_NAME_PREFIX);
            if (newFileNamePrefix == null) {
                newFileNamePrefix = "";
            }
            boolean containsHeaderRow = "true".equals(msg.getPartnership().getAttribute(Partnership.PA_SPLIT_FILE_CONTAINS_HEADER_ROW));
            String preprocessDir = Properties.getProperty("storageBaseDir", fileToSend.getParent()) + File.separator + "preprocess";
            // Move the file to a holding folder so it is not processed by the directory poller anymore
            String movedFilePath = preprocessDir + File.separator + filename;
            File movedFile = new File(movedFilePath);
            try {
                IOUtil.moveFile(fileToSend, movedFile, false);
            } catch (IOException e1) {
                throw new OpenAS2Exception("Failed to move file for split processing: " + fileToSend.getAbsolutePath(), e1);
            }
            FileSplitter fileSplitter = new FileSplitter(movedFile, fileToSend.getParent(), fileSizeThreshold, containsHeaderRow, filename, newFileNamePrefix);
            new Thread(fileSplitter).start();
            return null;
        } else {
            addMessageMetadata(msg, filename);
            File pendingFile = new File(msg.getAttribute(FileAttribute.MA_PENDINGFILE));
            try {
                IOUtil.moveFile(fileToSend, pendingFile, false);
            } catch (IOException e) {
                logger.error(": " + e.getMessage(), e);
                throw new OpenAS2Exception("Failed to move the inbound file " + fileToSend.getPath() + " to the processing location " + pendingFile.getName());
            }
            // Update the message's partnership with any additional attributes since initial call in case dynamic variables were not set initially
            getSession().getPartnershipFactory().updatePartnership(msg, true);
            return processDocument(pendingFile, msg); 
        }
    }

    /**
     * Take the file input stream and write it to a file system file in the processing folder. 
     * Use this method if the file is produced in real time through a stream.
     * @param ip
     * @param filename
     * @return
     * @throws OpenAS2Exception
     * @throws FileNotFoundException
     */
    protected Message processDocument(InputStream ip, String filename) throws OpenAS2Exception, FileNotFoundException {
        Message msg = buildBaseMessage(filename);
        addMessageMetadata(msg, filename);
        File pendingFile = new File(msg.getAttribute(FileAttribute.MA_PENDINGFILE));
        FileOutputStream fo = null;
        try {
            fo = new FileOutputStream(pendingFile);
        } catch (FileNotFoundException e1) {
            throw new OpenAS2Exception("Could not create file in pending folder: " + pendingFile.getName(), e1);
        }
        try {
            IOUtils.copy(ip, fo);
        } catch (IOException e1) {
            fo = null;
            throw new OpenAS2Exception("Could not write file to pending folder: " + pendingFile.getName(), e1);
        }
        try {
            ip.close();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        ip = null;
        try {
            fo.close();
        } catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        fo = null;
        return processDocument(pendingFile, msg);
    }

    protected Message processDocument(File pendingFile, Message msg) throws OpenAS2Exception, FileNotFoundException {
        buildMessageData(msg, pendingFile, null);
        String customHeaderList = msg.getPartnership().getAttribute(Partnership.PA_CUSTOM_MIME_HEADER_NAMES_FROM_FILENAME);
        if (customHeaderList != null && customHeaderList.length() > 0) {
            String[] headerNames = customHeaderList.split("\\s*,\\s*");
            String delimiters = msg.getPartnership().getAttribute(Partnership.PA_CUSTOM_MIME_HEADER_NAME_DELIMITERS_IN_FILENAME);
            if (logger.isTraceEnabled()) {
                logger.trace("Adding custom headers based on message file name to custom headers map. Delimeters: " + delimiters + msg.getLogMsgID());
            }
            String filename = msg.getAttribute(FileAttribute.MA_FILENAME);
            if (delimiters != null) {
                // Extract the values based on delimiters which means the mime header names are
                // prefixed with a target
                StringTokenizer valueTokens = new StringTokenizer(filename, delimiters, false);
                if (valueTokens != null && valueTokens.countTokens() != headerNames.length) {
                    msg.setLogMsg("Filename does not match headers list: Headers=" + customHeaderList + " ::: Filename=" + filename + " ::: String delimiters=" + delimiters);
                    logger.error(msg);
                    throw new OpenAS2Exception("Invalid filename for extracting custom headers: " + filename);
                }
                for (int i = 0; i < headerNames.length; i++) {
                    String[] header = headerNames[i].split("\\.");
                    if (logger.isTraceEnabled()) {
                        logger.trace("Adding custom header: " + headerNames[i] + " :::Split count:" + header.length + msg.getLogMsgID());
                    }
                    if (header.length != 2) {
                        throw new OpenAS2Exception("Invalid custom header: " + headerNames[i] + "  :: The header name must be prefixed by \"header.\" or \"junk.\" etc");
                    }
                    if (!"header".equalsIgnoreCase(header[0])) {
                        continue; // Ignore anything not prefixed by "header"
                    }
                    msg.addCustomOuterMimeHeader(header[1], valueTokens.nextToken());
                }
            } else {
                String regex = msg.getPartnership().getAttribute(Partnership.PA_CUSTOM_MIME_HEADER_NAMES_REGEX_ON_FILENAME);
                if (regex != null) {
                    Pattern p = Pattern.compile(regex);
                    Matcher m = p.matcher(filename);
                    if (!m.find() || m.groupCount() != headerNames.length) {
                        msg.setLogMsg("Could not match filename to headers required using the regex provided: " + (m.find() ? ("Mismatch in header count to extracted group count: " + headerNames.length + "::" + m.groupCount()) : "No match found in filename"));
                        logger.error(msg);
                        throw new OpenAS2Exception("Invalid filename for extracting custom headers: " + filename);
                    }
                    for (int i = 0; i < headerNames.length; i++) {
                        msg.addCustomOuterMimeHeader(headerNames[i], m.group(i + 1));
                    }
                }
            }
        }
        if (logger.isInfoEnabled()) {
            logger.info("File assigned to message: " + pendingFile.getName() + msg.getLogMsgID());
        }

        if (msg.getData() == null) {
            throw new InvalidMessageException("Failed to retrieve data for outbound AS2 message for file: " + pendingFile.getName());
        }
        if (logger.isTraceEnabled()) {
            logger.trace("PARTNERSHIP parms: " + msg.getPartnership().getAttributes() + msg.getLogMsgID());
        }
        Map<String, Object> options = new HashMap<String, Object>();
        // Initialise the resend parameters
        int maxResendCount = AS2Util.getMaxResendCount(getSession(), msg);
        msg.setOption(ResenderModule.OPTION_MAX_RETRY_COUNT, maxResendCount);
        msg.setOption(ResenderModule.OPTION_RETRIES, "0");
        if (logger.isTraceEnabled()) {
            try {

                logger.trace("Message object in directory polling module. Content-Disposition: " + msg.getContentDisposition() + "\n      Content-Type : " + msg.getContentType() + "\n      HEADERS : " + AS2Util.printHeaders(msg.getData().getAllHeaders()) + "\n      Content-Disposition in MSG getData() MIMEPART: " + msg.getData().getContentType() + msg.getLogMsgID());
            } catch (Exception e) {
            }
        }
        try {
            msg.setStatus(Message.MSG_STATUS_MSG_SEND);
            // Transmit the message
            getSession().getProcessor().handle(SenderModule.DO_SEND, msg, options);
            if (!msg.isConfiguredForAsynchMDN()) {
                AS2Util.cleanupFiles(msg, false);
            }
        } catch (Exception e) {
            msg.setLogMsg("Fatal error sending message: " + org.openas2.logging.Log.getExceptionMsg(e));
            logger.error(msg, e);
            AS2Util.cleanupFiles(msg, true);
        }
        return msg;

    }

    protected abstract Message createMessage();

    /**
     * Creates a Message object and sets up the sender and receiver to identify the partnership.
     * @param filename - the name of the file to be processed.
     *                   Only used if the poller is a filename based poller to identify sender and receiver.
     * @return - the Message object
     * @throws OpenAS2Exception
     */
    public Message buildBaseMessage(String filename) throws OpenAS2Exception {
        Message msg = createMessage();
        MessageParameters params = new MessageParameters(msg);

        // Get the parameter that should provide the link between the polled directory
        // and an AS2 sender and recipient
        String defaults = getParameter(PARAM_DEFAULTS, false);
        // Link the file to an AS2 sender and recipient via the Message object
        // associated with the file
        if (defaults != null) {
            params.setParameters(defaults);
        }
        String format = getParameter(PARAM_FORMAT, false);
        if (format != null) {
            // Must be a poller that contains the AS2 ID's plus other meta data in the source filename
            String delimiters = getParameter(PARAM_DELIMITERS, ".-");
            String mergeExtra = getParameter(PARAM_MERGE_EXTRA, "false");
            boolean mergeExtraTokens = "true".equalsIgnoreCase(mergeExtra);
            params.setParameters(format, delimiters, filename, mergeExtraTokens);
        }
        // Should have sender/receiver now so update the message's partnership with any
        // stored information based on the identified partner IDs
        getSession().getPartnershipFactory().updatePartnership(msg, true);
        return msg;
    }

    public void addMessageMetadata(Message msg, String filename) throws OpenAS2Exception {
        msg.setAttribute(FileAttribute.MA_FILENAME, filename);
        msg.setPayloadFilename(filename);
        // Set the filename extension if it has one
        msg.setAttribute(FileAttribute.MA_FILENAME_EXTENSION, FileUtil.getFilenameExtension(filename));
        // Set a new message ID
        msg.updateMessageID();
        // Set the sender and receiver in the Message object headers
        msg.setHeader("AS2-To", msg.getPartnership().getReceiverID(Partnership.PID_AS2));
        msg.setHeader("AS2-From", msg.getPartnership().getSenderID(Partnership.PID_AS2));
        // Now build the filename since it is by default dependent on having sender and
        // receiver ID
        String pendingFile = AS2Util.buildPendingFileName(msg, getSession().getProcessor(), "pendingmdn");
        msg.setAttribute(FileAttribute.MA_PENDINGFILE, pendingFile);
        CompositeParameters parser = new CompositeParameters(false).add("date", new DateParameters()).add("msg", new MessageParameters(msg)).add("rand", new RandomParameters());
        msg.setAttribute(FileAttribute.MA_ERROR_DIR, ParameterParser.parse(getParameter(PARAM_ERROR_DIRECTORY, true), parser));
        msg.setAttribute(FileAttribute.MA_ERROR_FILENAME, getParameter(PARAM_ERROR_FILENAME, false));
        if (getParameter(PARAM_SENT_DIRECTORY, false) != null) {
            msg.setAttribute(FileAttribute.MA_SENT_DIR, ParameterParser.parse(getParameter(PARAM_SENT_DIRECTORY, false), parser));
            msg.setAttribute(FileAttribute.MA_SENT_FILENAME, getParameter(PARAM_SENT_FILENAME, false));
        }
    }

    /**
     * Provides support for a random InputStream. 
     *     NOTE: This method should not be used for very large files as it will consume all the available heap and fail to send.
     * @param msg - the AS2 message structure that will be formulated into an AS2 HTTP message.
     * @param ip - the generic inputstream
     * @param filename - name of the file being sent (currently unused)
     * @throws OpenAS2Exception

     */
    public void buildMessageData(Message msg, InputStream ip, String filename) throws OpenAS2Exception {
            String contentType = getMessageContentType(msg);
            javax.mail.util.ByteArrayDataSource byteSource;
            try {
                byteSource = new javax.mail.util.ByteArrayDataSource(ip, contentType);
            } catch (IOException e) {
                throw new OpenAS2Exception("Failed to set up datasource from input stream: " + e.getMessage(), e);
            }
            buildMessageData(msg, byteSource, contentType);

    }

    /**
     * This method will minimise the memory usage when creating the MimeBodyPart
     * @param msg - the AS2 message structure that will be formulated into an AS2 HTTP message.
     * @param fileObject - a File object that will provide the file content
     * @param contentType - the Content-Type for the sent data - can be null and fall back to the OpenAS2 default
     * @throws OpenAS2Exception
     */
    public void buildMessageData(Message msg, File fileObject, String contentType) throws OpenAS2Exception {
        buildMessageData(msg, new FileDataSource(fileObject), contentType);
    }

    /**
     * This method will minimise the memory usage when creating the MimeBodyPart
     * @param msg - the AS2 message structure that will be formulated into an AS2 HTTP message.
     * @param dataSource - a DatsSource object that will provide the file content
     * @param contentType - the Content-Type for the sent data - can be null and fall back to the OpenAS2 default
     * @throws OpenAS2Exception
     */
    public void buildMessageData(Message msg, DataSource dataSource, String contentType) throws OpenAS2Exception {
        if (contentType == null) {
            contentType = getMessageContentType(msg);
        }
        MimeBodyPart body = new MimeBodyPart();
        try {
            body.setDataHandler(new DataHandler(dataSource));
            body.setHeader(MimeUtil.MIME_CONTENT_TYPE_KEY, contentType);
        } catch (MessagingException e) {
            throw new OpenAS2Exception("Failed to set properties on mime body part: " + e.getMessage(), e);
        }
        setAdditionalMetaData(msg, body);
        msg.setData(body);
    }

    public String getMessageContentType(Message msg) throws OpenAS2Exception {
        MessageParameters params = new MessageParameters(msg);

        // Allow Content-Type to be overridden at partnership level or as property
        String contentType = msg.getPartnership().getAttributeOrProperty(Partnership.PA_CONTENT_TYPE, null);
        // The content type could be determined dynamically based on filename extension
        if (msg.getPartnership().isUseDynamicContentTypeLookup()) {
            String fileExtension = msg.getAttribute(FileAttribute.MA_FILENAME_EXTENSION);
            if (fileExtension != null) {
                String dynamicContentType = msg.getPartnership().getContentTypeFromFileExtension(fileExtension);
                if (dynamicContentType != null) {
                    // Dynamic override found so use it
                    contentType = dynamicContentType;
                }
            }
        }
        if (contentType == null) {
            contentType = getParameter(PARAM_MIMETYPE, false);
        }
        if (contentType == null) {
            contentType = "application/octet-stream";
        } else {
            try {
                contentType = ParameterParser.parse(contentType, params);
            } catch (InvalidParameterException e) {
                throw new OpenAS2Exception("Bad content-type" + contentType, e);
            }
        }
        return contentType;
    }

    private void setAdditionalMetaData(Message msg, MimeBodyPart mimeBodyPart) throws OpenAS2Exception {

        try {
            // add below statement will tell the receiver to save the filename
            // as the one sent by sender. 2007-06-01
            String sendFileName = getParameter("sendfilename", false);
            if (sendFileName != null && sendFileName.equals("true")) {
                String contentDisposition = "Attachment; filename=\"" + msg.getAttribute(FileAttribute.MA_FILENAME) + "\"";
                mimeBodyPart.setHeader("Content-Disposition", contentDisposition);
                msg.setContentDisposition(contentDisposition);
            }
            /*
             * Not sure it should be set at this level as there is no encoding of the
             * content at this point so make it configurable
             */
            if (msg.getPartnership().isSetTransferEncodingOnInitialBodyPart()) {
                String contentTxfrEncoding = msg.getPartnership().getAttribute(Partnership.PA_CONTENT_TRANSFER_ENCODING);
                if (contentTxfrEncoding == null) {
                    contentTxfrEncoding = Session.DEFAULT_CONTENT_TRANSFER_ENCODING;
                }
                try {
                    mimeBodyPart.setHeader("Content-Transfer-Encoding", contentTxfrEncoding);
                } catch (MessagingException e) {
                    throw new OpenAS2Exception("Failed to set content transfer encoding in created MimeBodyPart: " + org.openas2.logging.Log.getExceptionMsg(e), e);
                }
            }

        } catch (MessagingException me) {
            throw new WrappedException(me);
        }
    }
}
