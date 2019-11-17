package org.openas2.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.DispositionException;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.cert.CertificateFactory;
import org.openas2.lib.helper.BCCryptoHelper;
import org.openas2.lib.helper.ICryptoHelper;
import org.openas2.lib.message.AS2Standards;
import org.openas2.lib.util.MimeUtil;
import org.openas2.message.AS2Message;
import org.openas2.message.AS2MessageMDN;
import org.openas2.message.FileAttribute;
import org.openas2.message.Message;
import org.openas2.message.MessageMDN;
import org.openas2.params.CompositeParameters;
import org.openas2.params.DateParameters;
import org.openas2.params.InvalidParameterException;
import org.openas2.params.MessageMDNParameters;
import org.openas2.params.MessageParameters;
import org.openas2.params.ParameterParser;
import org.openas2.params.RandomParameters;
import org.openas2.partner.Partnership;
import org.openas2.processor.Processor;
import org.openas2.processor.msgtracking.BaseMsgTrackingModule;
import org.openas2.processor.resender.ResenderModule;
import org.openas2.processor.sender.SenderModule;
import org.openas2.processor.storage.StorageModule;

import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.internet.ContentType;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AS2Util {
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
        CompositeParameters params = new CompositeParameters(false).add("date", new DateParameters()).add("msg", new MessageParameters(msg)).add("rand", new RandomParameters());
        if (isMDN) {
            params.add("mdn", new MessageMDNParameters(msg.getMDN()));
            idFormat = msg.getPartnership().getAttributeOrProperty(Properties.AS2_MDN_MESSAGE_ID_FORMAT, null);
        }
        if (idFormat == null) {
            idFormat = msg.getPartnership().getAttributeOrProperty(Properties.AS2_MESSAGE_ID_FORMAT, "<OPENAS2-$date.ddMMyyyyHHmmssZ$-$rand.UUID$@$msg.sender.as2_id$_$msg.receiver.as2_id$>");
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

    public static void parseMDN(Message msg, X509Certificate receiver) throws OpenAS2Exception {
        Log logger = LogFactory.getLog(AS2Util.class.getSimpleName());
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
                mainPart = getCryptoHelper().verifySignature(mainPart, receiver);
            }
        } catch (Exception e1) {
            logger.error("Error parsing MDN: " + org.openas2.logging.Log.getExceptionMsg(e1), e1);
            throw new OpenAS2Exception("Failed to verify signature of received MDN.");

        }

        try {
            MimeMultipart reportParts = new MimeMultipart(mainPart.getDataHandler().getDataSource());

            if (reportParts != null) {
                ContentType reportType = new ContentType(reportParts.getContentType());

                if (reportType.getBaseType().equalsIgnoreCase("multipart/report")) {
                    int reportCount = reportParts.getCount();
                    MimeBodyPart reportPart;

                    for (int j = 0; j < reportCount; j++) {
                        reportPart = (MimeBodyPart) reportParts.getBodyPart(j);
                        if (logger.isTraceEnabled() && "true".equalsIgnoreCase(System.getProperty("logRxdMdnMimeBodyParts", "false"))) {
                            logger.trace("Report MimeBodyPart from Multipart for inbound MDN: " + msg.getLogMsgID() + "\n" + MimeUtil.toString(reportPart, true));
                        }

                        if (reportPart.isMimeType("text/plain")) {
                            mdn.setText(reportPart.getContent().toString());
                        } else if (reportPart.isMimeType(AS2Standards.DISPOSITION_TYPE)) {
                            InternetHeaders disposition = new InternetHeaders(reportPart.getInputStream());
                            mdn.setAttribute(AS2MessageMDN.MDNA_REPORTING_UA, disposition.getHeader("Reporting-UA", ", "));
                            mdn.setAttribute(AS2MessageMDN.MDNA_ORIG_RECIPIENT, disposition.getHeader("Original-Recipient", ", "));
                            mdn.setAttribute(AS2MessageMDN.MDNA_FINAL_RECIPIENT, disposition.getHeader("Final-Recipient", ", "));
                            mdn.setAttribute(AS2MessageMDN.MDNA_ORIG_MESSAGEID, disposition.getHeader("Original-Message-ID", ", "));
                            mdn.setAttribute(AS2MessageMDN.MDNA_DISPOSITION, disposition.getHeader("Disposition", ", "));
                            mdn.setAttribute(AS2MessageMDN.MDNA_MIC, disposition.getHeader("Received-Content-MIC", ", "));
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new OpenAS2Exception("Filed to parse MDN: " + org.openas2.logging.Log.getExceptionMsg(e), e);
        }
    }

    /**
     * Verify disposition status is "processed" then check MIC is matched
     *
     * @param msg - the original message sent to the partner that the MDN
     *            relates to
     * @return true if mdn processed
     * @throws DispositionException - something wrong t=with the Disposition
     *                              structure
     * @throws OpenAS2Exception     - an internally handled error has occurred
     */
    public static boolean checkMDN(AS2Message msg) throws DispositionException, OpenAS2Exception {
        Log logger = LogFactory.getLog(AS2Util.class.getSimpleName());
        /*
         * The sender may return an error in the disposition and not set the MIC so
         * check disposition first
         */
        String disposition = msg.getMDN().getAttribute(AS2MessageMDN.MDNA_DISPOSITION);
        if (disposition != null && logger.isInfoEnabled()) {
            logger.info("received MDN [" + disposition + "]" + msg.getLogMsgID());
        }
        boolean dispositionHasWarning = false;
        try {
            new DispositionType(disposition).validate();
        } catch (DispositionException de) {
            if (logger.isWarnEnabled()) {
                logger.warn("Disposition exception on MDN. Disposition: " + disposition + msg.getLogMsgID(), de);
            }
            // Something wrong detected so flag it for later use
            dispositionHasWarning = true;
            de.setText(msg.getMDN().getText());

            if ((de.getDisposition() != null) && de.getDisposition().isWarning()) {
                // Do not throw error in this case ... just log it
                de.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
                de.terminate();
            } else {
                throw de;
            }
        } catch (OpenAS2Exception e) {
            msg.setLogMsg("Processing error occurred: " + org.openas2.logging.Log.getExceptionMsg(e));
            logger.error(msg, e);
            throw new OpenAS2Exception(e);
        }

        if ("none".equalsIgnoreCase(msg.getPartnership().getAttribute(Partnership.PA_AS2_MDN_OPTIONS))) {
            // signed MDN not requested so...
            return true;
        }
        if (logger.isTraceEnabled()) {
            logger.trace("MIC processing start... ");
        }
        // get the returned mic from mdn object
        String returnMIC = msg.getMDN().getAttribute(AS2MessageMDN.MDNA_MIC);
        if (returnMIC == null || returnMIC.length() < 1) {
            if (dispositionHasWarning) {
                // TODO: Think this should pribably throw error if MIC should have been returned
                // but for now...
                msg.setLogMsg("Returned MIC not found but disposition has warning so might be normal.");
                logger.warn(msg);
            } else {
                msg.setLogMsg("Returned MIC not found so cannot validate returned message.");
                logger.error(msg);
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
            logger.error(msg);
            throw new OpenAS2Exception("Invalid MIC string received. Forcing Resend");
        }
        String rMic = m.group(1);
        String rMicAlg = m.group(2);
        m = p.matcher(calcMIC);
        if (!m.find()) {
            msg.setLogMsg("Invalid MIC format in calculated MIC: " + calcMIC);
            logger.error(msg);
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
                String errmsg = "MIC algorithm returned by partner is not the same as the algorithm requested but must be the same per RFC4130 section 7.4.3. Original MIC alg: " + cMicAlg + " ::: returned MIC alg: " + rMicAlg + "\n\t\tEnsure that Partner supports the requested algorithm and the \"" + Partnership.PA_AS2_MDN_OPTIONS + "\" attribute for the outbound partnership uses the same algorithm as the \" +" + Partnership.PA_SIGNATURE_ALGORITHM + "\" attribute.";
                throw new OpenAS2Exception(errmsg + " Forcing Resend");
            }
        }
        if (!cMic.equals(rMic)) {
            /*
             * RFC 6362 specifies that the sent attachments should be considered invalid and
             * retransmitted
             */
            msg.setLogMsg("MIC not matched, original MIC: " + calcMIC + " return MIC: " + returnMIC);
            logger.error(msg);
            throw new OpenAS2Exception("MIC not matched. Forcing Resend");
        }
        if (logger.isTraceEnabled()) {
            logger.trace("MIC is matched, received MIC: " + returnMIC + msg.getLogMsgID());
        }
        return true;
    }

    // How many times should this message be sent?
    public static String retries(Map<Object, Object> options, String fallbackRetries) {
        String left;
        if (options == null || (left = (String) options.get(SenderModule.SOPT_RETRIES)) == null) {
            left = fallbackRetries;
        }

        if (left == null) {
            left = SenderModule.DEFAULT_RETRIES;
        }
        // Verify it is a valid integer
        try {
            Integer.parseInt(left);
        } catch (Exception e) {
            return SenderModule.DEFAULT_RETRIES;
        }
        return left;
    }

    /*
     * @description Attempts to check if a resend should go ahead and if so
     * decrements the resend count and stores the decremented retry count in the
     * options map. If the passed in retry count is null or invalid it will fall
     * back to a system default
     */
    public static boolean resend(Session session, Object sourceClass, String how, Message msg, OpenAS2Exception cause, String tries, boolean useOriginalMsgObject, boolean keepOriginalData) throws OpenAS2Exception {
        Log logger = LogFactory.getLog(AS2Util.class.getSimpleName());
        if (logger.isDebugEnabled()) {
            logger.debug("RESEND requested.... retries to go: " + tries + "\n        Message file from passed in object: " + msg.getAttribute(FileAttribute.MA_PENDINGFILE) + msg.getLogMsgID());
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
        if (retries >= 0 && retries-- <= 0) {
            msg.setLogMsg("Message abandoned after retry limit reached.");
            logger.error(msg);
            // Log significant msg state
            msg.setOption("STATE", Message.MSG_STATE_SEND_FAIL);
            msg.trackMsgState(session);
            throw new OpenAS2Exception("Message abandoned after retry limit reached." + msg.getLogMsgID());
        }

        if (useOriginalMsgObject) {
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
                    throw new OpenAS2Exception("Could not retrieve pending info file: " + org.openas2.logging.Log.getExceptionMsg(e), e);
                } catch (IOException e) {
                    throw new OpenAS2Exception("Could not open pending info file: " + org.openas2.logging.Log.getExceptionMsg(e), e);
                }
                try {
                    originalMsg = (Message) pifois.readObject();
                } catch (Exception e) {
                    throw new OpenAS2Exception("Cannot retrieve original message object for resend: " + org.openas2.logging.Log.getExceptionMsg(e));
                }
            } finally {
                try {
                    if (pifois != null) {
                        pifois.close();
                    }
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            // Update original with latest message-id and pendinginfo file so it
            // is kept up to date
            originalMsg.setAttribute(FileAttribute.MA_PENDINGINFO, msg.getAttribute(FileAttribute.MA_PENDINGINFO));
            if (!keepOriginalData) {
                originalMsg.setMessageID(msg.getMessageID());
                originalMsg.setOption(ResenderModule.OPTION_RETRIES, tries);
            }
            if (logger.isTraceEnabled()) {
                logger.trace("Message file extracted from passed in object: " + msg.getAttribute(FileAttribute.MA_PENDINGFILE) + "\n        Message file extracted from original object: " + originalMsg.getAttribute(FileAttribute.MA_PENDINGFILE) + msg.getLogMsgID());
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
             * Per https://tools.ietf.org/html/rfc4130#section-9.3 resend should have same
             * Message-Id ... BUT Because it was implemented in the beginning to vreate a
             * new one for each resend, for backwards compatibility the default is the
             * reverse Systems like Mendelson require a new Message-Id
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
            // msg.setHeader("Original-Message-Id", oldMsgId); // Not sure about this so
            // lesve out for now
            String newPendingInfoFileName = buildPendingFileName(msg, session.getProcessor(), "pendingmdninfo");
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
                msg.setLogMsg("Error renaming file: " + org.openas2.logging.Log.getExceptionMsg(iose));
                logger.error(msg, iose);
            }
        }
        Map<Object, Object> options = new HashMap<Object, Object>();
        options.put(ResenderModule.OPTION_CAUSE, cause);
        options.put(ResenderModule.OPTION_INITIAL_SENDER, sourceClass);
        options.put(ResenderModule.OPTION_RESEND_METHOD, how);
        options.put(ResenderModule.OPTION_RETRIES, "" + retries);
        session.getProcessor().handle(ResenderModule.DO_RESEND, msg, options);
        return true;
    }

    /**
     * Processing MDN sent from receiver.
     *
     * @param msg         The context object
     * @param data        Received data
     * @param out         HTTP output stream
     * @param isAsyncMDN  boolean indicating if this is an ASYNC MDN
     * @param session     - Session object
     * @param sourceClass - who invoked this method
     * @throws OpenAS2Exception - an internally handled error has occurred
     * @throws IOException      - the IO system has a problem
     */
    public static void processMDN(AS2Message msg, byte[] data, OutputStream out, boolean isAsyncMDN, Session session, Object sourceClass) throws OpenAS2Exception, IOException {
        Log logger = LogFactory.getLog(AS2Util.class.getSimpleName());

        // Create a MessageMDN and copy HTTP headers
        MessageMDN mdn = msg.getMDN();
        if (logger.isTraceEnabled()) {
            logger.trace("HTTP headers in received MDN: " + AS2Util.printHeaders(mdn.getHeaders().getAllHeaders()));
        }
        // get the MDN partnership info
        mdn.getPartnership().setSenderID(Partnership.PID_AS2, mdn.getHeader("AS2-From"));
        mdn.getPartnership().setReceiverID(Partnership.PID_AS2, mdn.getHeader("AS2-To"));
        session.getPartnershipFactory().updatePartnership(mdn, false);

        MimeBodyPart part;
        try {
            part = new MimeBodyPart(mdn.getHeaders(), data);
            msg.getMDN().setData(part);
        } catch (MessagingException e1) {
            msg.setLogMsg("Failed to create mimebodypart from received MDN data: " + org.openas2.logging.Log.getExceptionMsg(e1));
            logger.error(msg, e1);
            if (isAsyncMDN) {
                HTTPUtil.sendHTTPResponse(out, HttpURLConnection.HTTP_BAD_REQUEST, null);
            }
            throw new OpenAS2Exception("Error receiving MDN. Processing stopped.");
        }

        CertificateFactory cFx = session.getCertificateFactory();
        X509Certificate senderCert = cFx.getCertificate(mdn, Partnership.PTYPE_RECEIVER);

        msg.setStatus(Message.MSG_STATUS_MDN_PARSE);
        if (logger.isTraceEnabled()) {
            logger.trace("Parsing MDN: " + mdn.toString() + msg.getLogMsgID());
        }
        AS2Util.parseMDN(msg, senderCert);

        if (isAsyncMDN) {
            getMetaData(msg, session);
        }

        String retries = (String) msg.getOption(ResenderModule.OPTION_RETRIES);

        msg.setStatus(Message.MSG_STATUS_MDN_VERIFY);
        if (logger.isTraceEnabled()) {
            logger.trace("MDN parsed. \n\tPayload file name: " + msg.getPayloadFilename() + "\n\tChecking MDN report..." + msg.getLogMsgID());
        }
        try {
            AS2Util.checkMDN(msg);
            /*
             * If the MDN was successfully received send correct HTTP response irrespective
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
                logger.error("Disposition exception processing MDN ..." + msg.getLogMsgID(), de);
            }
            // Hmmmm... Error may require manual intervention but keep
            // trying.... possibly change retry count to 1 or just fail????
            AS2Util.resend(session, sourceClass, SenderModule.DO_SEND, msg, de, retries, true, false);
            msg.setOption("STATE", Message.MSG_STATE_MSG_SENT_MDN_RECEIVED_ERROR);
            msg.trackMsgState(session);
            return;
        } catch (OpenAS2Exception oae) {
            // Possibly MIC mismatch so resend
            if (isAsyncMDN) {
                HTTPUtil.sendHTTPResponse(out, HttpURLConnection.HTTP_OK, null);
            }
            OpenAS2Exception oae2 = new OpenAS2Exception("Message was sent but an error occured while receiving the MDN: " + org.openas2.logging.Log.getExceptionMsg(oae), oae);
            oae2.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
            oae2.terminate();
            AS2Util.resend(session, sourceClass, SenderModule.DO_SEND, msg, oae2, retries, true, false);
            msg.setOption("STATE", Message.MSG_STATE_MSG_SENT_MDN_RECEIVED_ERROR);
            msg.trackMsgState(session);
            return;
        }

        msg.setOption("STATE", Message.MSG_STATE_MSG_SENT_MDN_RECEIVED_OK);
        msg.trackMsgState(session);
        if (logger.isTraceEnabled()) {
            logger.trace("MDN processed. \n\tPayload file name: " + msg.getPayloadFilename() + "\n\tPersisting MDN report..." + msg.getLogMsgID());
        }

        session.getProcessor().handle(StorageModule.DO_STOREMDN, msg, null);
        msg.setStatus(Message.MSG_STATUS_MSG_CLEANUP);
        // To support extended reporting via logging log info passing Message object
        msg.setLogMsg("Message sent and MDN received successfully.");
        logger.info(msg);

        cleanupFiles(msg, false);

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
        Log logger = LogFactory.getLog(AS2Util.class.getSimpleName());
        // use original message ID to open the pending information file from pendinginfo
        // folder.
        String originalMsgId = msg.getMDN().getAttribute(AS2MessageMDN.MDNA_ORIG_MESSAGEID);

        msg.setMessageID(originalMsgId);
        String pendinginfofile = buildPendingFileName(msg, session.getProcessor(), "pendingmdninfo");

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
            pendinginfofile = buildPendingFileName(msg, session.getProcessor(), "pendingmdninfo");
            iFile = new File(pendinginfofile);
            if (!iFile.exists()) {
                throw new OpenAS2Exception("Pending info file missing: " + pendinginfofile);
            }
        }
        msg.setAttribute(FileAttribute.MA_PENDINGINFO, pendinginfofile);
        getMetaData(msg, iFile);
    }

    public static void getMetaData(AS2Message msg, File inFile) throws OpenAS2Exception {
        Log logger = LogFactory.getLog(AS2Util.class.getSimpleName());
        ObjectInputStream pifois;
        try {
            pifois = new ObjectInputStream(new FileInputStream(inFile));
        } catch (IOException e) {
            throw new OpenAS2Exception("Could not open pending info file: " + org.openas2.logging.Log.getExceptionMsg(e), e);
        }

        try {
            // Get the original MIC from the first line of pending information file
            msg.setCalculatedMIC((String) pifois.readObject());
            // Get the retry count for number of resends to go from the second line of
            // pending information file
            String retries = (String) pifois.readObject();
            if (logger.isTraceEnabled()) {
                logger.trace("RETRY COUNT from pending info file: " + retries);
            }
            msg.setOption(ResenderModule.OPTION_RETRIES, retries);
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
            throw new OpenAS2Exception("Failed to retrieve the pending MDN information from file: " + org.openas2.logging.Log.getExceptionMsg(e), e);
        } catch (ClassNotFoundException e) {
            throw new OpenAS2Exception("Failed to rebuild an object from the pending MDN information from file: " + org.openas2.logging.Log.getExceptionMsg(e), e);
        } finally {
            if (pifois != null) {
                try {
                    pifois.close();
                } catch (IOException e) {
                }
            }
        }

    }

    public static void cleanupFiles(Message msg, boolean isError) {
        Log logger = LogFactory.getLog(AS2Util.class.getSimpleName());

        String pendingInfoFileName = msg.getAttribute(FileAttribute.MA_PENDINGINFO);
        if (pendingInfoFileName != null) {
            File fPendingInfoFile = new File(pendingInfoFileName);
            if (fPendingInfoFile.exists()) {
                if (logger.isTraceEnabled()) {
                    logger.trace("Deleting pendinginfo file : " + fPendingInfoFile.getAbsolutePath() + msg.getLogMsgID());
                }

                try {
                    IOUtil.deleteFile(fPendingInfoFile);
                    if (logger.isTraceEnabled()) {
                        logger.trace("deleted " + pendingInfoFileName + msg.getLogMsgID());
                    }
                } catch (Exception e) {
                    msg.setLogMsg("File was successfully sent but info file not deleted: " + pendingInfoFileName);
                    logger.warn(msg, e);
                }
            } else {
                msg.setLogMsg("Cleanup could not find pendinginfo file: " + pendingInfoFileName);
                logger.warn(msg);
            }
        }

        String pendingFileName = msg.getAttribute(FileAttribute.MA_PENDINGFILE);
        if (pendingFileName != null) {
            File fPendingFile = new File(pendingFileName);
            try {
                IOUtil.deleteFile(new File(pendingFileName + ".object"));
                if (logger.isTraceEnabled()) {
                    logger.trace("deleted " + pendingFileName + ".object" + msg.getLogMsgID());
                }
            } catch (Exception e) {
                msg.setLogMsg("File was successfully sent but message object file not deleted: " + org.openas2.logging.Log.getExceptionMsg(e));
                logger.warn(msg, e);
            }
            if (logger.isTraceEnabled()) {
                logger.trace("Cleaning up pending file : " + fPendingFile.getName() + " from pending folder : " + fPendingFile.getParent() + msg.getLogMsgID());
            }
            try {
                boolean isMoved = false;
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
                        isMoved = true;

                        if (logger.isInfoEnabled()) {
                            logger.info("moved " + fPendingFile.getAbsolutePath() + " to " + tgtFile.getAbsolutePath() + msg.getLogMsgID());
                        }

                    } catch (IOException iose) {
                        msg.setLogMsg("Error moving file to " + tgtDir + " : " + org.openas2.logging.Log.getExceptionMsg(iose));
                        logger.error(msg, iose);
                    }
                }

                if (!isMoved) {
                    IOUtil.deleteFile(fPendingFile);
                    if (logger.isInfoEnabled()) {
                        logger.info("deleted " + fPendingFile.getAbsolutePath() + msg.getLogMsgID());
                    }
                }
            } catch (Exception e) {
                msg.setLogMsg("File was successfully sent but not deleted: " + fPendingFile.getAbsolutePath());
                logger.error(msg, e);
            }
        }
    }

    private static String removeAngleBrackets(String srcString) {
        return (srcString == null ? null : srcString.replaceAll("^<([^>]+)>$", "$1"));
    }

    public static void attributeEnhancer(Map<String, String> attribs) throws OpenAS2Exception {
        Pattern PATTERN = Pattern.compile("\\$attribute\\.([^\\$]++)\\$|\\$properties\\.([^\\$]++)\\$");
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
            }
        }
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

}
