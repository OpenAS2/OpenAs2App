package org.openas2.processor.receiver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.message.AS2Message;
import org.openas2.message.AS2MessageMDN;
import org.openas2.message.Message;
import org.openas2.message.MessageMDN;
import org.openas2.partner.Partnership;
import org.openas2.processor.msgtracking.BaseMsgTrackingModule.FIELDS;
import org.openas2.util.AS2Util;
import org.openas2.util.HTTPUtil;

import jakarta.mail.internet.MimeBodyPart;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Socket;

public class AS2MDNReceiverHandler implements NetModuleHandler {
    private AS2MDNReceiverModule module;

    private Logger logger = LoggerFactory.getLogger(AS2MDNReceiverHandler.class);


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
            logger.info("Incoming connection" + " [" + getClientInfo(s) + "]");
        }

        AS2Message msg = new AS2Message();


        msg.setOption(FIELDS.DIRECTION, "SEND");

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
                    msg.setLogMsg("Error receiving asynchronous MDN. There is no data in the receivd MDN: " +  getClientInfo(s) + ":: " + msg.getLogMsgID());
                    logger.error(msg.getLogMsg());
                    return;
                }
            }
            if (logger.isInfoEnabled()) {
                logger.info("incoming connection for receiving AsyncMDN" + " [" + getClientInfo(s) + "]" + msg.getLogMsgID());
            }
            if (logger.isTraceEnabled()) {
                logger.trace("Incoming ASYNC MDN message - Message struct: " + msg.toString());
            }
            MimeBodyPart receivedPart = new MimeBodyPart(msg.getHeaders(), data);
            // ContentType receivedContentType = new ContentType(receivedPart.getContentType());
            msg.setData(receivedPart);

            /* Switch the msg headers for To and From to standardise processing for SYNC and ASYNC MDN
             * since the original message this MDN is responding to went in the opposite direction
             */
            String to = msg.getHeader("AS2-To");
            msg.setHeader("AS2-To", msg.getHeader("AS2-From"));
            msg.setHeader("AS2-From", to);
            msg.getPartnership().setSenderID(Partnership.PID_AS2, msg.getHeader("AS2-From"));
            msg.getPartnership().setReceiverID(Partnership.PID_AS2, msg.getHeader("AS2-To"));
            try {
                getModule().getSession().getPartnershipFactory().updatePartnership(msg, true);
            } catch (OpenAS2Exception e) {
                // Partnership not found so log and exit
                try {
                    HTTPUtil.sendHTTPResponse(s.getOutputStream(), HttpURLConnection.HTTP_BAD_REQUEST, null);
                } catch (IOException e1) {
                }
                if (logger.isInfoEnabled()) {
                    logger.info("Partnership lookup failed for MDN received from: " + msg.getHeader("AS2-To")
                        + "  MDN is targeting partner: " + msg.getHeader("AS2-From"));
                }
                return;
            }

            // Create a MessageMDN
            MessageMDN mdn = new AS2MessageMDN(msg, true);

            if (logger.isTraceEnabled()) {
                logger.trace("Incoming ASYNC MDN message - MDN struct: " + mdn.toString());
            }
            try {
                boolean mdnResponseIssue = AS2Util.processMDN(msg, data, s.getOutputStream(), true, getModule().getSession(), this.getClass());
                // Assume that appropriate logging and state handling was done upstream if an error occurred so only log state change for success
                if (!mdnResponseIssue) {
                    // Log state
                    msg.setOption("STATE", Message.MSG_STATE_MSG_SENT_MDN_RECEIVED_OK);
                    msg.trackMsgState(getModule().getSession());
                }
            } catch (Exception e) {
                /* Processing of the MDN would have done extensive error handling so only log an error if the error
                 * is an not OpenAS2 custom error.
                 */
                if (!(e instanceof OpenAS2Exception)){
                    /*
                     * Something unexpected (assumes a resend was not successfully initiated)
                     */
                    msg.setLogMsg("Unhandled error condition processing synchronous MDN. Message and associated files cleanup will be attempted but may be in an unknown state.");
                    logger.error(msg.getLogMsg(), e);
                }
                // Logger significant msg state
                msg.setOption("STATE", Message.MSG_STATE_MSG_SENT_MDN_PROCESSING_ERROR);
                msg.trackMsgState(getModule().getSession());
                AS2Util.cleanupFiles(msg, true);
            }

        } catch (Exception e) {
                msg.setLogMsg("Unhandled error condition receiving asynchronous MDN. Processing will be aborted.");
                logger.error(msg.getLogMsg(), e);
                try {
                    HTTPUtil.sendHTTPResponse(s.getOutputStream(), HttpURLConnection.HTTP_BAD_REQUEST, null);
                } catch (IOException e1) {
                }
        }

    }
}
