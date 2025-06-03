package org.openas2.processor.receiver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openas2.DispositionException;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.WrappedException;
import org.openas2.cert.CertificateFactory;
import org.openas2.cert.CertificateNotFoundException;
import org.openas2.cert.KeyNotFoundException;
import org.openas2.lib.helper.ICryptoHelper;
import org.openas2.lib.message.AS2Standards;
import org.openas2.lib.util.MimeUtil;
import org.openas2.message.AS2Message;
import org.openas2.message.AS2MessageMDN;
import org.openas2.message.Message;
import org.openas2.message.MessageMDN;
import org.openas2.message.NetAttribute;
import org.openas2.params.CompositeParameters;
import org.openas2.params.DateParameters;
import org.openas2.params.MessageParameters;
import org.openas2.params.ParameterParser;
import org.openas2.params.RandomParameters;
import org.openas2.partner.Partnership;
import org.openas2.processor.msgtracking.BaseMsgTrackingModule.FIELDS;
import org.openas2.processor.resender.ResenderModule;
import org.openas2.processor.sender.SenderModule;
import org.openas2.processor.storage.StorageModule;
import org.openas2.util.AS2Util;
import org.openas2.util.ByteArrayDataSource;
import org.openas2.util.DateUtil;
import org.openas2.util.DispositionOptions;
import org.openas2.util.DispositionType;
import org.openas2.util.HTTPUtil;
import org.openas2.util.IOUtil;
import org.openas2.util.Profiler;
import org.openas2.util.ProfilerStub;
import org.openas2.util.Properties;

import jakarta.activation.DataHandler;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.InternetHeaders;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;
import jakarta.mail.internet.MimeUtility;
import jakarta.mail.internet.ParseException;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

public class AS2ReceiverHandler implements NetModuleHandler {
    private AS2ReceiverModule module;

    private static final Logger LOG = LoggerFactory.getLogger(AS2ReceiverHandler.class);

    public AS2ReceiverHandler(AS2ReceiverModule module) {
        super();
        this.module = module;

    }

    public String getClientInfo(Socket s) {
        return " " + s.getInetAddress().getHostAddress() + " " + s.getPort();
    }

    public AS2ReceiverModule getModule() {
        return module;
    }

    public void handle(NetModule owner, Socket s) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("incoming connection" + getClientInfo(s));
        }

        AS2Message msg = createMessage(s);

        byte[] data = null;
        BufferedOutputStream out;

        msg.setOption(FIELDS.DIRECTION, "RECEIVE");

        try {
            out = new BufferedOutputStream(s.getOutputStream());
        } catch (IOException e1) {
            msg.setLogMsg("Failed to get outputstream on received socket. Response cannot be sent.");
            LOG.error(msg.getLogMsg(), e1);
            return;
        }

        try {
            // Time the transmission
            ProfilerStub transferStub = Profiler.startProfile();
            // Read in the message request, headers, and data
            try {
                data = HTTPUtil.readData(s.getInputStream(), s.getOutputStream(), msg);

            } catch (Throwable e) {
                msg.setLogMsg("HTTP connection error on inbound message. Error is: " + e.getMessage());
                LOG.error(msg.getLogMsg(), e);
                NetException ne = new NetException(s.getInetAddress(), s.getPort(), e);
                ne.log();
            }
            Profiler.endProfile(transferStub);

            String mic = null;
            if (data == null) {
                if ("true".equalsIgnoreCase(msg.getAttribute("isHealthCheck"))) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Healthcheck ping detected" + " [" + getClientInfo(s) + "]" + msg.getLogMsgID());
                    }
                    return;
                } else {
                    try {
                        HTTPUtil.sendHTTPResponse(s.getOutputStream(), HttpURLConnection.HTTP_BAD_REQUEST, null);
                    } catch (IOException e1) {
                    }
                    OpenAS2Exception oe = new OpenAS2Exception("Missing data in AS2 request.");
                    msg.setLogMsg("Error receiving message for inbound AS2 request. There is no data.");
                    if ("true".equals(Properties.getProperty(Properties.LOG_INVALID_HTTP_REQUEST, "true"))) {
                        LOG.info(msg.getLogMsg(), oe);
                    }
                    return;
                }
            } else {
                if (LOG.isInfoEnabled()) {
                    LOG.info("received " + IOUtil.getTransferRate(data.length, transferStub) + getClientInfo(s) + msg.getLogMsgID());
                }

                if (LOG.isTraceEnabled()) {
                    LOG.trace("Received msg built from HTTP input stream: " + msg.toString() + msg.getLogMsgID());
                }
                // TODO store HTTP request, headers, and data to file in Received folder -> use message-id for filename?
                try {
                    // Put received data in a MIME body part
                    ContentType receivedContentType = null;

                    try {
                        MimeBodyPart receivedPart = null;
                        if ("true".equals(msg.getPartnership().getAttributeOrProperty("use_old_mime_deserialise_method", "false"))) {
                        // TODO: Delete this when the new method is confirmed working reliably. Changed for 3.4.1
                            receivedContentType = new ContentType(msg.getHeader(MimeUtil.MIME_CONTENT_TYPE_KEY));

                            receivedPart = new MimeBodyPart();
                            receivedPart.setDataHandler(new DataHandler(new ByteArrayDataSource(data, receivedContentType.toString(), null)));
                            // Set "Content-Type" and "Content-Transfer-Encoding" to what is received in the
                            // HTTP header since it may not be set in the received mime body part
                            receivedPart.setHeader(MimeUtil.MIME_CONTENT_TYPE_KEY, receivedContentType.toString());

                        } else {
                        // We only need the Content-Type to rebuild the mime body part.
                            InternetHeaders ih = new InternetHeaders();
                            ih.setHeader(MimeUtil.MIME_CONTENT_TYPE_KEY, msg.getHeader(MimeUtil.MIME_CONTENT_TYPE_KEY));
                            receivedPart = new MimeBodyPart(ih, data);
                        }
                        if (LOG.isTraceEnabled() && "true".equalsIgnoreCase(System.getProperty("logRxdMsgMimeBodyParts", "false"))) {
                            LOG.trace("Received MimeBodyPart for inbound message: " + msg.getLogMsgID() + "\n" + MimeUtil.toString(receivedPart, true));
                        }
                        // Set the transfer encoding if necessary
                        String cte = receivedPart.getEncoding();
                        if (cte == null) {
                            // Not in the MimeBodyPart so try the HTTP headers...
                            cte = msg.getHeader("Content-Transfer-Encoding");
                            // Nada ... set to system default
                            if (cte == null) {
                                cte = Session.DEFAULT_CONTENT_TRANSFER_ENCODING;
                            }
                            receivedPart.setHeader("Content-Transfer-Encoding", cte);
                        } else if (LOG.isTraceEnabled()) {
                            LOG.trace("Received msg MimePart has transfer encoding: " + cte + msg.getLogMsgID());
                        }

                        msg.setData(receivedPart);
                    } catch (Exception e) {
                        msg.setLogMsg("Error extracting received message.");
                        LOG.error(msg.getLogMsg(), e);
                        throw new DispositionException(new DispositionType("automatic-action", "MDN-sent-automatically", "processed", "Error", "unexpected-processing-error"), AS2ReceiverModule.DISP_PARSING_MIME_FAILED, e);
                    }

                    // Check if request is forwarded by a reverse proxy
                    String sourceIpAddress = msg.getXForwardedFor();
                    if (sourceIpAddress == null) {
                        sourceIpAddress = msg.getXRealIP();
                    }
                    if (sourceIpAddress != null) {
                        LOG.info(msg.getLogMsgID() + " AS2 message has been forwarded by the proxy " + msg.getAttribute(NetAttribute.MA_SOURCE_IP) + ", the original server IP address is " + sourceIpAddress);
                        msg.setAttribute(NetAttribute.MA_SOURCE_IP, sourceIpAddress);
                    }

                    // Extract AS2 ID's from header, find the message's partnership and update the
                    // message
                    try {
                        msg.getPartnership().setSenderID(Partnership.PID_AS2, msg.getHeader("AS2-From"));
                        msg.getPartnership().setReceiverID(Partnership.PID_AS2, msg.getHeader("AS2-To"));

                        getModule().getSession().getPartnershipFactory().updatePartnership(msg, false);
                    } catch (OpenAS2Exception oae) {
                        throw new DispositionException(new DispositionType("automatic-action", "MDN-sent-automatically", "processed", "Error", "authentication-failed"), AS2ReceiverModule.DISP_PARTNERSHIP_NOT_FOUND, oae);
                    }
                    // Logger significant msg state
                    msg.setOption("STATE", Message.MSG_STATE_RECEIVE_START);
                    msg.trackMsgState(getModule().getSession());
                    // Decrypt and verify signature of the data, and attach data to the message
                    mic = decryptAndVerify(msg);
                    try {
                        // Extract and Store the received filename of the payload
                        String filename = msg.extractPayloadFilename();
                        // check for a fallback if not able to extract from content-disposition
                        if (filename == null || filename.length() == 0) {
                            filename = Properties.getProperty(Properties.AS2_RX_MESSAGE_FILENAME_FALLBACK, null);
                            if (filename == null) {
                                filename = msg.getMessageID();
                            } else {
                                CompositeParameters parser = new CompositeParameters(false).add("date", new DateParameters()).add("msg", new MessageParameters(msg)).add("rand", new RandomParameters());
                                filename = ParameterParser.parse(filename, parser);
                            }
                        }
                        msg.setPayloadFilename(filename);
                    } catch (ParseException e1) {
                        LOG.error("Failed to extract the file name from received content-disposition", e1);
                    }

                    // Process the received message
                    try {
                        Map<String, Object> options = new HashMap<String, Object>(1);
                        options.put(Partnership.PA_STORE_RECEIVED_FILE_TO, msg.getPartnership().getAttribute(Partnership.PA_STORE_RECEIVED_FILE_TO));
                        getModule().getSession().getProcessor().handle(StorageModule.DO_STORE, msg, options);
                    } catch (OpenAS2Exception oae) {
                        msg.setLogMsg("Error handling received message: " + oae.getCause());
                        LOG.error(msg.getLogMsg(), oae);
                        // Logger significant msg state
                        msg.setOption("STATE", Message.MSG_STATE_RECEIVE_EXCEPTION);
                        msg.trackMsgState(getModule().getSession());

                        throw new DispositionException(new DispositionType("automatic-action", "MDN-sent-automatically", "processed", "Error", "unexpected-processing-error"), AS2ReceiverModule.DISP_STORAGE_FAILED, oae);
                    }

                    // Transmit a success MDN if requested
                    try {
                        if (msg.isRequestingMDN()) {
                            // Logger significant msg state
                            msg.setOption("STATE", Message.MSG_STATE_MDN_SEND_START);
                            msg.trackMsgState(getModule().getSession());
                            boolean sentMDN = sendResponse(msg, out, new DispositionType("automatic-action", "MDN-sent-automatically", "processed"), mic, AS2ReceiverModule.DISP_SUCCESS);
                            if (!sentMDN) {
                                // Not sure what to do here as the AS2 spec does not specify so log warning for now
                                msg.setOption("STATE", Message.MSG_STATE_MSG_RXD_MDN_SENDING_FAIL);
                                msg.trackMsgState(getModule().getSession());

                                if (LOG.isWarnEnabled()) {
                                    LOG.warn("Received message processed but MDN could not be sent for Message-ID: " + msg.getMessageID());
                                }
                            } else {
                                msg.setOption("STATE", Message.MSG_STATE_MSG_RXD_MDN_SENT_OK);
                                msg.trackMsgState(getModule().getSession());
                            }
                        } else {
                            HTTPUtil.sendHTTPResponse(out, HttpURLConnection.HTTP_OK, null);
                            out.flush();
                            msg.setOption("STATE", Message.MSG_STATE_MSG_RXD_MDN_NOT_REQUESTED);
                            msg.trackMsgState(getModule().getSession());
                            LOG.info("Msg received, no MDN requested. Sent HTTP OK" + getClientInfo(s) + msg.getLogMsgID());
                        }
                    } catch (Exception e) {
                        msg.setLogMsg("Error processing MDN for received message: " + e.getCause());
                        LOG.error(msg.getLogMsg(), e);
                        // Logger significant msg state
                        msg.setOption("STATE", Message.MSG_STATE_MDN_SENDING_EXCEPTION);
                        msg.trackMsgState(getModule().getSession());
                        throw new WrappedException("Error creating and returning MDN, message was still processed", e);
                    }

                } catch (DispositionException de) {
                    sendResponse(msg, out, de.getDisposition(), mic, de.getText());
                    getModule().handleError(msg, de);
                } catch (OpenAS2Exception oae) {
                    // Logger significant msg state
                    msg.setOption("STATE", Message.MSG_STATE_RECEIVE_FAIL);
                    msg.trackMsgState(getModule().getSession());
                    getModule().handleError(msg, oae);
                }
            }
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {
                    msg.setLogMsg("Failed to close output connection.");
                    LOG.error(msg.getLogMsg(), e);
                }
            }
        }
    }

    // Create a new message and record the source ip and port
    protected AS2Message createMessage(Socket s) {
        AS2Message msg = new AS2Message();

        msg.setAttribute(NetAttribute.MA_SOURCE_IP, s.getInetAddress().toString());
        msg.setAttribute(NetAttribute.MA_SOURCE_PORT, Integer.toString(s.getPort()));
        msg.setAttribute(NetAttribute.MA_DESTINATION_IP, s.getLocalAddress().toString());
        msg.setAttribute(NetAttribute.MA_DESTINATION_PORT, Integer.toString(s.getLocalPort()));

        return msg;
    }

    protected String decryptAndVerify(AS2Message msg) throws OpenAS2Exception {
        CertificateFactory certFx = getModule().getSession().getCertificateFactory(CertificateFactory.COMPID_AS2_CERTIFICATE_FACTORY);
        ICryptoHelper ch;
        String mic = null;

        try {
            ch = AS2Util.getCryptoHelper();
        } catch (Exception e) {
            throw new WrappedException(e);
        }
        // Per RFC5402 compression is always before encryption but can be before or
        // after signing of message but only in one place
        boolean isDecompressed = false;

        try {
            if (ch.isEncrypted(msg.getData())) {
                msg.setRxdMsgWasEncrypted(true);
                // Decrypt
                if (LOG.isDebugEnabled()) {
                    LOG.debug("decrypting :::" + msg.getLogMsgID());
                }
                String x509_alias = msg.getPartnership().getAlias(Partnership.PTYPE_RECEIVER);
                X509Certificate receiverCert = certFx.getCertificate(x509_alias);
                PrivateKey receiverKey = certFx.getPrivateKey(x509_alias);
                try {
                    msg.setData(AS2Util.getCryptoHelper().decrypt(msg.getData(), receiverCert, receiverKey));
                    msg.setReceiverX509Alias(x509_alias);
                } catch (Exception e) {
                    // Something went wrong - possibly a certificate change so try the backup if configured
                    String x509_alias_fallback = msg.getPartnership().getAliasFallback(Partnership.PTYPE_RECEIVER);
                    if (x509_alias_fallback == null) {
                        // No fallback so just throw the original exception
                        throw e;
                    }
                    receiverCert = certFx.getCertificate(x509_alias_fallback);
                    receiverKey = certFx.getPrivateKey(x509_alias_fallback);
                    msg.setData(AS2Util.getCryptoHelper().decrypt(msg.getData(), receiverCert, receiverKey));
                    // success so the sender must have updated the receiver certificate
                    msg.setReceiverX509Alias(x509_alias_fallback);
                    // TODO: Automatically switch the alias in the partnerships.xml file and remove the fallback
                    // Send a message so that the certificate can be updated.
                    LOG.warn("Partner has updated our certificate. Switch the fallback alias and remove the X509 fallback for the partner: " + msg.getPartnership().getReceiverID(Partnership.PID_NAME));
                }
                if (LOG.isTraceEnabled() && "true".equalsIgnoreCase(System.getProperty("logRxdMsgMimeBodyParts", "false"))) {
                    LOG.trace("Received MimeBodyPart for inbound message after decryption: " + msg.getLogMsgID() + "\n" + MimeUtil.toString(msg.getData(), true));
                }
            }
        } catch (Exception e) {
            msg.setLogMsg("Error extracting received message: " + e.getCause());
            LOG.error(msg.getLogMsg(), e);
            throw new DispositionException(new DispositionType("automatic-action", "MDN-sent-automatically", "processed", "Error", "decryption-failed"), AS2ReceiverModule.DISP_DECRYPTION_ERROR, e);
        }

        try {
            if (ch.isCompressed(msg.getData())) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Decompressing received message before checking signature...");
                }
                AS2Util.getCryptoHelper().decompress(msg);
                isDecompressed = true;
                if (LOG.isTraceEnabled() && "true".equalsIgnoreCase(System.getProperty("logRxdMsgMimeBodyParts", "false"))) {
                    LOG.trace("Received MimeBodyPart for inbound message after decompression: " + msg.getLogMsgID() + "\n" + MimeUtil.toString(msg.getData(), true));
                }
            }
        } catch (Exception e1) {
            msg.setLogMsg("Error decompressing received message: " + e1.getCause());
            LOG.error(msg.getLogMsg(), e1);
            throw new DispositionException(new DispositionType("automatic-action", "MDN-sent-automatically", "processed", "Error", "decompresion-failed"), AS2ReceiverModule.DISP_DECOMPRESSION_ERROR, e1);
        }

        try {
            if (ch.isSigned(msg.getData())) {
                msg.setRxdMsgWasSigned(true);
                if (LOG.isDebugEnabled()) {
                    LOG.debug("verifying signature" + msg.getLogMsgID());
                }
                String x509_alias = msg.getPartnership().getAlias(Partnership.PTYPE_SENDER);
                X509Certificate senderCert = certFx.getCertificate(x509_alias);
                try {
                    msg.setData(AS2Util.getCryptoHelper().verifySignature(msg.getData(), senderCert));
                    msg.setSenderX509Alias(x509_alias);
                } catch (Exception e) {
                    // Something went wrong - possibly a certificate change so try the backup if configured
                    String x509_alias_fallback = msg.getPartnership().getAliasFallback(Partnership.PTYPE_SENDER);
                    if (x509_alias_fallback == null) {
                        // No fallback so just throw the original exception
                        throw e;
                    }
                    senderCert = certFx.getCertificate(x509_alias_fallback);
                    msg.setData(AS2Util.getCryptoHelper().verifySignature(msg.getData(), senderCert));
                    msg.setSenderX509Alias(x509_alias_fallback);
                    // TODO: Automatically switch the alias in the partnerships.xml file and remove the fallback
                    // Send a message so that the certificate can be updated.
                    LOG.warn("Partner has updated their certificate. Switch the fallback alias and remove the X509 fallback for the partner: " + msg.getPartnership().getSenderID(Partnership.PID_NAME));
                }
                if (LOG.isTraceEnabled() && "true".equalsIgnoreCase(System.getProperty("logRxdMsgMimeBodyParts", "false"))) {
                    LOG.trace("Received MimeBodyPart for inbound message after signature verification: " + msg.getLogMsgID() + "\n" + MimeUtil.toString(msg.getData(), true));
                }
            }
        } catch (Exception e) {
            msg.setLogMsg("Error decrypting received message: " + org.openas2.util.Logging.getExceptionMsg(e));
            LOG.error(msg.getLogMsg(), e);
            throw new DispositionException(new DispositionType("automatic-action", "MDN-sent-automatically", "processed", "Error", "integrity-check-failed"), AS2ReceiverModule.DISP_VERIFY_SIGNATURE_FAILED, e);
        }
        if (!msg.isRxdMsgWasSigned() && msg.getPartnership().isRejectUnsignedMessages()) {
            // Configured to reject unsigned messages and this was not signed so...
            throw new DispositionException(
                    new DispositionType("automatic-action", "MDN-sent-automatically", "processed", "Error", "signed-message-required"),
                    AS2ReceiverModule.DISP_ONLY_SIGNED_MESSAGES
            );
        }
        if (LOG.isTraceEnabled()) {
            try {
                LOG.trace("SMIME Decrypted Content-Disposition: " + msg.getContentDisposition() + "\n      Content-Type received: " + msg.getContentType() + "\n      HEADERS after decryption: " + msg.getData().getAllHeaders() + "\n      Content-Disposition in MSG getData() MIMEPART after decryption: " + msg.getData().getContentType());
            } catch (MessagingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        /*
         * Calculate the MIC after signing or encryption of the message but prior to
         * doing any decompression but include headers for unsigned messages (see
         * RFC4130 section 7.3.1 for details)
         */
        DispositionOptions dispOptions = new DispositionOptions(msg.getHeader("Disposition-Notification-Options"));
        if (LOG.isDebugEnabled()) {
            LOG.debug("Received Disposition-Notification-Options header value: " + dispOptions);
        }
        if (dispOptions.getMicalg() != null) {
            try {
                mic = ch.calculateMIC(msg.getData(), dispOptions.getMicalg(), (msg.isRxdMsgWasSigned() || msg.isRxdMsgWasEncrypted()), msg.getPartnership().isPreventCanonicalization());
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Prevent Canonicalization: " + msg.getPartnership().isPreventCanonicalization() + " ::: MIC calc on rxd msg: " + mic);
                }
                if (LOG.isTraceEnabled()) {
                    // Generate some alternative MIC's to see if the partner is somehow using a
                    // different default
                    String tmic = ch.calculateMIC(msg.getData(), dispOptions.getMicalg(), (msg.isRxdMsgWasSigned() || msg.isRxdMsgWasEncrypted()), !msg.getPartnership().isPreventCanonicalization());
                    LOG.trace("MIC with forced reversed prevent canocalization: " + tmic + msg.getLogMsgID());
                    tmic = ch.calculateMIC(msg.getData(), dispOptions.getMicalg(), false, msg.getPartnership().isPreventCanonicalization());
                    LOG.trace("MIC with forced exclude headers flag: " + tmic + msg.getLogMsgID());

                }
            } catch (Exception e) {
                msg.setLogMsg("Error calculating MIC on received message: " + e.getCause());
                LOG.error(msg.getLogMsg(), e);
                throw new DispositionException(new DispositionType("automatic-action", "MDN-sent-automatically", "processed", "Error", "unexpected-processing-error"), AS2ReceiverModule.DISP_CALC_MIC_FAILED, e);
            }
        }

        // Per RFC5402 compression is always before encryption but can be before or
        // after signing of message but only in one place
        try {
            if (ch.isCompressed(msg.getData())) {
                if (isDecompressed) {
                    throw new DispositionException(new DispositionType("automatic-action", "MDN-sent-automatically", "processed", "Error", "decompression-failed"), AS2ReceiverModule.DISP_DECOMPRESSION_ERROR, new Exception("Message has already been decompressed. Per RFC5402 it cannot occur twice."));
                }
                if (LOG.isTraceEnabled()) {
                    LOG.trace("Decompressing received message after decryption...");
                }
                AS2Util.getCryptoHelper().decompress(msg);
            }
        } catch (Exception e) {
            msg.setLogMsg("Unexepcted error checking for compressed message after signing");
            LOG.error(msg.getLogMsg(), e);
            throw new DispositionException(new DispositionType("automatic-action", "MDN-sent-automatically", "processed", "Error", "decompression-failed"), AS2ReceiverModule.DISP_DECOMPRESSION_ERROR, new Exception("Unexpected error occurred checking for compressed message: " + e.getMessage()));
        }
        return mic;
    }

    /**
     * Sends a response for received AS2 message. If sending an MDN is enabled then
     * sets up MDN object and invokes the
     * {@link org.openas2.processor.sender.MDNSenderModule}
     *
     * @param msg         The received message that an MDN must be sent for.
     * @param out         The output stream for the connection the AS2 message
     *                    was received on
     * @param disposition The disposition type that must be sent in the MDN
     * @param mic         The MIC of the received AS2 message
     * @param text        The textual message describing the result of the
     *                    message receipt and processing
     * @return Returns true if an response was sent
     */
    protected boolean sendResponse(AS2Message msg, BufferedOutputStream out, DispositionType disposition, String mic, String text) {
        boolean mdnBlocked = false;

        mdnBlocked = (msg.getPartnership().getAttribute(Partnership.PA_BLOCK_ERROR_MDN) != null);

        if (!mdnBlocked) {

            try {
                createMDN(getModule().getSession(), msg, mic, disposition, text);
            } catch (Exception e1) {
                // Maybe should construct error disposition and try to send but ....
                try {
                    HTTPUtil.sendHTTPResponse(out, HttpURLConnection.HTTP_INTERNAL_ERROR, null);
                } catch (IOException e) {
                    if (LOG.isErrorEnabled()) {
                        LOG.error("Error sending HTTP_INTERNAL_ERROR response. " + msg.getLogMsgID(), e);
                    }
                    return false;
                }
                WrappedException we = new WrappedException("Error creating MDN", e1);
                we.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
                we.log();
                msg.setLogMsg("Unexpected error occurred creating MDN: " + org.openas2.util.Logging.getExceptionMsg(e1));
                LOG.error(msg.getLogMsg(), e1);
                return false;
            }
            try {
                Map<String, Object> options = new HashMap<String, Object>();
                options.put("buffered_output_stream", out);
                // Set up retry counts in case this partner uses Async MDN responses
                int maxResendCount = AS2Util.getMaxResendCount(getModule().getSession(), msg);
                msg.setOption(ResenderModule.OPTION_MAX_RETRY_COUNT, maxResendCount);
                msg.setOption(ResenderModule.OPTION_RETRIES, "0");
                getModule().getSession().getProcessor().handle(SenderModule.DO_SENDMDN, msg, options);
            } catch (Exception e) {
                WrappedException we = new WrappedException("Error sending MDN", e);
                we.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
                we.log();
                msg.setLogMsg("Unexpected error occurred sending MDN: " + org.openas2.util.Logging.getExceptionMsg(e));
                LOG.error(msg.getLogMsg(), e);
                return false;
            }
        } else {
            try {
                HTTPUtil.sendHTTPResponse(out, HttpURLConnection.HTTP_OK, null);
            } catch (IOException e) {
                if (LOG.isErrorEnabled()) {
                    LOG.error("Error sending HTTP OK response. " + msg.getLogMsgID(), e);
                }
                return false;
            }
        }
        return true;
    }

    public MessageMDN createMDN(Session session, AS2Message msg, String mic, DispositionType disposition, String text) throws Exception {

        AS2MessageMDN mdn = new AS2MessageMDN(msg, false);

        mdn.setHeader("AS2-Version", "1.1");
        // RFC2822 format: Wed, 04 Mar 2009 10:59:17 +0100
        mdn.setHeader("Date", DateUtil.formatDate("EEE, dd MMM yyyy HH:mm:ss Z"));
        mdn.setHeader(HTTPUtil.HEADER_CONNECTION, "close, TE");
        String userAgent = Properties.getProperty(Properties.HTTP_USER_AGENT_PROP, msg.getAppTitle());
        mdn.setHeader(HTTPUtil.HEADER_USER_AGENT, userAgent);
        mdn.setHeader("Server", userAgent);
        mdn.setHeader("Mime-Version", "1.0");

        // get the MDN partnership info
        // not sure that it should be this way since the config should relfect the
        // inbound original message settings but ...
        mdn.getPartnership().setSenderID(Partnership.PID_AS2, mdn.getHeader("AS2-From"));
        mdn.getPartnership().setReceiverID(Partnership.PID_AS2, mdn.getHeader("AS2-To"));
        session.getPartnershipFactory().updatePartnership(mdn, true);

        mdn.setHeader("From", msg.getPartnership().getReceiverID(Partnership.PID_EMAIL));
        String subject = mdn.getPartnership().getAttribute(Partnership.PA_SUBJECT);

        if (subject != null) {
            mdn.setHeader("Subject", ParameterParser.parse(subject, new MessageParameters(msg)));
        } else {
            mdn.setHeader("Subject", "Your Requested MDN Response re: " + mdn.getMessage().getSubject());
        }
        mdn.setText(ParameterParser.parse(text, new MessageParameters(msg)));
        mdn.setAttribute(AS2MessageMDN.MDNA_REPORTING_UA, userAgent + "@" + msg.getAttribute(NetAttribute.MA_DESTINATION_IP) + ":" + msg.getAttribute(NetAttribute.MA_DESTINATION_PORT));
        mdn.setAttribute(AS2MessageMDN.MDNA_ORIG_RECIPIENT, "rfc822; " + msg.getHeader("AS2-To"));
        mdn.setAttribute(AS2MessageMDN.MDNA_FINAL_RECIPIENT, "rfc822; " + msg.getPartnership().getReceiverID(Partnership.PID_AS2));
        mdn.setAttribute(AS2MessageMDN.MDNA_ORIG_MESSAGEID, msg.getHeader("Message-ID"));
        mdn.setAttribute(AS2MessageMDN.MDNA_DISPOSITION, disposition.toString());

        DispositionOptions dispOptions = new DispositionOptions(msg.getHeader("Disposition-Notification-Options"));

        mdn.setAttribute(AS2MessageMDN.MDNA_MIC, mic);
        createMDNData(session, mdn, dispOptions.getMicalg(), dispOptions.getProtocol());

        mdn.updateMessageID();

        // store MDN into msg in case AsynchMDN is sent fails, needs to be resent by
        // send module
        msg.setMDN(mdn);
        return mdn;
    }

    public void createMDNData(Session session, MessageMDN mdn, String micAlg, String signatureProtocol) throws Exception {
        // Create the text part
        MimeBodyPart textPart = new MimeBodyPart();
        String text = mdn.getText() + "\r\n";
        textPart.setContent(text, "text/plain");
        textPart.setHeader(MimeUtil.MIME_CONTENT_TYPE_KEY, "text/plain");
        boolean unfoldHeaders = "true".equalsIgnoreCase(mdn.getPartnership().getAttributeOrProperty("remove_http_header_folding", "true"));

        // Create the report part
        MimeBodyPart reportMetaPart = new MimeBodyPart();
        InternetHeaders reportValues = new InternetHeaders();
        reportValues.setHeader("Reporting-UA", mdn.getAttribute(AS2MessageMDN.MDNA_REPORTING_UA));
        reportValues.setHeader("Original-Recipient", mdn.getAttribute(AS2MessageMDN.MDNA_ORIG_RECIPIENT));
        reportValues.setHeader("Final-Recipient", mdn.getAttribute(AS2MessageMDN.MDNA_FINAL_RECIPIENT));
        reportValues.setHeader("Original-Message-ID", mdn.getAttribute(AS2MessageMDN.MDNA_ORIG_MESSAGEID));
        reportValues.setHeader("Disposition", mdn.getAttribute(AS2MessageMDN.MDNA_DISPOSITION));
        reportValues.setHeader("Received-Content-MIC", mdn.getAttribute(AS2MessageMDN.MDNA_MIC));
        Enumeration<String> reportEn = reportValues.getAllHeaderLines();
        StringBuffer reportData = new StringBuffer();
        while (reportEn.hasMoreElements()) {
            reportData.append(reportEn.nextElement()).append("\r\n");
        }
        reportData.append("\r\n");
        String reportText = reportData.toString();
        reportMetaPart.setContent(reportText, AS2Standards.DISPOSITION_TYPE);
        reportMetaPart.setHeader(MimeUtil.MIME_CONTENT_TYPE_KEY, AS2Standards.DISPOSITION_TYPE);

        // Create the multipart to hold the report parts
        MimeMultipart reportMultiPart = new MimeMultipart(AS2Standards.REPORT_SUBTYPE, textPart, reportMetaPart);
        MimeBodyPart reportPart = new MimeBodyPart();
        reportPart.setContent(reportMultiPart);
        //IMPORTANT: Set the Content-Type AFTER setting the content as setContent() clears the Content-Type of the BodyPart
        reportPart.setHeader(MimeUtil.MIME_CONTENT_TYPE_KEY, unfoldHeaders?MimeUtility.unfold(reportMultiPart.getContentType()):reportMultiPart.getContentType());
        // Sign the data if needed
        if (signatureProtocol != null) {
            CertificateFactory certFx = session.getCertificateFactory(CertificateFactory.COMPID_AS2_CERTIFICATE_FACTORY);

            try {
                // The receiver of the original message is the sender of the MDN - sign with the receivers private key
                String x509_alias = mdn.getPartnership().getAlias(Partnership.PTYPE_RECEIVER);
                X509Certificate senderCert = certFx.getCertificate(x509_alias);
                PrivateKey senderKey = certFx.getPrivateKey(x509_alias);
                Partnership p = mdn.getPartnership();
                String contentTxfrEncoding = p.getAttribute(Partnership.PA_CONTENT_TRANSFER_ENCODING);
                boolean isRemoveCmsAlgorithmProtectionAttr = "true".equalsIgnoreCase(p.getAttribute(Partnership.PA_REMOVE_PROTECTION_ATTRIB));
                if (contentTxfrEncoding == null) {
                    contentTxfrEncoding = Session.DEFAULT_CONTENT_TRANSFER_ENCODING;
                }
                // sign the data using CryptoHelper
                MimeBodyPart signedReport = AS2Util.getCryptoHelper().sign(reportPart, senderCert, senderKey, micAlg, contentTxfrEncoding, false, isRemoveCmsAlgorithmProtectionAttr);
                if (unfoldHeaders) {
                    signedReport.setHeader(MimeUtil.MIME_CONTENT_TYPE_KEY, MimeUtility.unfold(signedReport.getContentType()));
                }
                mdn.setData(signedReport);
            } catch (CertificateNotFoundException cnfe) {
                cnfe.log();
                mdn.setData(reportPart);
            } catch (KeyNotFoundException knfe) {
                knfe.log();
                mdn.setData(reportPart);
            }
        } else {
            mdn.setData(reportPart);
        }
        mdn.setHeader(MimeUtil.MIME_CONTENT_TYPE_KEY, mdn.getData().getContentType());
    }

}
