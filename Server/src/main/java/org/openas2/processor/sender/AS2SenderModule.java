package org.openas2.processor.sender;

import org.apache.commons.io.filefilter.AgeFileFilter;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.ComponentNotFoundException;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.cert.CertificateFactory;
import org.openas2.lib.helper.ICryptoHelper;
import org.openas2.message.AS2Message;
import org.openas2.message.AS2MessageMDN;
import org.openas2.message.DataHistoryItem;
import org.openas2.message.FileAttribute;
import org.openas2.message.Message;
import org.openas2.message.MessageMDN;
import org.openas2.message.NetAttribute;
import org.openas2.params.CompositeParameters;
import org.openas2.params.InvalidParameterException;
import org.openas2.params.MessageParameters;
import org.openas2.params.ParameterParser;
import org.openas2.partner.Partnership;
import org.openas2.processor.resender.ResenderModule;
import org.openas2.schedule.HasSchedule;
import org.openas2.util.AS2Util;
import org.openas2.util.DateUtil;
import org.openas2.util.DispositionOptions;
import org.openas2.util.HTTPUtil;
import org.openas2.util.IOUtil;
import org.openas2.util.Properties;
import org.openas2.util.ResponseWrapper;

import javax.mail.MessagingException;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.net.ssl.SSLHandshakeException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AS2SenderModule extends HttpSenderModule implements HasSchedule {

    private Log logger = LogFactory.getLog(AS2SenderModule.class.getSimpleName());

    public boolean canHandle(String action, Message msg, Map<Object, Object> options) {
        if (!action.equals(SenderModule.DO_SEND)) {
            return false;
        }

        return (msg instanceof AS2Message);
    }

    public void handle(String action, Message msg, Map<Object, Object> options) throws OpenAS2Exception {

        if (logger.isInfoEnabled()) {
            logger.info("message sender invoked" + msg.getLogMsgID());
        }
        boolean isResend = Message.MSG_STATUS_MSG_RESEND.equals(msg.getStatus());
        options.put("DIRECTION", "SEND");
        options.put("IS_RESEND", isResend ? "Y" : "N");
        if (!(msg instanceof AS2Message)) {
            throw new OpenAS2Exception("Can't send non-AS2 message");
        }

        // verify all required information is present for sending
        checkRequired(msg);
        // Store options on the message object
        if (options != null) {
            msg.getOptions().putAll(options);
        }
        if (logger.isTraceEnabled()) {
            logger.trace("Retry count from options: " + options);
        }
        // Get the resend retry count
        String retries = AS2Util.retries(options, getParameter(SenderModule.SOPT_RETRIES, false));

        // Get any static custom headers
        addCustomHeaders(msg);
        // encrypt and/or sign and/or compress the message if needed
        MimeBodyPart securedData;
        try {
            securedData = secure(msg);
            // Add any additional headers if configured since this will be the outermost
            // Mime body part
            addCustomOuterMimeHeaders(msg, securedData);

            storePendingInfo((AS2Message) msg, isResend);
        } catch (Exception e) {
            msg.setLogMsg(org.openas2.logging.Log.getExceptionMsg(e));
            logger.error(msg, e);
            // Log significant msg state
            msg.setOption("STATE", Message.MSG_STATE_SEND_EXCEPTION);
            msg.trackMsgState(getSession());
            throw new OpenAS2Exception("Error setting up message for sending.", e);
        }
        if (logger.isTraceEnabled()) {
            try {
                logger.trace("Message object in sender module. Content-Disposition: " + msg.getContentDisposition() + "\n      Content-Type : " + msg.getContentType() + "\n      HEADERS : " + AS2Util.printHeaders(msg.getData().getAllHeaders()) + "\n      Content-Disposition in MSG getData() MIMEPART: " + msg.getData().getContentType() + msg.getLogMsgID());
            } catch (Exception e) {
            }
        }
        String url = msg.getPartnership().getAttribute(Partnership.PA_AS2_URL);
        // Allow for having dynamic variables in the URL
        CompositeParameters params = new CompositeParameters(false).add("msg", new MessageParameters(msg));
        url = ParameterParser.parse(url, params);
        try {
            // Create the HTTP connection and set up headers
            // Log significant msg state
            msg.setOption("STATE", Message.MSG_STATE_SEND_START);
            msg.trackMsgState(getSession());

            sendMessage(url, msg, securedData, retries);
        } catch (HttpResponseException hre) {
            // Will have been logged so just resend
            resend(msg, hre, retries, false);
            // Log significant msg state
            msg.setOption("STATE", Message.MSG_STATE_SEND_EXCEPTION);
            msg.trackMsgState(getSession());
            return;
        } catch (SSLHandshakeException e) {
            msg.setLogMsg("Failed to connect to partner using SSL certificate. Please run the SSL certificate checker utility to identify the issue: " + url);
            logger.error(msg, e);
            msg.setOption("STATE", Message.MSG_STATE_SEND_FAIL);
            msg.trackMsgState(getSession());
            return;
        } catch (Exception e) {
            msg.setLogMsg("Unexpected error sending file: " + org.openas2.logging.Log.getExceptionMsg(e));
            logger.error(msg, e);
            resend(msg, new OpenAS2Exception(org.openas2.logging.Log.getExceptionMsg(e)), retries, false);
            // Log significant msg state
            msg.setOption("STATE", Message.MSG_STATE_SEND_EXCEPTION);
            msg.trackMsgState(getSession());
            return;
        }
    }

    protected void checkRequired(Message msg) throws InvalidParameterException {
        Partnership partnership = msg.getPartnership();

        try {
            InvalidParameterException.checkValue(msg, "ContentType", msg.getContentType());
            InvalidParameterException.checkValue(msg, "Attribute: " + Partnership.PA_AS2_URL, partnership.getAttribute(Partnership.PA_AS2_URL));
            InvalidParameterException.checkValue(msg, "Receiver: " + Partnership.PID_AS2, partnership.getReceiverID(Partnership.PID_AS2));
            InvalidParameterException.checkValue(msg, "Sender: " + Partnership.PID_AS2, partnership.getSenderID(Partnership.PID_AS2));
            InvalidParameterException.checkValue(msg, "Subject", msg.getSubject());
            InvalidParameterException.checkValue(msg, "Sender: " + Partnership.PID_EMAIL, partnership.getSenderID(Partnership.PID_EMAIL));
            InvalidParameterException.checkValue(msg, "Message Data", msg.getData());
        } catch (InvalidParameterException rpe) {
            rpe.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
            throw rpe;
        }
    }

    private void sendMessage(String url, Message msg, MimeBodyPart securedData, String retries) throws Exception {
        URL urlObj = new URL(url);
        InternetHeaders ih = getHttpHeaders(msg, securedData);
        msg.setAttribute(NetAttribute.MA_DESTINATION_IP, urlObj.getHost());
        msg.setAttribute(NetAttribute.MA_DESTINATION_PORT, Integer.toString(urlObj.getPort()));

        if (logger.isInfoEnabled()) {
            logger.info("Connecting to: " + url + msg.getLogMsgID());
        }

        Map<String, String> httpOptions = getHttpOptions();
        httpOptions.put(HTTPUtil.PARAM_HTTP_USER, msg.getPartnership().getAttribute(HTTPUtil.PARAM_HTTP_USER));
        httpOptions.put(HTTPUtil.PARAM_HTTP_PWD, msg.getPartnership().getAttribute(HTTPUtil.PARAM_HTTP_PWD));
        long maxSize = msg.getPartnership().getNoChunkedMaxSize();
        ResponseWrapper resp = HTTPUtil.execRequest(HTTPUtil.Method.POST, url, ih.getAllHeaders(), null, securedData.getInputStream(), httpOptions, maxSize);
        if (logger.isInfoEnabled()) {
            logger.info("Message sent and response received in " + resp.getTransferTimeMs() + "ms" + msg.getLogMsgID());
        }

        // Check the HTTP Response code
        int rc = resp.getStatusCode();
        if ((rc != HttpURLConnection.HTTP_OK) && (rc != HttpURLConnection.HTTP_CREATED) && (rc != HttpURLConnection.HTTP_ACCEPTED) && (rc != HttpURLConnection.HTTP_PARTIAL) && (rc != HttpURLConnection.HTTP_NO_CONTENT)) {
            msg.setLogMsg("Error sending message. URL: " + url + " ::: Response Code: " + rc + " " + resp.getStatusPhrase() + " ::: Response Message: " + resp.getBody().toString());
            logger.error(msg);
            throw new HttpResponseException(url, rc, resp.getStatusPhrase());
        }
        // So far so good ...
        processResponse(msg, resp);
    }

    private void processResponse(Message msg, ResponseWrapper response) {
        if (logger.isTraceEnabled()) {
            logger.trace("Message sent. Checking if MDN is expected..." + msg.getLogMsgID());
        }
        if (!msg.isConfiguredForMDN()) {
            return;
        }
        // Check if it will be a Sync or AsyncMDN
        if (msg.getPartnership().getAttribute(Partnership.PA_AS2_RECEIPT_OPTION) != null) {
            // Async MDN
            msg.setStatus(Message.MSG_STATUS_MDN_WAIT);
        } else {
            // Create a MessageMDN and copy HTTP headers
            MessageMDN mdn = new AS2MessageMDN((AS2Message) msg, false);
            if (logger.isTraceEnabled()) {
                logger.trace("MDN msg initalised for inbound contains headers:" + AS2Util.printHeaders(mdn.getHeaders().getAllHeaders()) + msg.getLogMsgID());
            }
            mdn.copyHeaders(response.getHeaders());

            if (logger.isTraceEnabled()) {
                logger.trace("Synchronous MDN received. Start processing..." + msg.getLogMsgID());
            }
            msg.setStatus(Message.MSG_STATUS_MDN_PROCESS_INIT);
            try {
                AS2Util.processMDN((AS2Message) msg, response.getBody(), null, false, getSession(), this);
                // Log significant msg state
                msg.setOption("STATE", Message.MSG_STATE_MSG_SENT_MDN_RECEIVED_OK);
                msg.trackMsgState(getSession());
            } catch (Exception e) {
                if (Message.MSG_STATUS_MDN_PROCESS_INIT.equals(msg.getStatus()) || Message.MSG_STATUS_MDN_PARSE.equals(msg.getStatus()) || !(e instanceof OpenAS2Exception)) {
                    /*
                     * Cannot identify the target if in init or parse state so not sure what the
                     * best course of action is apart from do nothing
                     */
                    msg.setLogMsg("Unhandled error condition processing synchronous MDN. Message and associated files cleanup will be attempted but may be in an unknown state.");
                    logger.error(msg, e);
                } else {
                    /*
                     * Most likely a resend abort of max resend reached if OpenAS2Exception so do
                     * not log as should have been logged upstream ... just clean up the mess
                     */
                    // Must have received MDN successfully
                    msg.setLogMsg("Exception receiving synchronous MDN. Message and associated files cleanup will be attempted but may be in an unknown state.");
                    logger.error(msg, e);

                }
                // Log significant msg state
                msg.setOption("STATE", Message.MSG_STATE_SEND_FAIL);
                msg.trackMsgState(getSession());
                AS2Util.cleanupFiles(msg, true);
            }
        }
    }

    private void resend(Message msg, OpenAS2Exception cause, String tries, boolean keepRestoredData) throws OpenAS2Exception {
        AS2Util.resend(getSession(), this, SenderModule.DO_SEND, msg, cause, tries, false, keepRestoredData);
    }

    /**
     * Returns a MimeBodyPart or MimeMultipart object
     *
     * @param msg The message object carried around containing necessary
     *            information
     * @return The secured mimebodypart
     * @throws Exception some unforseen issue has occurred
     */
    protected MimeBodyPart secure(Message msg) throws Exception {
        // Set up encrypt/sign variables
        MimeBodyPart dataBP = msg.getData();
        /*
         * Based on RFC4130, RFC6362 and RFC5042, the MIC is calculated as follows:
         * Signed message - MIME header fields and content that is to be signed which
         * may or may not be encrypted and/or compressed.
         *
         * Unsigned encrypted message - data content including all MIME header fields
         * and any applied Content-Transfer-Encoding prior to encryption and/or
         * compression
         *
         * So essentially, calculate the MIC before doing any compression or encryption
         * if message not being signed otherwise calculate right before signing of the
         * message but include headers for unsigned messages (see RFC4130 section 7.3.1
         * for details)
         */

        Partnership partnership = msg.getPartnership();
        String contentTxfrEncoding = partnership.getAttribute(Partnership.PA_CONTENT_TRANSFER_ENCODING);
        if (contentTxfrEncoding == null) {
            contentTxfrEncoding = Session.DEFAULT_CONTENT_TRANSFER_ENCODING;
        }

        boolean encrypt = partnership.getAttribute(Partnership.PA_ENCRYPTION_ALGORITHM) != null;
        boolean sign = partnership.getAttribute(Partnership.PA_SIGNATURE_ALGORITHM) != null;

        if (!sign) {
            calcAndStoreMic(msg, dataBP, (sign || encrypt));
        }

        // Check if compression is enabled
        String compressionType = msg.getPartnership().getAttribute("compression_type");
        if (logger.isTraceEnabled()) {
            logger.trace("Compression type from config: " + compressionType);
        }
        boolean isCompress = false;
        if (compressionType != null && !"NONE".equalsIgnoreCase(compressionType)) {
            if (compressionType.equalsIgnoreCase(ICryptoHelper.COMPRESSION_ZLIB)) {
                isCompress = true;
            } else {
                throw new OpenAS2Exception("Unsupported compression type: " + compressionType);
            }
        }
        String compressionMode = msg.getPartnership().getAttribute("compression_mode");
        boolean isCompressBeforeSign = true; // Defaults to compressing the
        // entire message before signing
        // and encryption
        if (compressionMode != null && compressionMode.equalsIgnoreCase("compress-after-signing")) {
            isCompressBeforeSign = false;
        }
        if (isCompress && isCompressBeforeSign) {
            if (logger.isTraceEnabled()) {
                logger.trace("Compressing outbound message before signing...");
            }
            if (!sign && !encrypt) {
                // Add any additional headers since this will be the outermost Mime body part if
                // configured
                addCustomOuterMimeHeaders(msg, dataBP);
            }
            dataBP = AS2Util.getCryptoHelper().compress(msg, dataBP, compressionType, contentTxfrEncoding);
        }
        // Encrypt and/or sign the data if requested
        CertificateFactory certFx = getSession().getCertificateFactory();

        // Sign the data if requested
        if (sign) {
            if (!encrypt && !(isCompress && !isCompressBeforeSign)) {
                // Add any additional headers since this will be the outermost Mime body part if
                // configured
                addCustomOuterMimeHeaders(msg, dataBP);
            }
            calcAndStoreMic(msg, dataBP, (sign || encrypt));
            X509Certificate senderCert = certFx.getCertificate(msg, Partnership.PTYPE_SENDER);

            PrivateKey senderKey = certFx.getPrivateKey(msg, senderCert);
            String digest = partnership.getAttribute(Partnership.PA_SIGNATURE_ALGORITHM);

            if (logger.isDebugEnabled()) {
                logger.debug("Params for creating signed body part:: DATA: " + dataBP + "\n SIGN DIGEST: " + digest + "\n CERT ALG NAME EXTRACTED: " + senderCert.getSigAlgName() + "\n CERT PUB KEY ALG NAME EXTRACTED: " + senderCert.getPublicKey().getAlgorithm() + msg.getLogMsgID());
            }
            boolean isRemoveCmsAlgorithmProtectionAttr = "true".equalsIgnoreCase(partnership.getAttribute(Partnership.PA_REMOVE_PROTECTION_ATTRIB));
            dataBP = AS2Util.getCryptoHelper().sign(dataBP, senderCert, senderKey, digest, contentTxfrEncoding, msg.getPartnership().isRenameDigestToOldName(), isRemoveCmsAlgorithmProtectionAttr);

            DataHistoryItem historyItem = new DataHistoryItem(dataBP.getContentType());
            // *** add one more item to msg history
            msg.getHistory().getItems().add(historyItem);

            if (logger.isDebugEnabled()) {
                logger.debug("signed data" + msg.getLogMsgID());
            }
        }

        if (isCompress && !isCompressBeforeSign) {
            if (!encrypt) {
                // Add any additional headers since this will be the outermost Mime body part if
                // configured
                addCustomOuterMimeHeaders(msg, dataBP);
            }
            if (logger.isTraceEnabled()) {
                logger.trace("Compressing outbound message after signing...");
            }
            dataBP = AS2Util.getCryptoHelper().compress(msg, dataBP, compressionType, contentTxfrEncoding);
        }
        // Encrypt the data if requested
        if (encrypt) {
            // Add any additional headers since this will be the outermost Mime body part if
            // configured
            addCustomOuterMimeHeaders(msg, dataBP);
            String algorithm = partnership.getAttribute(Partnership.PA_ENCRYPTION_ALGORITHM);

            X509Certificate receiverCert = certFx.getCertificate(msg, Partnership.PTYPE_RECEIVER);
            dataBP = AS2Util.getCryptoHelper().encrypt(dataBP, receiverCert, algorithm, contentTxfrEncoding);

            // Asynch MDN 2007-03-12
            DataHistoryItem historyItem = new DataHistoryItem(dataBP.getContentType());
            // *** add one more item to msg history
            msg.getHistory().getItems().add(historyItem);

            if (logger.isDebugEnabled()) {
                logger.debug("encrypted data" + msg.getLogMsgID());
            }
        }

        String t = dataBP.getEncoding();
        if ((t == null || t.length() < 1) && "true".equalsIgnoreCase(partnership.getAttribute(Partnership.PA_SET_CONTENT_TRANSFER_ENCODING_OMBP))) {
            dataBP.setHeader("Content-Transfer-Encoding", contentTxfrEncoding);
        }
        return dataBP;
    }

    protected void addCustomHeaders(Message msg) throws OpenAS2Exception {
        String customHeaders = msg.getPartnership().getAttribute(Partnership.PA_CUSTOM_MIME_HEADERS);
        if (customHeaders != null && customHeaders.length() > 0) {
            if (logger.isTraceEnabled()) {
                logger.trace("Adding custom header attribute to custom headers map..." + msg.getLogMsgID());
            }
            String[] headers = customHeaders.split("\\s*;\\s*");
            for (int i = 0; i < headers.length; i++) {
                String[] header = headers[i].split("\\s*:\\s*");
                if (logger.isTraceEnabled()) {
                    logger.trace("Adding custom header: " + headers[i] + " :::Split count:" + header.length + msg.getLogMsgID());
                }
                if (header.length != 2) {
                    throw new OpenAS2Exception("Invalid custom header: " + headers[i]);
                }
                msg.addCustomOuterMimeHeader(header[0].replaceAll(" ", ""), header[1]);
            }
        }

    }

    protected void addCustomOuterMimeHeaders(Message msg, MimeBodyPart dataBP) throws MessagingException {
        if (logger.isTraceEnabled()) {
            logger.trace("Adding custom headers to outer MBP...." + msg.getLogMsgID());
        }
        Map<String, String> hdrs = msg.getCustomOuterMimeHeaders();
        if (hdrs == null) {
            return;
        }
        for (Map.Entry<String, String> entry : hdrs.entrySet()) {
            dataBP.addHeader(entry.getKey(), entry.getValue());
            if (logger.isTraceEnabled()) {
                logger.trace("Added custom headers to outer MBP: " + entry.getKey() + "--->" + entry.getValue() + msg.getLogMsgID());
            }
        }
    }

    protected InternetHeaders getHttpHeaders(Message msg, MimeBodyPart securedData) throws MessagingException {
        Partnership partnership = msg.getPartnership();
        InternetHeaders ih = new InternetHeaders();

        ih.addHeader(HTTPUtil.HEADER_CONNECTION, "close, TE");
        String userAgent = Properties.getProperty(Properties.HTTP_USER_AGENT_PROP, msg.getAppTitle() + " (" + AS2SenderModule.class.getSimpleName() + ")");
        ih.addHeader("User-Agent", userAgent);

        // Ensure date is formatted in english so there are only USASCII chars to avoid
        // error
        ih.addHeader("Date", DateUtil.formatDate(Properties.getProperty("HTTP_HEADER_DATE_FORMAT", "EEE, dd MMM yyyy HH:mm:ss Z"), Locale.ENGLISH));
        ih.addHeader("Message-ID", msg.getMessageID());
        ih.addHeader("Mime-Version", "1.0"); // make sure this is the
        // encoding used in the msg, run TBF1
        try {
            ih.addHeader(HTTPUtil.HEADER_CONTENT_TYPE, securedData.getContentType());
        } catch (MessagingException e) {
            ih.addHeader(HTTPUtil.HEADER_CONTENT_TYPE, msg.getContentType());
        }
        // AS2 V1.2 additionally supports EDIINT-Features
        // ih.addHeader("EDIINT-Features","CEM,multiple-attachments");
        // TODO (possibly implement???)
        ih.addHeader("AS2-Version", "1.1"); // RFC6017 - AS2 V1.1 supports compression
        /*
         * The Content-Transfer-Encoding header is now a restricted header for HTTP so
         * allow it to be controlled by config at partnership level Java will
         * automatically remove this even if set unless the
         * sun.net.http.allowRestrictedHeaders property is set to "true"
         */
        if ("true".equalsIgnoreCase(System.getProperty("sun.net.http.allowRestrictedHeaders", "false"))) {
            if (logger.isDebugEnabled()) {
                logger.debug("HTTP RESTRICTED HEADERS property is not active");
            }
            String cte = null;
            cte = msg.getPartnership().getAttributeOrProperty(Partnership.PA_CONTENT_TRANSFER_ENCODING, null);
            if (cte != null) {
                if ("true".equalsIgnoreCase(msg.getPartnership().getAttributeOrProperty(Partnership.PA_SET_CONTENT_TRANSFER_ENCODING_HTTP, "false"))) {
                    ih.addHeader("Content-Transfer-Encoding", cte);
                }
            }
        }
        ih.addHeader("Recipient-Address", partnership.getAttribute(Partnership.PA_AS2_URL));
        String rId = partnership.getReceiverID(Partnership.PID_AS2);
        if (rId.contains(" ")) {
            rId = "\"" + rId + "\"";
        }
        ih.addHeader("AS2-To", rId);
        String sId = partnership.getSenderID(Partnership.PID_AS2);
        if (sId.contains(" ")) {
            sId = "\"" + sId + "\"";
        }
        ih.addHeader("AS2-From", sId);
        ih.addHeader("Subject", msg.getSubject());
        ih.addHeader("From", partnership.getSenderID(Partnership.PID_EMAIL));
        String dispTo = partnership.getAttribute(Partnership.PA_AS2_MDN_TO);
        if (dispTo != null) {
            ih.addHeader("Disposition-Notification-To", dispTo);
        }
        String dispOptions = partnership.getAttribute(Partnership.PA_AS2_MDN_OPTIONS);
        if (dispOptions != null && !"none".equalsIgnoreCase(dispOptions)) {
            ih.addHeader("Disposition-Notification-Options", dispOptions);
        }
        String receiptOption = partnership.getAttribute(Partnership.PA_AS2_RECEIPT_OPTION);
        if (receiptOption != null) {
            ih.addHeader("Receipt-Delivery-Option", receiptOption);
        }
        String contentDisp;
        try {
            contentDisp = securedData.getDisposition();
        } catch (MessagingException e) {
            contentDisp = msg.getContentDisposition();
        }
        if (contentDisp != null) {
            ih.addHeader("Content-Disposition", contentDisp);
        }
        if ("true".equalsIgnoreCase((partnership.getAttribute(Partnership.PA_ADD_CUSTOM_MIME_HEADERS_TO_HTTP)))) {
            if (logger.isTraceEnabled()) {
                logger.trace("Adding custom headers to HTTP..." + msg.getLogMsgID());
            }
            for (Map.Entry<String, String> entry : msg.getCustomOuterMimeHeaders().entrySet()) {
                ih.addHeader(entry.getKey(), entry.getValue());
            }
        }
        return ih;
    }

    /**
     * Stores metadata into pending information file and storing message object from
     * first send attempt. The message object is written to a separate file to avoid
     * repeated rewrites of possibly very large objects since it contains the
     * original file data
     *
     * @param msg      AS2Message structure
     * @param isResend Boolean to determine if this is a resend of an already
     *                 sent message or not
     * @throws Exception some unforseen issue has occurred
     */
    protected void storePendingInfo(AS2Message msg, boolean isResend) throws Exception {
        ObjectOutputStream oos = null;

        try {
            String pendingInfoFile = AS2Util.buildPendingFileName(msg, getSession().getProcessor(), "pendingmdninfo");
            String pendingFile = msg.getAttribute(FileAttribute.MA_PENDINGFILE);
            msg.setAttribute(FileAttribute.MA_PENDINGFILE, pendingFile);
            msg.setAttribute(FileAttribute.MA_PENDINGINFO, pendingInfoFile);
            if (!isResend) {
                // Write the object to a file to keep a lot of the original
                // static metadata intact for resends
                String pendingMsgObjFile = pendingFile + ".object";
                oos = new ObjectOutputStream(new FileOutputStream(pendingMsgObjFile));
                oos.writeObject(msg);
                oos.flush();
                oos.close();
            }
            oos = new ObjectOutputStream(new FileOutputStream(pendingInfoFile));
            oos.writeObject(msg.getCalculatedMIC());
            String retries = (String) msg.getOption(ResenderModule.OPTION_RETRIES);
            oos.writeObject((retries == null ? "" : retries));

            if (logger.isInfoEnabled()) {
                logger.info("Save Original mic & message id information into file: " + pendingInfoFile + msg.getLogMsgID());
            }
            oos.writeObject(msg.getPayloadFilename());
            oos.writeObject(msg.getAttribute(FileAttribute.MA_FILENAME));
            oos.writeObject(pendingFile);
            oos.writeObject(msg.getAttribute(FileAttribute.MA_ERROR_DIR));
            String sentDir = msg.getAttribute(FileAttribute.MA_SENT_DIR);
            oos.writeObject((sentDir == null ? "" : sentDir));
            oos.writeObject(msg.getAttributes());
            if (logger.isTraceEnabled()) {
                logger.trace("Pending info file written to:" + pendingInfoFile + "\n\tOriginal MIC: " + msg.getCalculatedMIC() + "\n\tRetry Count: " + retries + "\n\tOriginal file name : " + msg.getAttribute(FileAttribute.MA_FILENAME) + "\n\tPending message file : " + pendingFile + "\n\tError directory: " + msg.getAttribute(FileAttribute.MA_ERROR_DIR) + "\n\tSent directory: " + msg.getAttribute(FileAttribute.MA_SENT_DIR) + "\n\tAttributes: " + msg.getAttributes() + msg.getLogMsgID());
            }

            msg.setAttribute(FileAttribute.MA_STATUS, FileAttribute.MA_PENDING);
            // If ASYNC MDN is requested, set up a file watcher in case partner MDN is not
            // received
            if (msg.isConfiguredForAsynchMDN()) {
                // Create a listener that will force resend if the pendinginfo file is still
                // there after set amount of time

            }
        } catch (Exception e) {
            msg.setLogMsg("Error setting up pending information files: " + org.openas2.logging.Log.getExceptionMsg(e));
            logger.error(msg, e);
            throw new Exception("Unable to set up pending information files.");
        } finally {
            if (oos != null) {
                try {
                    oos.close();
                } catch (IOException e) {
                }
            }
        }
    }

    protected void calcAndStoreMic(Message msg, MimeBodyPart mbp, boolean includeHeaders) throws Exception {
        // Calculate and get the original mic
        // includeHeaders = (msg.getHistory().getItems().size() > 1);

        String mdnOptions = msg.getPartnership().getAttributeOrProperty(Partnership.PA_AS2_MDN_OPTIONS, null);
        if (mdnOptions == null || mdnOptions.length() < 1) {
            throw new OpenAS2Exception("Partner attribute " + Partnership.PA_AS2_MDN_OPTIONS + "is required but can be set to \"none\"");
        }
        if ("none".equalsIgnoreCase(mdnOptions)) {
            return;
        }
        DispositionOptions dispOptions = new DispositionOptions(mdnOptions);
        msg.setCalculatedMIC(AS2Util.getCryptoHelper().calculateMIC(mbp, dispOptions.getMicalg(), includeHeaders, msg.getPartnership().isPreventCanonicalization()));
        if (logger.isTraceEnabled()) {
            // Generate some alternative MIC's to see if the partner is somehow using a
            // different default
            String tmic = AS2Util.getCryptoHelper().calculateMIC(mbp, dispOptions.getMicalg(), includeHeaders, !msg.getPartnership().isPreventCanonicalization());
            logger.trace("MIC outbound with forced reversed prevent canocalization: " + tmic + msg.getLogMsgID());
            tmic = AS2Util.getCryptoHelper().calculateMIC(msg.getData(), dispOptions.getMicalg(), false, msg.getPartnership().isPreventCanonicalization());
            logger.trace("MIC outbound with forced exclude headers flag: " + tmic + msg.getLogMsgID());

        }
    }

    protected void detectFailedSentMessages() {
        String dir;
        try {
            dir = getSession().getProcessor().getParameters().get("pendingmdninfo");
        } catch (ComponentNotFoundException e) {
            logger.warn("Failed to retrieve the name of the pending info folder for sent messages in trying to run the failed message detection method.", e);
            return;
        }
        File pendingDir;
        try {
            pendingDir = IOUtil.getDirectoryFile(dir);
        } catch (IOException e) {
            logger.warn("Failed to open the pending info folder for sent messages in trying to run the failed message detection method.", e);
            return;
        }
        // We are interested in files older than configured seconds
        int maxWaitMdnResponseSecs = Integer.parseInt(Properties.getProperty(Properties.AS2_MDN_RESP_MAX_WAIT_SECS, "4560"));
        long cutoff = System.currentTimeMillis() - (maxWaitMdnResponseSecs * 1000);
        String[] files = pendingDir.list(new AgeFileFilter(cutoff));
        for (int i = 0; i < files.length; i++) {
            File inFile = new File(pendingDir + File.separator + files[i]);
            try {
                AS2Message msg = new AS2Message();
                AS2Util.getMetaData(msg, inFile);
                AS2Util.cleanupFiles(msg, true);
                String msgStr = "Pending information file detected that is past max wait time, Failure most likely due to not receiving MDN response in Async mode: " + inFile.getAbsolutePath();
                msg.setLogMsg(msgStr);
                msg.setStatus(Message.MSG_STATUS_MSG_TERMINATED_IN_ERROR);
                logger.error(msg, null);
                // Log significant msg state
                msg.setOption("STATE", Message.MSG_STATE_MDN_ASYNC_RECEIVE_FAIL);
                msg.trackMsgState(getSession());
            } catch (Exception e) {
                logger.warn("Failed to process the pending info folder for sent messages in trying to run the failed message detection method.", e);
            }
        }
    }

    @Override
    public void schedule(ScheduledExecutorService executor) {
        String delayStr = Properties.getProperty(Properties.AS2_MDN_RESP_MAX_WAIT_SECS, "4560");
        Long delay = Long.parseLong(delayStr) / 4 * 1000;
        executor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                detectFailedSentMessages();
            }
        }, delay, delay, TimeUnit.MILLISECONDS);
    }
}
