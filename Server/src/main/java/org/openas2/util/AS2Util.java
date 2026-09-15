package org.openas2.util;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openas2.ComponentNotFoundException;
import org.openas2.DispositionException;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.cert.CertificateFactory;
import org.openas2.lib.helper.BCCryptoHelper;
import org.openas2.lib.helper.ICryptoHelper;
import org.openas2.lib.message.AS2Standards;
import org.openas2.lib.util.MimeUtil;
import org.openas2.message.*;
import org.openas2.params.*;
import org.openas2.partner.Partnership;
import org.openas2.processor.Processor;
import org.openas2.processor.msgtracking.BaseMsgTrackingModule;
import org.openas2.processor.receiver.MessageBuilderModule;
import org.openas2.processor.resender.ResenderModule;
import org.openas2.processor.sender.SenderModule;
import org.openas2.processor.storage.StorageModule;

import jakarta.mail.Header;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.ContentType;
import jakarta.mail.internet.InternetHeaders;
import jakarta.mail.internet.MimeBodyPart;
import jakarta.mail.internet.MimeMultipart;

import java.io.*;
import java.net.HttpURLConnection;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AS2Util {

    /*
     * Emitted whenever a fallback certificate turns out to be the one that worked, from either
     * direction, so a single log search finds every certificate overlap that is still in progress.
     * The text is unchanged from when only the inbound paths reported this.
     */
    public static final String LOG_MSG_OUR_CERT_SWITCHED = "Partner has updated our certificate. Switch the fallback alias and remove the X509 fallback for the partner: ";
    public static final String LOG_MSG_PARTNER_CERT_SWITCHED = "Partner has updated their certificate. Switch the fallback alias and remove the X509 fallback for the partner: ";

    private static ICryptoHelper ch;

    public static ICryptoHelper getCryptoHelper() throws Exception {
        if (ch == null) {
            ch = new BCCryptoHelper();
            ch.initialize();
        }

        return ch;
    }

    public static String generateMessageID(Message msg, boolean isMDN) throws InvalidParameterException {
        String idFormat = null;
        CompositeParameters params = new CompositeParameters(
                false).add("date",
                new DateParameters()).add("msg",
                new MessageParameters(msg)).add("rand",new RandomParameters()
        );
        if (isMDN) {
            params.add("mdn", new MessageMDNParameters(msg.getMDN()));
            idFormat = msg.getPartnership().getAttributeOrProperty(Properties.AS2_MDN_MESSAGE_ID_FORMAT, null);
        }
        if (idFormat == null) {
            idFormat = msg.getPartnership().getAttributeOrProperty(
                Properties.AS2_MESSAGE_ID_FORMAT,
                "<OPENAS2-$date.ddMMyyyyHHmmssZ$-$rand.UUID$@$msg.sender.as2_id$_$msg.receiver.as2_id$>"
            );
        }
        String id = ParameterParser.parse(idFormat, params);
        // RFC822 requires enclosing message in <> but AS2 spec provides for this to be
        // overridden
        String isEncap = msg.getPartnership().getAttributeOrProperty(Properties.AS2_MESSAGE_ID_ENCLOSE_IN_BRACKETS, "true");
        if ("true".equalsIgnoreCase(isEncap)) {
            // Add the angle brackets if not already added
            if (!id.startsWith("<")) {
                id = "<" + id;
            }
            if (!id.endsWith(">")) {
                id = id + ">";
            }
        }
        return id;
    }

    /**
     * @param msg- the AS2 message that is being processed
     * @param receiver - the receivers X509 certificate
     * @return - a boolean indicating if the extracted response indicated an issue processing the AS2 message that was sent. Message state is NOT updated in this method.
     * @throws OpenAS2Exception - thrown if there are issues trying to extract the response from the partner
     */
    public static boolean parseMDN(Message msg, X509Certificate receiver) throws OpenAS2Exception {
        Logger logger = LoggerFactory.getLogger(AS2Util.class);
        MessageMDN mdn = msg.getMDN();
        MimeBodyPart mainPart = mdn.getData();
        if (logger.isTraceEnabled() && "true".equalsIgnoreCase(System.getProperty("logRxdMdnMimeBodyParts", "false"))) {
            try {
                logger.trace("Received MimeBodyPart for inbound MDN: " + msg.getLogMsgID() + "\n" + MimeUtil.toString(mainPart, true));
            } catch (Exception e) {
                logger.trace("Failed to log the MimeBodyPart as part of trace logging: " + e.getMessage());
            }
        }
        try {
            ICryptoHelper ch = getCryptoHelper();

            if (ch.isSigned(mainPart)) {
                // the signature verifier will return the signed content as a MimeBodyPart
                mainPart = getCryptoHelper().verifySignature(mainPart, receiver);
            }
        } catch (Exception e1) {
            logger.error("Error parsing MDN: " + org.openas2.util.Logging.getExceptionMsg(e1), e1);
            throw new OpenAS2Exception("Failed to verify signature of received MDN.");
        }

        try {
            MimeMultipart reportParts = new MimeMultipart(mainPart.getDataHandler().getDataSource());

            if (reportParts != null && reportParts.getCount() > 0) {
                ContentType reportType = new ContentType(reportParts.getContentType());
                Charset charset = getCharset(reportType, msg, logger);

                if (reportType.getBaseType().equalsIgnoreCase("multipart/report")) {
                    int reportCount = reportParts.getCount();
                    MimeBodyPart reportPart;

                    for (int j = 0; j < reportCount; j++) {
                        reportPart = (MimeBodyPart) reportParts.getBodyPart(j);
                        if (logger.isTraceEnabled() && "true".equalsIgnoreCase(System.getProperty("logRxdMdnMimeBodyParts", "false"))) {
                            logger.trace("Report MimeBodyPart from Multipart for inbound MDN: " + msg.getLogMsgID() + "\n" + MimeUtil.toString(reportPart, true));
                        }

                        if (reportPart.isMimeType("text/plain")) {
                            mdn.setText(getMimeBodyPartText(reportPart, charset));
                        } else if (reportPart.isMimeType(AS2Standards.DISPOSITION_TYPE)) {
                            InternetHeaders headers = new InternetHeaders(reportPart.getInputStream());
                            mdn.setAttribute(AS2MessageMDN.MDNA_REPORTING_UA, headers.getHeader("Reporting-UA", ", "));
                            mdn.setAttribute(AS2MessageMDN.MDNA_ORIG_RECIPIENT, headers.getHeader("Original-Recipient", ", "));
                            mdn.setAttribute(AS2MessageMDN.MDNA_FINAL_RECIPIENT, headers.getHeader("Final-Recipient", ", "));
                            mdn.setAttribute(AS2MessageMDN.MDNA_ORIG_MESSAGEID, headers.getHeader("Original-Message-ID", ", "));
                            mdn.setAttribute(AS2MessageMDN.MDNA_DISPOSITION, headers.getHeader("Disposition", ", "));
                            mdn.setAttribute(AS2MessageMDN.MDNA_MIC, headers.getHeader("Received-Content-MIC", ", "));
                        }
                    }
                } else {
                    // No multipart/report so now what?
                    logger.warn("MDN received from partner but did not contain a multipart/report section. " + msg.getLogMsgID());
                    int reportCount = reportParts.getCount();
                    MimeBodyPart reportPart;
                    for (int j = 0; j < reportCount; j++) {
                        reportPart = (MimeBodyPart) reportParts.getBodyPart(j);
                        logger.warn("Received MimeBodyPart from Multipart for inbound MDN: " + msg.getLogMsgID() + "\n" + MimeUtil.toString(reportPart, true));
                    }
                    return false;
                }
            } else {
                logger.error("The received MimeBodyPart for inbound MDN did not contain a standard MDN response. This is probably because an error occurred on the remote side processing the message. Review the response below for possible reasons: " + msg.getLogMsgID() + "\n" + MimeUtil.toString(mainPart, true));                
                return false;
            }
        } catch (Exception e) {
            try {
                logger.error("Failed to extract report from received MimeBodyPart for inbound MDN: " + msg.getLogMsgID() + "\n" + MimeUtil.toString(mainPart, true));
            } catch (Exception e1) {
                // Do nothing
            }
            throw new OpenAS2Exception("Failed to parse MDN: " + org.openas2.util.Logging.getExceptionMsg(e), e);
        }
        return true;
    }

    private static String getMimeBodyPartText(MimeBodyPart part, Charset charset) throws MessagingException, IOException {
        Object content = part.getContent();

        if (InputStream.class.isAssignableFrom(content.getClass())) {
            return IOUtils.toString((InputStream) content, charset);
        }

        return content.toString();
    }

    private static Charset getCharset(ContentType contentType, Message msg, Logger logger) {
        Charset charset = StandardCharsets.UTF_8;
        String charsetFromContentType = contentType.getParameter("charset");

        if (charsetFromContentType != null) {
            try {
                charset = Charset.forName(charsetFromContentType);
            } catch (Exception e) {
                // Just warn and allow the default to go through
                logger.warn("Received charset string that cannot be parsed: " + charsetFromContentType + ", MSG_ID=" + msg.getLogMsgID());
            }
        }

        return charset;
    }

    /**
     * Verify disposition status is "processed" then check MIC is matched
     *
     * @param msg - the original message sent to the partner that the MDN
     *            relates to
     * @return true if mdn processed
     * @throws DispositionException - something wrong with the Disposition
     *                              structure
     * @throws OpenAS2Exception     - an internally handled error has occurred
     */
    public static boolean checkMDN(AS2Message msg) throws DispositionException, OpenAS2Exception {
        Logger logger = LoggerFactory.getLogger(AS2Util.class);
        /*
         * The sender may return an error in the disposition and not set the MIC so
         * check disposition first
         */
        String disposition = msg.getMDN().getAttribute(AS2MessageMDN.MDNA_DISPOSITION);
        boolean dispositionHasWarning = false;
        if (disposition != null) {
            if (logger.isInfoEnabled()) {
                logger.info("Partner " + msg.getPartnership().getReceiverID(Partnership.PID_AS2) + " responded with MDN [" + disposition + "]" + msg.getLogMsgID());
            }
            DispositionType dt = null;
            try {
                dt = new DispositionType(disposition);
            } catch (OpenAS2Exception e) {
                msg.setLogMsg("Error occurred instantating a Disposition object from received disposition: " + org.openas2.util.Logging.getExceptionMsg(e));
                logger.error(msg.getLogMsg(), e);
                throw new OpenAS2Exception(e);
            }
            try {
                dt.validate();
            } catch (DispositionException de) {
                if (logger.isWarnEnabled()) {
                    logger.warn("Disposition error detected in MDN. Received disposition: " + disposition + msg.getLogMsgID());
                }
                // Something wrong detected so flag it for later use
                dispositionHasWarning = true;
                de.setText(msg.getMDN().getText());

                if ((de.getDisposition() != null) && de.getDisposition().isWarning()) {
                    // Do not throw error in this case ... just log it
                    de.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
                    de.log();
                } else {
                    throw de;
                }
            }
        }

        if ("none".equalsIgnoreCase(msg.getPartnership().getAttribute(Partnership.PA_AS2_MDN_OPTIONS))) {
            // signed MDN not requested so...
            return true;
        }
        if (logger.isTraceEnabled()) {
            logger.trace("MIC processing start... "  + msg.getLogMsgID());
        }
        // get the returned mic from mdn object
        String returnMIC = msg.getMDN().getAttribute(AS2MessageMDN.MDNA_MIC);
        if (returnMIC == null || returnMIC.length() < 1) {
            if (dispositionHasWarning) {
                // TODO: Think this should probably throw error if MIC should have been returned
                // but for now...
                msg.setLogMsg("Returned MIC not found but disposition has warning so might be normal.");
                logger.warn(msg.getLogMsg());
            } else {
                msg.setLogMsg("Returned MIC not found so cannot validate returned message.");
                logger.error(msg.getLogMsg());
            }
            return false;
        }
        String calcMIC = msg.getCalculatedMIC();
        if (calcMIC == null) {
            throw new OpenAS2Exception("The claculated MIC was not retrieved from the message object.");
        }
        if (logger.isTraceEnabled()) {
            logger.trace("MIC check on calculated MIC: " + calcMIC + msg.getLogMsgID());
        }

        /*
         * Returned-Content-MIC header and rfc822 headers can contain spaces all over
         * the place. (not to mention comments!). Simple fix - delete all spaces. Since
         * the partner could return the algorithm in different case to what was sent,
         * remove the algorithm before compare The Algorithm is appended as a part of
         * the MIC by adding a comma then optionally a space followed by the algorithm
         */
        String regex = "^\\s*(\\S+)\\s*,\\s*(\\S+)\\s*$";
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(returnMIC);
        if (!m.find()) {
            msg.setLogMsg("Invalid MIC format in returned MIC: " + returnMIC);
            logger.error(msg.getLogMsg());
            throw new OpenAS2Exception("Invalid MIC string received. Forcing Resend");
        }
        String rMic = m.group(1);
        String rMicAlg = m.group(2);
        m = p.matcher(calcMIC);
        if (!m.find()) {
            msg.setLogMsg("Invalid MIC format in calculated MIC: " + calcMIC);
            logger.error(msg.getLogMsg());
            throw new OpenAS2Exception("Invalid MIC string retrieved from calculated MIC. Forcing Resend");
        }
        String cMic = m.group(1);
        String cMicAlg = m.group(2);

        if (!cMicAlg.equalsIgnoreCase(rMicAlg)) {
            // Appears not to match.... make sure dash is not the issue as in SHA-1 compared
            // to SHA1
            if (!cMicAlg.replaceAll("-", "").equalsIgnoreCase(rMicAlg.replaceAll("-", ""))) {
                /*
                 * RFC 6362 specifies that the sent attachments should be considered invalid and
                 * retransmitted
                 */
                String errmsg = "MIC algorithm returned by partner is not the same as the algorithm requested but must be the same per RFC4130 section 7.4.3. Original MIC alg: " + cMicAlg + " ::: returned MIC alg: " + rMicAlg + "\n        Ensure that Partner supports the requested algorithm and the \"" + Partnership.PA_AS2_MDN_OPTIONS + "\" attribute for the outbound partnership uses the same algorithm as the \" +" + Partnership.PA_SIGNATURE_ALGORITHM + "\" attribute.";
                throw new OpenAS2Exception(errmsg + " Forcing Resend");
            }
        }
        if (!cMic.equals(rMic)) {
            /*
             * RFC 6362 specifies that the sent attachments should be considered invalid and
             * retransmitted
             */
            msg.setLogMsg("MIC not matched, original MIC: " + calcMIC + " return MIC: " + returnMIC);
            logger.error(msg.getLogMsg());
            throw new OpenAS2Exception("MIC not matched. Forcing Resend");
        }
        if (logger.isTraceEnabled()) {
            logger.trace("MIC is matched, received MIC: " + returnMIC + msg.getLogMsgID());
        }
        return true;
    }

    /**
     * Flags a message to use the configured fallback certificate on its resend when the partner has
     * rejected it for a reason that a certificate rotation would explain.
     * <p>
     * Signing and encrypting both succeed locally whichever certificate is used, so unlike the inbound
     * side there is nothing to catch and retry in place: the only signal that the wrong certificate was
     * used is the partner saying so in the MDN disposition. A rejection is therefore mapped back to the
     * side whose certificate is implicated:
     * <ul>
     * <li>"decryption-failed" means the partner could not decrypt, so we encrypted to a certificate of
     * theirs that they no longer hold, and the receiver fallback should be used</li>
     * <li>"authentication-failed" or "integrity-check-failed" means the partner could not verify our
     * signature, so we signed with a certificate they do not have yet, and the sender fallback should
     * be used</li>
     * </ul>
     * Note that "integrity-check-failed" is reported by some implementations for a MIC mismatch rather
     * than a signature problem. Switching is therefore only done when a fallback is actually configured
     * for that side and only once per message, so a message that is failing for some other reason
     * behaves exactly as it did before.
     *
     * @param msg - the message that was rejected
     * @param de - the disposition exception carrying the partner's reported reason
     * @return true if the message was flagged to use a fallback certificate
     */
    /**
     * The message attribute that flags the fallback certificate for one side of a partnership. Keeping
     * the pairing in one place stops the side and the flag drifting apart between the code that decides
     * to switch and the code that acts on it.
     *
     * @param partnershipType - PTYPE_SENDER or PTYPE_RECEIVER
     * @return the attribute name for that side
     */
    private static String fallbackStateAttributeFor(String partnershipType) {
        return Partnership.PTYPE_SENDER.equals(partnershipType)
                ? Message.MA_SENDER_ALIAS_FALLBACK_STATE
                : Message.MA_RECEIVER_ALIAS_FALLBACK_STATE;
    }

    /**
     * Carries the fallback certificate state onto the message object a resend restored from disk.
     * <p>
     * A resend after the first one replaces the in-memory message with the one serialised when the
     * message was first sent, which predates any fallback decision. Without this the state would be
     * dropped and the resend would silently go back to the primary certificate while the logging said
     * otherwise.
     *
     * @param from - the message carrying the current state
     * @param to - the message restored from the stored object
     */
    static void carryFallbackState(Message from, Message to) {
        for (String partnershipType : new String[]{Partnership.PTYPE_SENDER, Partnership.PTYPE_RECEIVER}) {
            String attribute = fallbackStateAttributeFor(partnershipType);
            String state = from.getAttribute(attribute);
            if (state != null) {
                to.setAttribute(attribute, state);
            }
        }
    }

    /**
     * Resolves the certificate alias to use for one side of an outbound message, taking the fallback
     * when an earlier rejection by the partner has flagged this message for it.
     * <p>
     * Reported at info because it is not yet known to be the right certificate: that is only confirmed
     * when the partner accepts the message, which is reported separately.
     *
     * @param msg - the message being sent
     * @param partnershipType - PTYPE_SENDER for our own signing certificate, PTYPE_RECEIVER for the
     *                          partner's encryption certificate
     * @return the alias to use
     * @throws OpenAS2Exception if no alias is configured for that side
     */
    public static String resolveOutboundAlias(Message msg, String partnershipType) throws OpenAS2Exception {
        boolean useFallback = Message.FALLBACK_STATE_IN_USE.equals(msg.getAttribute(fallbackStateAttributeFor(partnershipType)));
        String alias = msg.getPartnership().getAliasOrFallback(partnershipType, useFallback);
        if (useFallback) {
            LoggerFactory.getLogger(AS2Util.class).info("Retrying with the fallback " + partnershipType
                    + " certificate alias \"" + alias + "\" after the partner rejected the primary" + msg.getLogMsgID());
        }
        return alias;
    }

    /**
     * Builds the advisory to log when a message only succeeded because a fallback certificate was used,
     * which is the signal that a certificate overlap is still in progress and can now be completed.
     * <p>
     * Sending cannot tell at the time it signs or encrypts whether the certificate is the right one, so
     * unlike the inbound side this cannot be reported until the partner has accepted the message. It is
     * reported with the same wording the inbound paths use so one log search finds every overlap.
     *
     * @param msg - the message that has just been accepted by the partner
     * @param partnerName - the partner to coordinate the switchover with
     * @return the advisory to log, or null if the message did not need a fallback certificate
     */
    static String fallbackCertificateInUseMessage(Message msg, String partnerName) {
        if (Message.FALLBACK_STATE_IN_USE.equals(msg.getAttribute(fallbackStateAttributeFor(Partnership.PTYPE_RECEIVER)))) {
            return LOG_MSG_PARTNER_CERT_SWITCHED + partnerName;
        }
        if (Message.FALLBACK_STATE_IN_USE.equals(msg.getAttribute(fallbackStateAttributeFor(Partnership.PTYPE_SENDER)))) {
            return LOG_MSG_OUR_CERT_SWITCHED + partnerName;
        }
        return null;
    }

    // Package private so the reason-to-side mapping can be tested directly
    static boolean switchToFallbackCertificate(Message msg, DispositionException de) {
        Logger logger = LoggerFactory.getLogger(AS2Util.class);
        if (de.getDisposition() == null || de.getDisposition().getStatusDescription() == null) {
            return false;
        }
        String reason = de.getDisposition().getStatusDescription().toLowerCase(Locale.ROOT);
        String partnershipType;
        String describeCert;
        if (reason.contains("decryption-failed")) {
            partnershipType = Partnership.PTYPE_RECEIVER;
            describeCert = "the partner's encryption certificate";
        } else if (reason.contains("authentication-failed") || reason.contains("integrity-check-failed")) {
            partnershipType = Partnership.PTYPE_SENDER;
            describeCert = "our signing certificate";
        } else {
            return false;
        }
        String attribute = fallbackStateAttributeFor(partnershipType);
        String state = msg.getAttribute(attribute);
        if (Message.FALLBACK_STATE_EXHAUSTED.equals(state)) {
            // Already tried and rejected, so leave the remaining retries on the primary certificate
            return false;
        }
        if (Message.FALLBACK_STATE_IN_USE.equals(state)) {
            /*
             * The fallback was rejected as well, so the certificate was not the problem. Go back to
             * the primary for whatever retries remain rather than spending them all on a certificate
             * that has now also been refused.
             */
            msg.setAttribute(attribute, Message.FALLBACK_STATE_EXHAUSTED);
            msg.setLogMsg("The fallback certificate for " + partnershipType + " was rejected as well, so the certificate"
                    + " is not the problem. Reverting to the primary alias for any remaining retries.");
            logger.warn(msg.getLogMsg() + msg.getLogMsgID());
            return false;
        }
        String fallbackAlias;
        try {
            fallbackAlias = msg.getPartnership().getAliasFallback(partnershipType);
        } catch (OpenAS2Exception e) {
            return false;
        }
        if (fallbackAlias == null) {
            return false;
        }
        msg.setAttribute(attribute, Message.FALLBACK_STATE_IN_USE);
        msg.setLogMsg("Partner rejected the message citing " + reason.trim() + " so the resend will use the fallback for "
                + describeCert + " (alias " + fallbackAlias + "). If this succeeds, promote the fallback to the primary alias"
                + " and remove the fallback once the partner has completed their switchover.");
        logger.warn(msg.getLogMsg() + msg.getLogMsgID());
        return true;
    }


    /**
     * @description Attempts to check if a resend should go ahead and if so
     * decrements the resend count and stores the decremented retry count in the
     * options map. If the passed in retry count is null or invalid it will fall
     * back to a system default
     * @param session
     * @param sourceClass
     * @param how
     * @param msg
     * @param cause
     * @param useOriginalMsgObject - some systems require the identifier to stay the same so do not repackage the message
     * @param keepOriginalData
     * @return
     * @throws OpenAS2Exception
     */
    public static boolean resend(Session session, Class<?> sourceClass, String how, Message msg, OpenAS2Exception cause, boolean useOriginalMsgObject, boolean keepOriginalData) throws OpenAS2Exception {
        Logger logger = LoggerFactory.getLogger(AS2Util.class);
        int retries = Integer.parseInt((String)msg.getOption(ResenderModule.OPTION_RETRIES));
        int maxRetryCount = getMaxResendCount(session, msg);
        if (logger.isDebugEnabled()) {
            logger.debug("RESEND requested. Retries: " + retries + " Max retries: " + maxRetryCount + "\n        Message file from passed in object: " + msg.getAttribute(FileAttribute.MA_PENDINGFILE) + msg.getLogMsgID());
        }
        if (maxRetryCount > -1) {
            // Have to resend some fixed number of times so check if we are done
            if (retries >= maxRetryCount) {
                // Retry limit reached so cleanup the message files
                msg.setLogMsg("Message abandoned after retry limit reached." + msg.getLogMsgID());
                logger.error(msg.getLogMsg());
                // Logger significant msg state
                msg.setOption("STATE", Message.MSG_STATE_SEND_FAIL);
                msg.trackMsgState(session);
                // Cleanup the files associated with this failed message
                if (logger.isDebugEnabled()) {
                    logger.debug("Calling AS2Util.cleanupFiles from resend abort on max retries.");
                }
                AS2Util.cleanupFiles(msg, true);
                // Signal sending retry has been abandoned
                return false;
            }
        }
        // Keep a popinter to the passed in msg object in case it is overwritten in this method so that setting
        // the resend flag to avoid file cleanup is not lost when this method exits and the original initiating
        // method of this cycle queries the msg object to check if it is ok to call file cleanup
        Message passed_in_msg = msg;
        // The current "msg" object is the same is the persisted one if it is the first entry to the resend process
        // so no need to retrieve from file system in that case
        if (useOriginalMsgObject && retries > 0) {
            String pendingMsgObjFileName = msg.getAttribute(FileAttribute.MA_PENDINGFILE) + ".object";

            if (logger.isDebugEnabled()) {
                logger.debug("Pending msg object file to retrieve data from in MDN receiver: " + pendingMsgObjFileName);
            }
            ObjectInputStream pifois = null;
            Message originalMsg;
            try {
                try {
                    pifois = new ObjectInputStream(new FileInputStream(new File(pendingMsgObjFileName)));
                } catch (FileNotFoundException e) {
                    throw new OpenAS2Exception("Could not retrieve pending info file: " + org.openas2.util.Logging.getExceptionMsg(e), e);
                } catch (IOException e) {
                    throw new OpenAS2Exception("Could not open pending info file: " + org.openas2.util.Logging.getExceptionMsg(e), e);
                }
                try {
                    originalMsg = (Message) pifois.readObject();
                } catch (Exception e) {
                    throw new OpenAS2Exception("Cannot retrieve original message object for resend: " + org.openas2.util.Logging.getExceptionMsg(e));
                }
            } finally {
                try {
                    if (pifois != null) {
                        pifois.close();
                    }
                } catch (IOException e) {
                }
            }
            // Update original with latest message-id and pendinginfo file so it
            // is kept up to date
            originalMsg.setAttribute(FileAttribute.MA_PENDINGINFO, msg.getAttribute(FileAttribute.MA_PENDINGINFO));
            // The stored object predates any certificate fallback decision, so bring that state forward
            carryFallbackState(msg, originalMsg);
            if (!keepOriginalData) {
                originalMsg.setMessageID(msg.getMessageID());
                originalMsg.setOption(ResenderModule.OPTION_RETRIES, "" + retries);
            }
            if (logger.isTraceEnabled()) {
                logger.trace("Message file extracted from passed in object: "
                             + msg.getAttribute(FileAttribute.MA_PENDINGFILE)
                             + "\n        Message file extracted from original object: "
                             + originalMsg.getAttribute(FileAttribute.MA_PENDINGFILE) + msg.getLogMsgID());
            }
            msg = originalMsg;
        }

        // Update the message state for the failed message as it will no longer be using
        // the same message ID
        msg.setOption("STATE", Message.MSG_STATE_SEND_FAIL_RESEND_QUEUED);
        msg.trackMsgState(session);
        boolean requiresNewMessageId = "true".equalsIgnoreCase(msg.getPartnership().getAttributeOrProperty(Partnership.PA_RESEND_REQUIRES_NEW_MESSAGE_ID, "true"));

        if (requiresNewMessageId) {
            /**
             * TODO: CHANGE THE DEFAULT TO FALSE
             * Per https://tools.ietf.org/html/rfc4130#section-9.3 resend should have same
             * Message-Id ... BUT Because it was implemented in the beginning to create a
             * new one for each resend, for backwards compatibility the default is the
             * reverse.
             * Systems like Mendelson require a new Message-Id
             */
            // Resend requires a new Message-Id and we need to update the pendinginfo file
            // name to match....
            // The actual file that is pending can remain the same name since it is pointed
            // to by line in pendinginfo file
            String oldMsgId = msg.getMessageID();
            msg.setAttribute(BaseMsgTrackingModule.FIELDS.PRIOR_MSG_ID, oldMsgId);
            String oldPendingInfoFileName = msg.getAttribute(FileAttribute.MA_PENDINGINFO);
            String newMsgId = msg.generateMessageID();
            // Set new Id in Message object so we can generate new file name
            msg.setMessageID(newMsgId);
            // msg.setHeader("Original-Message-Id", oldMsgId); // Not sure about this so leave out for now
            String newPendingInfoFileName = buildPendingFileName(msg, session.getProcessor(), Processor.PENDING_MDN_INFO_DIRECTORY_IDENTIFIER);
            if (logger.isDebugEnabled()) {
                logger.debug("" + "\n        Old Msg Id: " + oldMsgId + "\n        Old Info File: " + oldPendingInfoFileName + "\n        New Info File: " + newPendingInfoFileName + msg.getLogMsgID());
            }
            // Update the pending file to new name
            File oldPendInfFile = new File(oldPendingInfoFileName);
            File newPendInfFile = new File(newPendingInfoFileName);
            if (logger.isTraceEnabled()) {
                logger.trace("Attempting to rename pending info file : " + oldPendInfFile.getName() + " :::: New name: " + newPendInfFile.getName() + msg.getLogMsgID());
            }
            try {
                newPendInfFile = IOUtil.moveFile(oldPendInfFile, newPendInfFile, false);
                // Update the name of the file in the message object
                msg.setAttribute(FileAttribute.MA_PENDINGINFO, newPendingInfoFileName);
                if (logger.isInfoEnabled()) {
                    logger.info("Renamed pending info file : " + oldPendInfFile.getName() + " :::: New name: " + newPendInfFile.getName() + msg.getLogMsgID());
                }

            } catch (IOException iose) {
                msg.setLogMsg("Error renaming file: " + org.openas2.util.Logging.getExceptionMsg(iose));
                logger.error(msg.getLogMsg(), iose);
            }
        }
        Map<String, Object> options = new HashMap<String, Object>();
        options.put(ResenderModule.OPTION_CAUSE, cause);
        options.put(ResenderModule.OPTION_INITIAL_SENDER, sourceClass);
        options.put(ResenderModule.OPTION_RESEND_METHOD, how);
        options.put(ResenderModule.OPTION_RETRIES, "" + retries);
        session.getProcessor().handle(ResenderModule.DO_RESEND, msg, options);
        // Make sure the flag is set in the passed in object if it was swapped out
        if (passed_in_msg != msg) {
            passed_in_msg.setIsResend(msg.isResend());
        }
        return true;
    }

    /**
     * Processing MDN sent from receiver. Unless the MDN cannot be extracted from the received HTTP packet, the
     * method will ensure all appropriate action is taken to handle any errors cleanly  and return a boolean
     * indicating whether the MDN was successfully extracted and processed.
     *
     * @param msg         The context object
     * @param data        Received data
     * @param out         HTTP output stream
     * @param isAsyncMDN  boolean indicating if this is an ASYNC MDN
     * @param session     - Session object
     * @param sourceClass - who invoked this method

     * @return mdnResponseIssue - boolean indicating that the MDN processing identified an issue.

     * @throws OpenAS2Exception - an internally handled error has occurred
     * @throws IOException      - the IO system has a problem
     * 
     */
    public static boolean processMDN(AS2Message msg, byte[] data, OutputStream out, boolean isAsyncMDN, Session session, Class<?> sourceClass) throws OpenAS2Exception, IOException {
        Logger logger = LoggerFactory.getLogger(AS2Util.class);

        // Create a MessageMDN and copy HTTP headers
        MessageMDN mdn = msg.getMDN();
        if (logger.isTraceEnabled()) {
            logger.trace("HTTP headers in received MDN: " + AS2Util.printHeaders(mdn.getHeaders().getAllHeaders()));
        }
        // get the MDN partnership info
        mdn.getPartnership().setSenderID(Partnership.PID_AS2, StringUtil.removeDoubleQuotes(mdn.getHeader("AS2-From")));
        mdn.getPartnership().setReceiverID(Partnership.PID_AS2, StringUtil.removeDoubleQuotes(mdn.getHeader("AS2-To")));
        try {
            session.getPartnershipFactory().updatePartnership(mdn, false);
        } catch (OpenAS2Exception e) {
            // Partnership not found
           try {
                HTTPUtil.sendHTTPResponse(out, HttpURLConnection.HTTP_BAD_REQUEST, null);
            } catch (IOException e1) {
            }
            if (logger.isInfoEnabled()) {
                logger.info("Partnership lookup failed for MDN received from: " + msg.getHeader("AS2-To")
                    + "  MDN is targeting partner: " + msg.getHeader("AS2-From"));
            }
            cleanupFiles(msg, true);
            // since we cannot identify the partnership there is nothing to do here except tell the caller we did not process it
            return false;
        }

        MimeBodyPart part;
        try {
            part = new MimeBodyPart(mdn.getHeaders(), data);
            msg.getMDN().setData(part);
        } catch (MessagingException e1) {
            msg.setLogMsg("Failed to create mimebodypart from received MDN data for partnership " + mdn.getPartnership().getName() + ": " + org.openas2.util.Logging.getExceptionMsg(e1));
            logger.error(msg.getLogMsg(), e1);
            AS2Util.resend(session, sourceClass, SenderModule.DO_SEND, msg, new OpenAS2Exception(e1), true, false);
            return true;
        }
        CertificateFactory cFx = session.getCertificateFactory(CertificateFactory.COMPID_AS2_CERTIFICATE_FACTORY);
        String x509_alias = mdn.getPartnership().getAlias(Partnership.PTYPE_RECEIVER);
        X509Certificate senderCert = cFx.getCertificate(x509_alias);

        msg.setStatus(Message.MSG_STATUS_MDN_PARSE);
        if (logger.isTraceEnabled()) {
            logger.trace("Parsing MDN: " + mdn.toString() + msg.getLogMsgID());
        }
        boolean mdnParsed;
        try {
            mdnParsed = AS2Util.parseMDN(msg, senderCert);
        } catch (OpenAS2Exception e) {
            /*
             * Verifying the MDN signature fails locally when the partner has rotated the certificate
             * it signs with, so retry with the fallback before treating this as an error. Parsing
             * throws before it reads anything out of the report, so retrying is safe.
             */
            String fallbackAlias = mdn.getPartnership().getAliasFallback(Partnership.PTYPE_RECEIVER);
            if (fallbackAlias == null) {
                throw e;
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Verifying the MDN with the primary certificate failed so trying the fallback alias: " + fallbackAlias + msg.getLogMsgID());
            }
            try {
                mdnParsed = AS2Util.parseMDN(msg, cFx.getCertificate(fallbackAlias));
            } catch (Exception fallbackFailure) {
                /*
                 * The fallback did not work either, or is not in the keystore at all. Report the
                 * original failure rather than this one: the primary is the configured certificate and
                 * its error is the meaningful diagnostic, while a fallback that cannot be used is a
                 * configuration problem worth logging separately.
                 */
                logger.error("The MDN was not verified by the fallback alias \"" + fallbackAlias + "\" either: "
                        + org.openas2.util.Logging.getExceptionMsg(fallbackFailure) + msg.getLogMsgID());
                throw e;
            }
            // Succeeded on the fallback, so the partner has switched to their new certificate
            logger.warn(LOG_MSG_PARTNER_CERT_SWITCHED + mdn.getPartnership().getReceiverID(Partnership.PID_NAME) + msg.getLogMsgID());
        }
        if (!mdnParsed) {
            msg.setStatus(Message.MSG_STATUS_MSG_TERMINATED_IN_ERROR);
            msg.setOption("STATE", Message.MSG_STATE_MSG_SENT_MDN_RECEIVED_ERROR);
            msg.trackMsgState(session);
            return false;
        }

        if (isAsyncMDN) {
            getMetaData(msg, session);
        }

        msg.setStatus(Message.MSG_STATUS_MDN_VERIFY);
        if (logger.isTraceEnabled()) {
            logger.trace("MDN parsed.    Payload file name: " + msg.getPayloadFilename() + "\n    Checking MDN report..." + msg.getLogMsgID());
        }
        try {
            AS2Util.checkMDN(msg);
            /*
             * If the MDN was successfully received, send correct HTTP response irrespective
             * of possible error conditions due to disposition errors or MIC mismatch
             */
            if (isAsyncMDN) {
                HTTPUtil.sendHTTPResponse(out, HttpURLConnection.HTTP_OK, null);
            }

        } catch (DispositionException de) {
            /*
             * Issue with disposition but still send OK at HTTP level to indicate message
             * received
             */
            if (isAsyncMDN) {
                HTTPUtil.sendHTTPResponse(out, HttpURLConnection.HTTP_OK, null);
            }
            // If a disposition exception occurs then there must have been an
            // error response in the disposition
            if (logger.isErrorEnabled()) {
                logger.error("Disposition exception processing MDN: " + de.getText() + msg.getLogMsgID());
            }
            // A rejection naming a certificate problem may just mean a rotation is in progress, so
            // flag the fallback certificate for the resend before queueing it
            switchToFallbackCertificate(msg, de);
            // Hmmmm... Error may require manual intervention but keep
            // trying.... possibly change retry count to 1 or just fail????
            AS2Util.resend(session, sourceClass, SenderModule.DO_SEND, msg, de, true, false);
            msg.setOption("STATE", Message.MSG_STATE_MSG_SENT_MDN_RECEIVED_ERROR);
            msg.trackMsgState(session);
            return true;
        } catch (OpenAS2Exception oae) {
            // Possibly MIC mismatch so resend
            if (isAsyncMDN) {
                HTTPUtil.sendHTTPResponse(out, HttpURLConnection.HTTP_OK, null);
            }
            OpenAS2Exception oae2 = new OpenAS2Exception("Message was sent but an error occured while receiving the MDN: " + org.openas2.util.Logging.getExceptionMsg(oae), oae);
            oae2.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
            oae2.log();
            AS2Util.resend(session, sourceClass, SenderModule.DO_SEND, msg, oae2, true, false);
            msg.setOption("STATE", Message.MSG_STATE_MSG_SENT_MDN_RECEIVED_ERROR);
            msg.trackMsgState(session);
            return true;
        }

        msg.setOption("STATE", Message.MSG_STATE_MSG_SENT_MDN_RECEIVED_OK);
        if (logger.isTraceEnabled()) {
            logger.trace("MDN processed. \n    Payload file name: " + msg.getPayloadFilename() + "\n    Persisting MDN report..." + msg.getLogMsgID());
        }

        // Store the MDN before tracking the state so the stored MDN file path is captured in the
        // tracking record.
        session.getProcessor().handle(StorageModule.DO_STOREMDN, msg, null);
        msg.trackMsgState(session);
        msg.setStatus(Message.MSG_STATUS_MSG_CLEANUP);
        // To support extended reporting via logging log info passing Message object
        msg.setLogMsg("Message sent and MDN received successfully.");
        logger.info(msg.getLogMsg());
        /*
         * The partner accepted the message, so if a fallback certificate was what made that work the
         * overlap can now be completed. Reported here rather than when signing or encrypting because
         * that is the first point at which it is known to have been the right certificate.
         */
        String overlapAdvisory = fallbackCertificateInUseMessage(msg, msg.getPartnership().getReceiverID(Partnership.PID_NAME));
        if (overlapAdvisory != null) {
            logger.warn(overlapAdvisory + msg.getLogMsgID());
        }

        cleanupFiles(msg, false);
        return false;

    }

    /*
     * @description This method builds the name of the pending info file
     *
     * @param msg - the Message object containing enough information to build the
     * pending info file name
     */
    public static String buildPendingFileName(Message msg, Processor processor, String directoryIdentifier) throws OpenAS2Exception {
        String msgId = msg.getMessageID(); // this includes enclosing angled brackets <>
        String dir = processor.getParameters().get(directoryIdentifier);
        if (msgId == null || msgId.length() < 1) {
            // No ID set yet so generate a random string for uniqueness
            msgId = AS2Util.generateMessageID(msg, false);
            msg.setMessageID(msgId);
        }
        return (dir + "/" + IOUtil.cleanFilename(msgId));
    }
    /*
     * @description This method retrieves the information from the pending
     * information file written by the sender module
     *
     * @param msg - the Message object containing enough information to build the
     * pending info file name
     */

    public static void getMetaData(AS2Message msg, Session session) throws OpenAS2Exception {
        Logger logger = LoggerFactory.getLogger(AS2Util.class);
        // use original message ID to open the pending information file from pendinginfo
        // folder.
        String originalMsgId = msg.getMDN().getAttribute(AS2MessageMDN.MDNA_ORIG_MESSAGEID);

        msg.setMessageID(originalMsgId);
        String pendinginfofile = buildPendingFileName(msg, session.getProcessor(), Processor.PENDING_MDN_INFO_DIRECTORY_IDENTIFIER);

        if (logger.isDebugEnabled()) {
            logger.debug("Pending info file to retrieve data from in MDN receiver: " + pendinginfofile);
        }
        // Get the pending information file based on original message ID
        File iFile = new File(pendinginfofile);
        if (!iFile.exists()) {
            // try without the angle brackets in case they were added
            String oMsgIdStripped = removeAngleBrackets(originalMsgId);
            if (oMsgIdStripped == null || originalMsgId.equals(oMsgIdStripped)) {
                // No difference so...
                throw new OpenAS2Exception("Pending info file missing: " + pendinginfofile);
            }
            msg.setMessageID(oMsgIdStripped);
            pendinginfofile = buildPendingFileName(msg, session.getProcessor(), Processor.PENDING_MDN_INFO_DIRECTORY_IDENTIFIER);
            iFile = new File(pendinginfofile);
            if (!iFile.exists()) {
                throw new OpenAS2Exception("Pending info file missing: " + pendinginfofile);
            }
        }
        msg.setAttribute(FileAttribute.MA_PENDINGINFO, pendinginfofile);
        getMetaData(msg, iFile);
    }

    @SuppressWarnings("unchecked")
    public static void getMetaData(AS2Message msg, File inFile) throws OpenAS2Exception {
        Logger logger = LoggerFactory.getLogger(AS2Util.class);
        ObjectInputStream pifois;
        try {
            pifois = new ObjectInputStream(new FileInputStream(inFile));
        } catch (IOException e) {
            throw new OpenAS2Exception("Could not open pending info file: " + org.openas2.util.Logging.getExceptionMsg(e), e);
        }

        try {
            // Get the original MIC from the first line of pending information file
            msg.setCalculatedMIC((String) pifois.readObject());
            // Get the retry count for number of resends to go from the second line of
            // pending information file
            int retries = Integer.parseInt((String) pifois.readObject());
            if (logger.isTraceEnabled()) {
                logger.trace("RETRY COUNT from pending info file: " + retries);
            }
            msg.setOption(ResenderModule.OPTION_RETRIES, "" + retries);
            // Get the original source file name from the 3rd line of pending information
            // file
            msg.setPayloadFilename((String) pifois.readObject());
            msg.setAttribute(FileAttribute.MA_FILENAME, (String) pifois.readObject());
            // Get the original pending file from the 4th line of pending information file
            msg.setAttribute(FileAttribute.MA_PENDINGFILE, (String) pifois.readObject());
            msg.setAttribute(FileAttribute.MA_ERROR_DIR, (String) pifois.readObject());
            msg.setAttribute(FileAttribute.MA_SENT_DIR, (String) pifois.readObject());
            msg.getAttributes().putAll((Map<String, String>) pifois.readObject());

            if (logger.isTraceEnabled()) {
                logger.trace("Data retrieved from Pending info file:" + "\n        Original MIC: " + msg.getCalculatedMIC() + "\n        Retry Count: " + retries + "\n        Original file name : " + msg.getPayloadFilename() + "\n        Sent file name : " + msg.getAttribute(FileAttribute.MA_FILENAME) + "\n        Pending message file : " + msg.getAttribute(FileAttribute.MA_PENDINGFILE) + "\n        Error directory: " + msg.getAttribute(FileAttribute.MA_ERROR_DIR) + "\n        Sent directory: " + msg.getAttribute(FileAttribute.MA_SENT_DIR) + "\n        Attributes: " + msg.getAttributes() + msg.getLogMsgID());
            }
        } catch (IOException e) {
            throw new OpenAS2Exception("Processing file failed: " + inFile.getAbsolutePath() + "Exception retrieving the pending MDN information: " + org.openas2.util.Logging.getExceptionMsg(e), e);
        } catch (ClassNotFoundException e) {
            throw new OpenAS2Exception("Processing file failed: " + inFile.getAbsolutePath() + "Failed to rebuild an object from the pending MDN information: " + org.openas2.util.Logging.getExceptionMsg(e), e);
        } finally {
            if (pifois != null) {
                try {
                    pifois.close();
                } catch (IOException e) {
                }
            }
        }

    }

    public synchronized static void cleanupFiles(Message msg, boolean isError) {
        Logger logger = LoggerFactory.getLogger(AS2Util.class);
        if (msg.isFileCleanupCompleted()) {
            if (logger.isTraceEnabled()) {
                logger.trace("File cleanup already called for " + msg.getMessageID());
            }
            return;
        }
        String pendingMessageMetadata = msg.getAttribute(FileAttribute.MA_PENDINGINFO);
        if (pendingMessageMetadata != null) {
            File fPendingInfoFile = new File(pendingMessageMetadata);
            if (fPendingInfoFile.exists()) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Deleting pendinginfo file : " + fPendingInfoFile.getAbsolutePath() + msg.getLogMsgID());
                }

                try {
                    IOUtil.deleteFile(fPendingInfoFile);
                    if (logger.isTraceEnabled()) {
                        logger.trace("Pending MDN INFO file deleted: " + pendingMessageMetadata + msg.getLogMsgID());
                    }
                } catch (Exception e) {
                    msg.setLogMsg("File was successfully sent but info file not deleted: " + pendingMessageMetadata);
                    logger.warn(msg.getLogMsg(), e);
                }
            } else {
                msg.setLogMsg("Cleanup could not find pendinginfo file: " + pendingMessageMetadata);
                logger.warn(msg.getLogMsg());
            }
        }

        String pendingFileName = msg.getAttribute(FileAttribute.MA_PENDINGFILE);
        if (pendingFileName != null) {
            File fPendingFile = new File(pendingFileName);
            File msgObjFile = new File(pendingFileName + ".object");
            if (msgObjFile.exists()) {
                try {
                    IOUtil.deleteFile(msgObjFile);
                    if (logger.isTraceEnabled()) {
                        logger.trace("The RETRY message object file deleted: " + pendingFileName + ".object" + msg.getLogMsgID());
                    }
                } catch (Exception e) {
                    msg.setLogMsg("The RETRY message object file NOT deleted: " + org.openas2.util.Logging.getExceptionMsg(e));
                    logger.warn(msg.getLogMsg(), e);
                }
            }
            if (logger.isTraceEnabled()) {
                logger.trace("Cleaning up pending file : " + fPendingFile.getName() + " ::: From pending folder : " + fPendingFile.getParent() + msg.getLogMsgID());
            }
            try {
                // Move file to error or sent directory if the error or sent saving functionality is enabled
                boolean isMoved = false;
                if (fPendingFile.exists()) {
                    String tgtDir = null;
                    String targetFilenameUnparsed = "";
                    if (isError) {
                        tgtDir = msg.getAttribute(FileAttribute.MA_ERROR_DIR);
                        targetFilenameUnparsed = msg.getAttribute(FileAttribute.MA_ERROR_FILENAME);
                    } else {
                        // If the Sent Directory option is set, move the transmitted file to the sent
                        // directory
                        tgtDir = msg.getAttribute(FileAttribute.MA_SENT_DIR);
                        targetFilenameUnparsed = msg.getAttribute(FileAttribute.MA_SENT_FILENAME);
                    }
                    if (tgtDir != null && tgtDir.length() > 0) {
                        File tgtFile = null;
                        try {
                            String tgtFileName = fPendingFile.getName();
                            if (targetFilenameUnparsed != null && targetFilenameUnparsed.length() > 0) {
                                CompositeParameters parser = new CompositeParameters(false).add("date", new DateParameters()).add("msg", new MessageParameters(msg)).add("rand", new RandomParameters());
                                tgtFileName = ParameterParser.parse(targetFilenameUnparsed, parser);
                            }
                            tgtFileName = IOUtil.cleanFilename(tgtFileName);
                            tgtFile = new File(tgtDir + "/" + tgtFileName);
                            tgtFile = IOUtil.moveFile(fPendingFile, tgtFile, false);
                            logger.info("PENDING FILE " + pendingFileName + " AFTER MOVE STILL EXISTS? " + fPendingFile.exists());
                            isMoved = true;
    
                            if (logger.isDebugEnabled()) {
                                logger.debug("Pending MDN MSG FILE file " + fPendingFile.getAbsolutePath() + " moved to " + tgtFile.getAbsolutePath() + msg.getLogMsgID());
                            }
    
                        } catch (IOException iose) {
                            msg.setLogMsg("Error moving file to " + tgtDir + " : " + org.openas2.util.Logging.getExceptionMsg(iose));
                            logger.error(msg.getLogMsg(), iose);
                        }
                    }
                }
                if (!isMoved) {
                    // Could not find somewhere to move it to so delete it if it still exists
                    if (fPendingFile.exists()) {
                        IOUtil.deleteFile(fPendingFile);
                        if (logger.isInfoEnabled()) {
                            logger.info("Pending MDN MSG FILE deleted: " + fPendingFile.getAbsolutePath() + msg.getLogMsgID());
                        }
                    }
                }
            } catch (Exception e) {
                msg.setLogMsg("File cleanup unable to delete the locally stored version of the pending MSG file: " + fPendingFile.getAbsolutePath());
                logger.error(msg.getLogMsg(), e);
            }
        }
        msg.setFileCleanupCompleted(true);
    }

    private static String removeAngleBrackets(String srcString) {
        return (srcString == null ? null : srcString.replaceAll("^<([^>]+)>$", "$1"));
    }

    public static boolean attributeEnhancer(Map<String, String> attribs) throws OpenAS2Exception {
        Pattern PATTERN = Pattern.compile("\\$attribute\\.([^\\$]++)\\$|\\$properties\\.([^\\$]++)\\$");
        boolean valuesWereEnhanced = false;
        for (Map.Entry<String, String> entry : attribs.entrySet()) {
            String input = entry.getValue();
            StringBuffer strBuf = new StringBuffer();
            Matcher matcher = PATTERN.matcher(input);
            boolean hasChanged = false;
            while (matcher.find()) {
                String value = null;
                String key = matcher.group(1);
                if (key == null) {
                    key = matcher.group(2);
                    value = Properties.getProperty(key, null);
                } else {
                    value = attribs.get(key);
                }
                hasChanged = true;
                if (value == null) {
                    throw new OpenAS2Exception("Missing attribute value for replacement: " + matcher.group());
                } else {
                    matcher.appendReplacement(strBuf, Matcher.quoteReplacement(value));
                }
            }
            if (hasChanged) {
                matcher.appendTail(strBuf);
                attribs.put(entry.getKey(), strBuf.toString());
                valuesWereEnhanced = true;
            }
        }
        return valuesWereEnhanced;
    }

    public static String printHeaders(Enumeration<Header> hdrs) {
        return printHeaders(hdrs, " == ", "\n\t\t");
    }

    public static String printHeaders(Enumeration<Header> hdrs, String nameValueSeparator, String valuePairSeparator) {
        String headers = "";
        while (hdrs.hasMoreElements()) {
            Header h = hdrs.nextElement();
            headers = headers + valuePairSeparator + h.getName() + nameValueSeparator + h.getValue();
        }

        return (headers);

    }

    public static int getMaxResendCount(Session session, Message msg) throws ComponentNotFoundException {
        // Retry count - first try on partnership then on the processor
        String maxRetryCntStr = msg.getPartnership().getAttribute(Partnership.PA_RESEND_MAX_RETRIES);
        if (maxRetryCntStr == null || maxRetryCntStr.length() < 1) {
            maxRetryCntStr = session.getProcessor().getParameters().get(MessageBuilderModule.PARAM_RESEND_MAX_RETRIES);
        }
        int maxResendCount = (maxRetryCntStr != null && maxRetryCntStr.length() > 0)?Integer.parseInt(maxRetryCntStr):ResenderModule.DEFAULT_RETRIES;
        return maxResendCount;
    }
}
