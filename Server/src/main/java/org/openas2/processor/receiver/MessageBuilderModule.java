package org.openas2.processor.receiver;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.WrappedException;
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

import javax.activation.DataHandler;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
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


    protected Message processDocument(InputStream ip, String filename) throws OpenAS2Exception, FileNotFoundException {
        Message msg = buildMessageMetadata(filename);

        String pendingFile = msg.getAttribute(FileAttribute.MA_PENDINGFILE);
        // Persist the file that has been passed in
        File doc = new File(pendingFile);
        FileOutputStream fo = null;
        try {
            fo = new FileOutputStream(doc);
        } catch (FileNotFoundException e1) {
            throw new OpenAS2Exception("Could not create file in pending folder: " + pendingFile, e1);
        }
        try {
            IOUtils.copy(ip, fo);
        } catch (IOException e1) {
            fo = null;
            throw new OpenAS2Exception("Could not write file to pending folder: " + pendingFile, e1);
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

        FileInputStream fis = new FileInputStream(doc);
        try {
            buildMessageData(msg, fis, filename);
        } finally {
            try {
                fis.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            fis = null;
            doc = null;
        }
        String customHeaderList = msg.getPartnership().getAttribute(Partnership.PA_CUSTOM_MIME_HEADER_NAMES_FROM_FILENAME);
        if (customHeaderList != null && customHeaderList.length() > 0) {
            String[] headerNames = customHeaderList.split("\\s*,\\s*");
            String delimiters = msg.getPartnership().getAttribute(Partnership.PA_CUSTOM_MIME_HEADER_NAME_DELIMITERS_IN_FILENAME);
            if (logger.isTraceEnabled()) {
                logger.trace("Adding custom headers based on message file name to custom headers map. Delimeters: " + delimiters + msg.getLogMsgID());
            }
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
            logger.info("File assigned to message: " + filename + msg.getLogMsgID());
        }

        if (msg.getData() == null) {
            throw new InvalidMessageException("Failed to retrieve data for outbound AS2 message for file: " + filename);
        }
        if (logger.isTraceEnabled()) {
            logger.trace("PARTNERSHIP parms: " + msg.getPartnership().getAttributes() + msg.getLogMsgID());
        }
        // Retry count - first try on partnership then directory polling module
        String maxRetryCnt = msg.getPartnership().getAttribute(Partnership.PA_RESEND_MAX_RETRIES);
        if (maxRetryCnt == null || maxRetryCnt.length() < 1) {
            maxRetryCnt = getSession().getProcessor().getParameters().get(PARAM_RESEND_MAX_RETRIES);
        }
        if (logger.isTraceEnabled()) {
            logger.trace("RESEND COUNT extracted from config: " + maxRetryCnt + msg.getLogMsgID());
        }
        Map<Object, Object> options = msg.getOptions();
        options.put(ResenderModule.OPTION_RETRIES, maxRetryCnt);

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
        } catch (Exception e) {
            msg.setLogMsg("Fatal error sending message: " + org.openas2.logging.Log.getExceptionMsg(e));
            logger.error(msg, e);
            AS2Util.cleanupFiles(msg, true);
        }
        return msg;

    }

    protected abstract Message createMessage();

    public Message buildMessageMetadata(String filename) throws OpenAS2Exception {
        Message msg = createMessage();
        msg.setAttribute(FileAttribute.MA_FILENAME, filename);
        msg.setPayloadFilename(filename);
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
            String delimiters = getParameter(PARAM_DELIMITERS, ".-");
            String mergeExtra = getParameter(PARAM_MERGE_EXTRA, "false");
            boolean mergeExtraTokens = "true".equalsIgnoreCase(mergeExtra);
            params.setParameters(format, delimiters, filename, mergeExtraTokens);
        }

        // Should have sender/receiver now so update the message's partnership with any
        // stored information based on the identified partner IDs
        getSession().getPartnershipFactory().updatePartnership(msg, true);
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
        return msg;

    }

    public void buildMessageData(Message msg, InputStream ip, String filename) throws OpenAS2Exception {
        MessageParameters params = new MessageParameters(msg);

        try {
            // byte[] data = IOUtilOld.getFileBytes(file);
            // Allow Content-Type to be overridden at partnership level or as property
            String contentType = msg.getPartnership().getAttributeOrProperty(Partnership.PA_CONTENT_TYPE, null);
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
            javax.mail.util.ByteArrayDataSource byteSource = new javax.mail.util.ByteArrayDataSource(ip, contentType);
            MimeBodyPart body = new MimeBodyPart();
            body.setDataHandler(new DataHandler(byteSource));

            // below statement is not filename related, just want to make it
            // consist with the parameter "mimetype="application/EDI-X12""
            // defined in config.xml 2007-06-01

            body.setHeader("Content-Type", contentType);

            // add below statement will tell the receiver to save the filename
            // as the one sent by sender. 2007-06-01
            String sendFileName = getParameter("sendfilename", false);
            if (sendFileName != null && sendFileName.equals("true")) {
                String contentDisposition = "Attachment; filename=\"" + msg.getAttribute(FileAttribute.MA_FILENAME) + "\"";
                body.setHeader("Content-Disposition", contentDisposition);
                msg.setContentDisposition(contentDisposition);
            }

            msg.setData(body);
        } catch (MessagingException me) {
            throw new WrappedException(me);
        } catch (IOException ioe) {
            throw new WrappedException(ioe);
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
                msg.getData().setHeader("Content-Transfer-Encoding", contentTxfrEncoding);
            } catch (MessagingException e) {
                throw new OpenAS2Exception("Failed to set content transfer encoding in created MimeBodyPart: " + org.openas2.logging.Log.getExceptionMsg(e), e);
            }
        }
    }
}
