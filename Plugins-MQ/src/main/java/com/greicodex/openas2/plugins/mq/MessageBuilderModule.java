package com.greicodex.openas2.plugins.mq;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.activation.DataHandler;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.WrappedException;
import org.openas2.message.FileAttribute;
import org.openas2.message.Message;
import org.openas2.params.CompositeParameters;
import org.openas2.params.DateParameters;
import org.openas2.params.InvalidParameterException;
import org.openas2.params.MessageParameters;
import org.openas2.params.ParameterParser;
import org.openas2.params.RandomParameters;
import org.openas2.partner.Partnership;
import org.openas2.processor.receiver.BaseReceiverModule;
import org.openas2.util.AS2Util;

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

    protected abstract Message createMessage();

    public Message buildMessageMetadata(Map<String, String> headers) throws OpenAS2Exception {
	Message msg = createMessage();
	msg.setAttribute(FileAttribute.MA_FILENAME, headers.getOrDefault(FileAttribute.MA_FILENAME, FileAttribute.MA_FILENAME));
	msg.setPayloadFilename(headers.getOrDefault(FileAttribute.MA_FILENAME, FileAttribute.MA_FILENAME));
	MessageParameters params = new MessageParameters(msg);

	// Get the parameter that should provide the link between the polled directory
	// and an AS2 sender and recipient
	String defaults = getParameter(PARAM_DEFAULTS, false);
	// Link the file to an AS2 sender and recipient via the Message object
	// associated with the file
	if (defaults != null) {
	    params.setParameters(defaults);
	}

	headers.forEach((t, u) -> {
            try {
                params.setParameter(t, u);
            } catch (InvalidParameterException ex) {
                Logger.getLogger(MessageBuilderModule.class.getName()).log(Level.SEVERE, null, ex);
            }
        });

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
	CompositeParameters parser = new CompositeParameters(false).add("date", new DateParameters())
		.add("msg", new MessageParameters(msg)).add("rand", new RandomParameters());
	msg.setAttribute(FileAttribute.MA_ERROR_DIR, ParameterParser.parse(getParameter(PARAM_ERROR_DIRECTORY, true), parser));
	msg.setAttribute(FileAttribute.MA_ERROR_FILENAME, getParameter(PARAM_ERROR_FILENAME, false));
	if (getParameter(PARAM_SENT_DIRECTORY, false) != null) {
	    msg.setAttribute(FileAttribute.MA_SENT_DIR, ParameterParser.parse(getParameter(PARAM_SENT_DIRECTORY, false), parser));
	    msg.setAttribute(FileAttribute.MA_SENT_FILENAME, getParameter(PARAM_SENT_FILENAME, false));
	}
	return msg;

    }

    public void buildMessageData(Message msg, InputStream inputData) throws OpenAS2Exception {
	MessageParameters params = new MessageParameters(msg);

	try {
            String contentType=msg.getAttribute(Partnership.PA_CONTENT_TYPE);

	    // Allow Content-Type to be overridden at partnership level or as property
	    if(contentType == null || contentType.length() == 0 ) {
                contentType = msg.getPartnership().getAttributeOrProperty(Partnership.PA_CONTENT_TYPE, null);
            }
	    if (contentType == null){
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
	    javax.mail.util.ByteArrayDataSource byteSource = new javax.mail.util.ByteArrayDataSource(inputData, contentType);
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
		String contentDisposition = "Attachment; filename=\"" + msg.getAttribute(FileAttribute.MA_FILENAME)
			+ "\"";
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
	    if (contentTxfrEncoding == null)
		contentTxfrEncoding = Session.DEFAULT_CONTENT_TRANSFER_ENCODING;
	    try {
		msg.getData().setHeader("Content-Transfer-Encoding", contentTxfrEncoding);
	    } catch (MessagingException e) {
		throw new OpenAS2Exception("Failed to set content transfer encoding in created MimeBodyPart: "
			+ org.openas2.logging.Log.getExceptionMsg(e), e);
	    }
	}
    }
}
