package org.openas2.processor.sender;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.http.protocol.HTTP;
import org.openas2.OpenAS2Exception;
import org.openas2.WrappedException;
import org.openas2.message.AS2Message;
import org.openas2.message.AS2MessageMDN;
import org.openas2.message.Message;
import org.openas2.message.MessageMDN;
import org.openas2.processor.msgtracking.BaseMsgTrackingModule.FIELDS;
import org.openas2.processor.resender.ResenderModule;
import org.openas2.processor.storage.StorageModule;
import org.openas2.util.AS2Util;
import org.openas2.util.DispositionType;
import org.openas2.util.HTTPUtil;
import org.openas2.util.ResponseWrapper;

import jakarta.mail.Header;
import jakarta.mail.internet.MimeBodyPart;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class MDNSenderModule extends HttpSenderModule {

    public static final String MDN_TYPE_OPT = "mdn-type-option";
    public static final String MDN_TYPE_VAL_SYNC = "Synchronous";
    public static final String MDN_TYPE_VAL_ASYNC = "Asynchronous";

    private Logger logger = LoggerFactory.getLogger(MDNSenderModule.class);

    /** TODO: Remove this when module config enforces setting the action so that the super method does all the work
    *
    */
   public String getModuleAction() {
       String action = super.getModuleAction();
       if (action == null) {
           return SenderModule.DO_SENDMDN;
       }
       return action;
   }

    public boolean canHandle(String action, Message msg, Map<String, Object> options) {
        if (!super.canHandle(action, msg, options)) {
            return false;
        }
        // So generally supports the action. Check if specifically for AS2 messages
        return (msg instanceof AS2Message);
    }

    public void handle(String action, Message msg, Map<String, Object> options) throws OpenAS2Exception {
        if (logger.isDebugEnabled()) {
            logger.debug("MDN sending started. Partner requested " + (msg.isRequestingAsynchMDN()?"ASYNC":"SYNC") + " mode for MDN response.");
        }
        if (options == null) {
            options = new HashMap<String, Object>();
        }
        options.put(FIELDS.DIRECTION, "RECEIVE");
        boolean isResend = Message.MSG_STATUS_MDN_RESEND.equals(msg.getStatus());
        options.put(FIELDS.IS_RESEND, isResend ? "Y" : "N");
        Object bos = options.get("buffered_output_stream");
        BufferedOutputStream httpOutputStream = bos==null?null:(BufferedOutputStream) bos;
        if (!isResend && httpOutputStream == null) {
            throw new OpenAS2Exception("MDN sender module did not receive the HTTP response stream on first invocation.");
        }
        MessageMDN mdn = msg.getMDN();
        DispositionType disposition = new DispositionType(mdn.getAttribute(AS2MessageMDN.MDNA_DISPOSITION));

        // if asyncMDN requested...
        if (msg.isRequestingAsynchMDN()) {
            // for asyncMDN initiate MDN send via separate channel so indicate receipt processed OK
            try {
                if (!isResend) {
                    HTTPUtil.sendHTTPResponse(httpOutputStream, HttpURLConnection.HTTP_OK, null);
                }
            } catch (IOException e) {
                // Not sure of best action here. For now just ignore and try to send MDN.
                e.printStackTrace();
            }
            if (logger.isTraceEnabled()) {
                logger.trace("Calling asynch MDN sender....");
            }
            if (!sendAsyncMDN(mdn, ((AS2Message) msg).getAsyncMDNurl(), disposition, options)) {
                // Handling of failure to send MDN done already in sendAsyncMDN so just return
                return;
            }
        } else {
            // otherwise, send sync MDN back on same connection

            ByteArrayOutputStream dataStream = new ByteArrayOutputStream();
            MimeBodyPart part = mdn.getData();
            try {
                part.writeTo(dataStream);
            } catch (Exception e) {
                WrappedException we = new WrappedException("Error writing MDN to byte array.", e);
                we.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
                //we.terminate();
                throw new WrappedException(we);
            }
            // make sure to set the content-length header
            mdn.setHeader("Content-Length", Integer.toString(dataStream.size()));
            try {
                HTTPUtil.sendHTTPResponse(httpOutputStream, HttpURLConnection.HTTP_OK, dataStream, mdn.getHeaders().getAllHeaderLines());
                msg.setOption("STATE", Message.MSG_STATE_MSG_RXD_MDN_SENT_OK);
                msg.trackMsgState(getSession());
            } catch (IOException e) {
                WrappedException we = new WrappedException("Error writing MDN to output stream.", e);
                we.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
                throw new WrappedException(we);
            }
            if (logger.isTraceEnabled()) {
                Enumeration<Header> headers = mdn.getHeaders().getAllHeaders();
                if (headers.hasMoreElements()) {
                    logger.trace("MDN HEADERS SENT: " + HTTPUtil.printHeaders(headers, ";", "=") + msg.getLogMsgID());
                }
            }
        }
        // Save sent MDN for later examination
        getSession().getProcessor().handle(StorageModule.DO_STOREMDN, msg, null);
        if (logger.isInfoEnabled()) {
            logger.info("sent MDN [" + disposition.toString() + "]" + msg.getLogMsgID());
        }
    }

    private boolean sendAsyncMDN(MessageMDN mdn, String url, DispositionType disposition, Map<String, Object> options) throws OpenAS2Exception {

        AS2Message msg = (AS2Message) mdn.getMessage();
        // Store options on the message object
        if (options != null) {
            msg.getOptions().putAll(options);
        }
        try {
            // Create a HTTP connection
            if (logger.isDebugEnabled()) {
                logger.debug("ASYNC MDN attempting connection to: " + url + mdn.getMessage().getLogMsgID());
            }
            long maxSize = msg.getPartnership().getNoChunkedMaxSize();
            Map<String, Object> httpOptions = getHttpOptions();
            httpOptions.put(HTTPUtil.PARAM_HTTP_USER, msg.getPartnership().getAttribute(HTTPUtil.PARAM_HTTP_USER));
            httpOptions.put(HTTPUtil.PARAM_HTTP_PWD, msg.getPartnership().getAttribute(HTTPUtil.PARAM_HTTP_PWD));
            // Convert the MimebodyPart to a string so we know how big it is to set Content-Length
            ByteArrayOutputStream dataOutputStream = new ByteArrayOutputStream();
            MimeBodyPart part = mdn.getData();
            try {
                part.writeTo(dataOutputStream);
            } catch (Exception e) {
                WrappedException we = new WrappedException("Error writing MDN to byte array.", e);
                we.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
                //we.terminate();
                throw new WrappedException(we);
            }
            byte[] data = dataOutputStream.toByteArray();
            // make sure to set the content-length header to avoid transferring as chunked which some AS2 software implementations do not support
            mdn.setHeader(HTTP.CONTENT_LEN, Integer.toString(data.length));
            boolean preventChunking = msg.getPartnership().isPreventChunking(false);
            ResponseWrapper resp = HTTPUtil.execRequest(HTTPUtil.Method.POST, url, mdn.getHeaders(), null, new ByteArrayInputStream(data), httpOptions, maxSize, preventChunking);

            int respCode = resp.getStatusCode();
            // Check the HTTP Response code
            if ((respCode != HttpURLConnection.HTTP_OK) && (respCode != HttpURLConnection.HTTP_CREATED) && (respCode != HttpURLConnection.HTTP_ACCEPTED) && (respCode != HttpURLConnection.HTTP_PARTIAL) && (respCode != HttpURLConnection.HTTP_NO_CONTENT)) {
                if (logger.isErrorEnabled()) {
                    msg.setLogMsg("Error sending AsyncMDN [" + disposition.toString() + "] HTTP response code: " + respCode);
                    logger.error(msg.getLogMsg());
                }
                throw new HttpResponseException(url, respCode, resp.getStatusPhrase());
            }
            // Logger significant msg state
            msg.setStatus(Message.MSG_STATE_MSG_RXD_MDN_SENT_OK);
            msg.setOption("STATE", Message.MSG_STATE_MSG_RXD_MDN_SENT_OK);
            msg.trackMsgState(getSession());

            if (logger.isInfoEnabled()) {
                logger.info("sent AsyncMDN [" + disposition.toString() + "] OK " + msg.getLogMsgID());
            }

            // log & store mdn into backup folder.
            getSession().getProcessor().handle(StorageModule.DO_STOREMDN, msg, null);

        } catch (HttpResponseException hre) {
            // Resend if the HTTP Response has an error code
            logger.warn("HTTP exception sending ASYNC MDN: " + org.openas2.util.Logging.getExceptionMsg(hre) + msg.getLogMsgID(), hre);
            hre.log();
            // Logger significant msg state
            msg.setOption("STATE", Message.MSG_STATE_MDN_SENDING_EXCEPTION);
            msg.trackMsgState(getSession());
            resend(msg, hre, options);
            return false;
        } catch (IOException ioe) {
            logger.warn("IO exception sending ASYNC MDN: " + org.openas2.util.Logging.getExceptionMsg(ioe) + msg.getLogMsgID(), ioe);
            // Resend if a network error occurs during transmission
            WrappedException wioe = new WrappedException(ioe);
            wioe.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
            wioe.log();
            // Logger significant msg state
            msg.setOption("STATE", Message.MSG_STATE_MDN_SENDING_EXCEPTION);
            msg.trackMsgState(getSession());
            resend(msg, wioe, options);
            return false;
        } catch (Exception e) {
            logger.warn("Unexpected exception sending ASYNC MDN: " + org.openas2.util.Logging.getExceptionMsg(e) + msg.getLogMsgID(), e);
            // Propagate error if it can't be handled by a resend
            // log & store mdn into backup folder.
            getSession().getProcessor().handle(StorageModule.DO_STOREMDN, msg, null);
            // Logger significant msg state
            msg.setOption("STATE", Message.MSG_STATE_MDN_SENDING_EXCEPTION);
            msg.trackMsgState(getSession());
            throw new WrappedException(e);
        }
        return true;
    }

    protected void resend(Message msg, OpenAS2Exception cause, Map<String, Object> options) throws OpenAS2Exception {
        // Get the resend retry count
        Map<String, Object> msgOptions = msg.getOptions();
        int tries = Integer.parseInt((String)msgOptions.get(ResenderModule.OPTION_RETRIES));
        int maxRetryCount = AS2Util.getMaxResendCount(getSession(), msg);
        if (logger.isTraceEnabled()) {
            logger.trace("Send MDN retry count: " + tries);
        }
        if (maxRetryCount > -1) {
            // Have to resend some fixed number of times so check if we are done
            if (tries >= maxRetryCount) {
                msg.setLogMsg("MDN response abandoned after retry limit reached.");
                logger.error(msg.getLogMsg());
                // Logger significant msg state
                msg.setOption("STATE", Message.MSG_STATE_MSG_RXD_MDN_SENDING_FAIL);
                msg.trackMsgState(getSession());
                AS2Util.cleanupFiles(msg, false);
                throw new OpenAS2Exception("MDN response abandoned after retry limit reached." + msg.getLogMsgID());
            }
        }
        options.put(ResenderModule.OPTION_CAUSE, cause);
        options.put(ResenderModule.OPTION_INITIAL_SENDER, this);
        options.put(ResenderModule.OPTION_RESEND_METHOD, SenderModule.DO_SENDMDN);
        options.put(ResenderModule.OPTION_RETRIES, "" + tries);
        try {
            msg.setStatus(Message.MSG_STATUS_MDN_RESEND);
            msg.setOption("STATE", Message.MSG_STATE_MDN_SEND_FAIL_RESEND_QUEUED);
            getSession().getProcessor().handle(ResenderModule.DO_RESEND, msg, options);
            msg.trackMsgState(getSession());
        } catch (OpenAS2Exception e) {
            msg.setOption("STATE", Message.MSG_STATE_MSG_RXD_MDN_SENDING_FAIL);
            msg.trackMsgState(getSession());
        }
    }

}
