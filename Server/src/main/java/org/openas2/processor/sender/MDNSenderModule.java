package org.openas2.processor.sender;

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.WrappedException;
import org.openas2.message.AS2Message;
import org.openas2.message.AS2MessageMDN;
import org.openas2.message.Message;
import org.openas2.message.MessageMDN;
import org.openas2.processor.resender.ResenderModule;
import org.openas2.processor.storage.StorageModule;
import org.openas2.util.AS2Util;
import org.openas2.util.DispositionType;
import org.openas2.util.HTTPUtil;
import org.openas2.util.ResponseWrapper;

import javax.mail.Header;
import javax.mail.internet.MimeBodyPart;
import java.io.BufferedOutputStream;
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

    private Log logger = LogFactory.getLog(MDNSenderModule.class.getSimpleName());

    public boolean canHandle(String action, Message msg, Map<Object, Object> options) {
        if (!action.equals(SenderModule.DO_SENDMDN)) {
            return false;
        }

        return (msg instanceof AS2Message);
    }

    public void handle(String action, Message msg, Map<Object, Object> options) throws OpenAS2Exception {
        if (logger.isDebugEnabled()) {
            logger.debug("ASYNC MDN send started...");
        }
        if (options == null) {
            options = new HashMap<Object, Object>();
        }
        options.put("DIRECTION", "RECEIVE");
        BufferedOutputStream out = (BufferedOutputStream) options.get("buffered_output_stream");
        MessageMDN mdn = msg.getMDN();
        DispositionType disposition = new DispositionType(mdn.getAttribute(AS2MessageMDN.MDNA_DISPOSITION));

        // if asyncMDN requested...
        if (msg.isRequestingAsynchMDN()) {
            // for asyncMDN initiate MDN send via separate channel so indicate receipt
            // processed OK
            try {
                if (!Message.MSG_STATUS_MSG_RESEND.equals(msg.getStatus())) {
                    HTTPUtil.sendHTTPResponse(out, HttpURLConnection.HTTP_OK, null);
                }
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            if (logger.isTraceEnabled()) {
                logger.trace("Calling asynch MDN sender....");
            }
            if (!sendAsyncMDN(mdn, ((AS2Message) msg).getAsyncMDNurl(), disposition, options)) {
                return;
            }
        } else {
            // otherwise, send sync MDN back on same connection

            ByteArrayOutputStream data = new ByteArrayOutputStream();
            MimeBodyPart part = mdn.getData();
            try {
                IOUtils.copy(part.getInputStream(), data);
            } catch (Exception e) {
                WrappedException we = new WrappedException("Error writing MDN to byte array.", e);
                we.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
                //we.terminate();
                throw new WrappedException(we);
            }
            // make sure to set the content-length header
            mdn.setHeader("Content-Length", Integer.toString(data.size()));

            try {
                HTTPUtil.sendHTTPResponse(out, HttpURLConnection.HTTP_OK, data, mdn.getHeaders().getAllHeaderLines());
            } catch (IOException e) {
                WrappedException we = new WrappedException("Error writing MDN to output stream.", e);
                we.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
                //we.terminate();
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

    private boolean sendAsyncMDN(MessageMDN mdn, String url, DispositionType disposition, Map<Object, Object> options) throws OpenAS2Exception {

        Message msg = mdn.getMessage();
        try {
            // Create a HTTP connection
            if (logger.isDebugEnabled()) {
                logger.debug("ASYNC MDN attempting connection to: " + url + mdn.getMessage().getLogMsgID());
            }
            long maxSize = msg.getPartnership().getNoChunkedMaxSize();
            Map<String, String> httpOptions = getHttpOptions();
            httpOptions.put(HTTPUtil.PARAM_HTTP_USER, msg.getPartnership().getAttribute(HTTPUtil.PARAM_HTTP_USER));
            httpOptions.put(HTTPUtil.PARAM_HTTP_PWD, msg.getPartnership().getAttribute(HTTPUtil.PARAM_HTTP_PWD));
            ResponseWrapper resp = HTTPUtil.execRequest(HTTPUtil.Method.POST, url, mdn.getHeaders().getAllHeaders(), null, mdn.getData().getInputStream(), httpOptions, maxSize);

            int respCode = resp.getStatusCode();
            // Check the HTTP Response code
            if ((respCode != HttpURLConnection.HTTP_OK) && (respCode != HttpURLConnection.HTTP_CREATED) && (respCode != HttpURLConnection.HTTP_ACCEPTED) && (respCode != HttpURLConnection.HTTP_PARTIAL) && (respCode != HttpURLConnection.HTTP_NO_CONTENT)) {
                if (logger.isErrorEnabled()) {
                    msg.setLogMsg("Error sending AsyncMDN [" + disposition.toString() + "] HTTP response code: " + respCode);
                    logger.error(msg);
                }
                throw new HttpResponseException(url, respCode, resp.getStatusPhrase());
            }

            if (logger.isInfoEnabled()) {
                logger.info("sent AsyncMDN [" + disposition.toString() + "] OK " + msg.getLogMsgID());
            }

            // log & store mdn into backup folder.
            getSession().getProcessor().handle(StorageModule.DO_STOREMDN, msg, null);
            // Log significant msg state
            msg.setOption("STATE", Message.MSG_STATE_MSG_RXD_MDN_SENT_OK);
            msg.trackMsgState(getSession());

        } catch (HttpResponseException hre) {
            // Resend if the HTTP Response has an error code
            logger.warn("HTTP exception sending ASYNC MDN: " + org.openas2.logging.Log.getExceptionMsg(hre) + msg.getLogMsgID(), hre);
            hre.terminate();
            resend(msg, hre);
            // Log significant msg state
            msg.setOption("STATE", Message.MSG_STATE_MDN_SENDING_EXCEPTION);
            msg.trackMsgState(getSession());
            return false;
        } catch (IOException ioe) {
            logger.warn("IO exception sending ASYNC MDN: " + org.openas2.logging.Log.getExceptionMsg(ioe) + msg.getLogMsgID(), ioe);
            // Resend if a network error occurs during transmission
            WrappedException wioe = new WrappedException(ioe);
            wioe.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
            wioe.terminate();

            resend(msg, wioe);
            // Log significant msg state
            msg.setOption("STATE", Message.MSG_STATE_MDN_SENDING_EXCEPTION);
            msg.trackMsgState(getSession());
            return false;
        } catch (Exception e) {
            logger.warn("Unexpected exception sending ASYNC MDN: " + org.openas2.logging.Log.getExceptionMsg(e) + msg.getLogMsgID(), e);
            // Propagate error if it can't be handled by a resend
            // log & store mdn into backup folder.
            getSession().getProcessor().handle(StorageModule.DO_STOREMDN, msg, null);
            // Log significant msg state
            msg.setOption("STATE", Message.MSG_STATE_MDN_SENDING_EXCEPTION);
            msg.trackMsgState(getSession());
            throw new WrappedException(e);
        }
        return true;
    }

    protected void resend(Message msg, OpenAS2Exception cause) throws OpenAS2Exception {
        // Get the resend retry count
        Map<Object, Object> msgOptions = msg.getOptions();
        String tries = AS2Util.retries(msgOptions, getParameter(SenderModule.SOPT_RETRIES, false));
        if (logger.isDebugEnabled()) {
            logger.debug("MDN resend retries: MSG - " + msgOptions.get(SenderModule.SOPT_RETRIES) + "   ::: RETRIES - " + tries);
        }
        int retries = -1;
        if (tries == null) {
            tries = SenderModule.DEFAULT_RETRIES;
        }
        try {
            retries = Integer.parseInt(tries);
        } catch (Exception e) {
            msg.setLogMsg("The retry count is not a valid integer value: " + tries);
            logger.error(msg);
        }
        if (msgOptions.get(SenderModule.SOPT_RETRIES) == null) {
            msgOptions.put(SenderModule.SOPT_RETRIES, retries);
        }
        if (logger.isTraceEnabled()) {
            logger.trace("Send MDN retry count: " + retries);
        }
        if (retries >= 0 && retries-- <= 0) {
            msg.setLogMsg("MDN response abandoned after retry limit reached.");
            logger.error(msg);
            // Log significant msg state
            msg.setOption("STATE", Message.MSG_STATE_MSG_RXD_MDN_SENDING_FAIL);
            msg.trackMsgState(getSession());
            AS2Util.cleanupFiles(msg, false);
            throw new OpenAS2Exception("MDN response abandoned after retry limit reached." + msg.getLogMsgID());
        }
        Map<Object, Object> options = new HashMap<Object, Object>();
        options.put(ResenderModule.OPTION_CAUSE, cause);
        options.put(ResenderModule.OPTION_INITIAL_SENDER, this);
        options.put(ResenderModule.OPTION_RESEND_METHOD, SenderModule.DO_SENDMDN);
        options.put(ResenderModule.OPTION_RETRIES, "" + retries);
        getSession().getProcessor().handle(ResenderModule.DO_RESEND, msg, options);
    }

}
