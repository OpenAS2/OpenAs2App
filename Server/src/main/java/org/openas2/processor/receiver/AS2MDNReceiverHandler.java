package org.openas2.processor.receiver;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.message.AS2Message;
import org.openas2.message.AS2MessageMDN;
import org.openas2.message.Message;
import org.openas2.message.MessageMDN;
import org.openas2.partner.Partnership;
import org.openas2.util.AS2Util;
import org.openas2.util.ByteArrayDataSource;
import org.openas2.util.HTTPUtil;

import javax.activation.DataHandler;
import javax.mail.internet.ContentType;
import javax.mail.internet.MimeBodyPart;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Socket;

public class AS2MDNReceiverHandler implements NetModuleHandler {
    private AS2MDNReceiverModule module;

    private Log logger = LogFactory.getLog(AS2MDNReceiverHandler.class.getSimpleName());


    public AS2MDNReceiverHandler(AS2MDNReceiverModule module) {
        super();
        this.module = module;
    }

    public String getClientInfo(Socket s) {
        return " " + s.getInetAddress().getHostAddress() + " " + s.getPort();
    }

    public AS2MDNReceiverModule getModule() {
        return module;
    }

    public void handle(NetModule owner, Socket s) {

        if (logger.isInfoEnabled()) {
            logger.info("incoming connection" + " [" + getClientInfo(s) + "]");
        }

        AS2Message msg = new AS2Message();


        msg.setOption("DIRECTION", "SEND");

        byte[] data = null;

        // Read in the message request, headers, and data
        try {
            data = HTTPUtil.readData(s.getInputStream(), s.getOutputStream(), msg);
            if (data == null) {
                if ("true".equalsIgnoreCase(msg.getAttribute("isHealthCheck"))) {
                    if (logger.isInfoEnabled()) {
                        logger.info("Healthcheck ping detected" + " [" + getClientInfo(s) + "]" + msg.getLogMsgID());
                    }
                    return;
                } else {
                    try {
                        HTTPUtil.sendHTTPResponse(s.getOutputStream(), HttpURLConnection.HTTP_BAD_REQUEST, null);
                    } catch (IOException e1) {
                    }
                    OpenAS2Exception oe = new OpenAS2Exception("Missing data in MDN response message");
                    msg.setLogMsg("Error receiving asynchronous MDN. There is no data.");
                    logger.error(msg, oe);
                    return;
                }
            }
            // check if the requested URL is defined in attribute "as2_receipt_option"
            // in one of partnerships, if yes, then process incoming AsyncMDN
            if (logger.isInfoEnabled()) {
                logger.info("incoming connection for receiving AsyncMDN" + " [" + getClientInfo(s) + "]" + msg.getLogMsgID());
            }
            if (logger.isTraceEnabled()) {
                logger.trace("Incoming ASYNC MDN message - Message struct: " + msg.toString());
            }
            ContentType receivedContentType;

            MimeBodyPart receivedPart = new MimeBodyPart(msg.getHeaders(), data);
            receivedContentType = new ContentType(receivedPart.getContentType());

            // MimeBodyPart receivedPart = new MimeBodyPart();
            receivedPart.setDataHandler(new DataHandler(new ByteArrayDataSource(data, receivedContentType.toString(), null)));
            receivedPart.setHeader("Content-Type", receivedContentType.toString());

            msg.setData(receivedPart);

            // Switch the msg headers since the original message went in the opposite direction
            String to = msg.getHeader("AS2-To");
            msg.setHeader("AS2-To", msg.getHeader("AS2-From"));
            msg.setHeader("AS2-From", to);
            msg.getPartnership().setSenderID(Partnership.PID_AS2, msg.getHeader("AS2-From"));
            msg.getPartnership().setReceiverID(Partnership.PID_AS2, msg.getHeader("AS2-To"));
            getModule().getSession().getPartnershipFactory().updatePartnership(msg, true);

            // Create a MessageMDN
            MessageMDN mdn = new AS2MessageMDN(msg, true);

            if (logger.isTraceEnabled()) {
                logger.trace("Incoming ASYNC MDN message - MDN struct: " + mdn.toString());
            }
            /*
            // Log significant msg state
            options.put("STATE", Message.MSG_STATE_MDN_RECEIVE_START);
            options.put("STATE_MSG", "MDN response received. Message processing started.");
            msg.trackMsgState(getModule().getSession(), options);
            */
            AS2Util.processMDN(msg, data, s.getOutputStream(), true, getModule().getSession(), this);
            // Log significant msg state
            msg.setOption("STATE", Message.MSG_STATE_MSG_SENT_MDN_RECEIVED_OK);
            msg.trackMsgState(getModule().getSession());

        } catch (Exception e) {
            if (Message.MSG_STATUS_MDN_PROCESS_INIT.equals(msg.getStatus()) || Message.MSG_STATUS_MDN_PARSE.equals(msg.getStatus()) || !(e instanceof OpenAS2Exception)) {
                /*
                 * Cannot identify the target if in init or parse state so not sure what the
                 * best course of action is apart from do nothing
                 */
                try {
                    HTTPUtil.sendHTTPResponse(s.getOutputStream(), HttpURLConnection.HTTP_BAD_REQUEST, null);
                } catch (IOException e1) {
                }
                msg.setLogMsg("Unhandled error condition receiving asynchronous MDN. Message and asociated files cleanup will be attempted but may be in an unknown state.");
                logger.error(msg, e);
            } else {
                /*
                 * Most likely a resend abort of max resend reached if
                 * OpenAS2Exception so do not log as should have been logged
                 * upstream ... just clean up the mess
                 */
                // Must have received MDN successfully so must respond with OK
                try {
                    HTTPUtil.sendHTTPResponse(s.getOutputStream(), HttpURLConnection.HTTP_OK, null);
                } catch (IOException e1) { // What to do ....
                }
                msg.setLogMsg("Exception receiving asynchronous MDN. Message and asociated files cleanup will be attempted but may be in an unknown state.");
                logger.error(msg, e);

            }
            // Log significant msg state
            msg.setOption("STATE", Message.MSG_STATE_SEND_FAIL);
            msg.trackMsgState(getModule().getSession());
            AS2Util.cleanupFiles(msg, true);
        }

    }
}
