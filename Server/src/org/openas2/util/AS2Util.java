package org.openas2.util;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.mail.internet.ContentType;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.DispositionException;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.cert.CertificateFactory;
import org.openas2.cert.CertificateNotFoundException;
import org.openas2.cert.KeyNotFoundException;
import org.openas2.lib.helper.BCCryptoHelper;
import org.openas2.lib.helper.ICryptoHelper;
import org.openas2.message.AS2Message;
import org.openas2.message.AS2MessageMDN;
import org.openas2.message.Message;
import org.openas2.message.MessageMDN;
import org.openas2.message.NetAttribute;
import org.openas2.params.MessageParameters;
import org.openas2.params.ParameterParser;
import org.openas2.partner.AS2Partnership;
import org.openas2.partner.ASXPartnership;
import org.openas2.partner.Partnership;
import org.openas2.processor.Processor;
import org.openas2.processor.resender.ResenderModule;
import org.openas2.processor.sender.SenderModule;

public class AS2Util {
    private static ICryptoHelper ch;

    public static ICryptoHelper getCryptoHelper() throws Exception {
        if (ch == null) {
            ch = new BCCryptoHelper();
            ch.initialize();
        }

        return ch;
    }

    public static MessageMDN createMDN(Session session, AS2Message msg, String mic,
            DispositionType disposition, String text) throws Exception {
        AS2MessageMDN mdn = new AS2MessageMDN(msg);
        mdn.setHeader("AS2-Version", "1.1");
        // RFC2822 format: Wed, 04 Mar 2009 10:59:17 +0100
        mdn.setHeader("Date", DateUtil.formatDate("EEE, dd MMM yyyy HH:mm:ss Z"));
        mdn.setHeader("Server", Session.TITLE);
        mdn.setHeader("Mime-Version", "1.0");
        mdn.setHeader("AS2-To", msg.getPartnership().getSenderID(AS2Partnership.PID_AS2));
        mdn.setHeader("AS2-From", msg.getPartnership().getReceiverID(AS2Partnership.PID_AS2));

        // get the MDN partnership info
        mdn.getPartnership().setSenderID(AS2Partnership.PID_AS2, mdn.getHeader("AS2-From"));
        mdn.getPartnership().setReceiverID(AS2Partnership.PID_AS2, mdn.getHeader("AS2-To"));
        session.getPartnershipFactory().updatePartnership(mdn, true);

        mdn.setHeader("From", msg.getPartnership().getReceiverID(Partnership.PID_EMAIL));
        String subject = mdn.getPartnership().getAttribute(ASXPartnership.PA_MDN_SUBJECT);

        if (subject != null) {
            mdn.setHeader("Subject", ParameterParser.parse(subject, new MessageParameters(msg)));
        } else {
            mdn.setHeader("Subject", "Your Requested MDN Response");
        }
        mdn.setText(ParameterParser.parse(text, new MessageParameters(msg)));
        mdn.setAttribute(AS2MessageMDN.MDNA_REPORTING_UA, Session.TITLE + "@"
                + msg.getAttribute(NetAttribute.MA_DESTINATION_IP) + ":"
                + msg.getAttribute(NetAttribute.MA_DESTINATION_PORT));
        mdn.setAttribute(AS2MessageMDN.MDNA_ORIG_RECIPIENT, "rfc822; " + msg.getHeader("AS2-To"));
        mdn.setAttribute(AS2MessageMDN.MDNA_FINAL_RECIPIENT, "rfc822; "
                + msg.getPartnership().getReceiverID(AS2Partnership.PID_AS2));
        mdn.setAttribute(AS2MessageMDN.MDNA_ORIG_MESSAGEID, msg.getHeader("Message-ID"));
        mdn.setAttribute(AS2MessageMDN.MDNA_DISPOSITION, disposition.toString());

        DispositionOptions dispOptions = new DispositionOptions(msg
                .getHeader("Disposition-Notification-Options"));

        mdn.setAttribute(AS2MessageMDN.MDNA_MIC, mic);
        createMDNData(session, mdn, dispOptions.getMicalg(), dispOptions.getProtocol());

        mdn.updateMessageID();
        
        // store MDN into msg in case AsynchMDN is sent fails, needs to be resent by send module
        msg.setMDN(mdn);

        return mdn;
    }

    public static void createMDNData(Session session, MessageMDN mdn, String micAlg,
            String signatureProtocol) throws Exception {
        // Create the report and sub-body parts
        MimeMultipart reportParts = new MimeMultipart();

        // Create the text part
        MimeBodyPart textPart = new MimeBodyPart();
        String text = mdn.getText() + "\r\n";            
        textPart.setContent(text, "text/plain");
        textPart.setHeader("Content-Type", "text/plain");        
        reportParts.addBodyPart(textPart);

        // Create the report part
        MimeBodyPart reportPart = new MimeBodyPart();
        InternetHeaders reportValues = new InternetHeaders();
        reportValues.setHeader("Reporting-UA", mdn.getAttribute(AS2MessageMDN.MDNA_REPORTING_UA));
        reportValues.setHeader("Original-Recipient", mdn
                .getAttribute(AS2MessageMDN.MDNA_ORIG_RECIPIENT));
        reportValues.setHeader("Final-Recipient", mdn
                .getAttribute(AS2MessageMDN.MDNA_FINAL_RECIPIENT));
        reportValues.setHeader("Original-Message-ID", mdn
                .getAttribute(AS2MessageMDN.MDNA_ORIG_MESSAGEID));
        reportValues.setHeader("Disposition", mdn.getAttribute(AS2MessageMDN.MDNA_DISPOSITION));
        reportValues.setHeader("Received-Content-MIC", mdn.getAttribute(AS2MessageMDN.MDNA_MIC));

        Enumeration<String> reportEn = reportValues.getAllHeaderLines();
        StringBuffer reportData = new StringBuffer();

        while (reportEn.hasMoreElements()) {
            reportData.append((String) reportEn.nextElement()).append("\r\n");
        }

        reportData.append("\r\n");

        String reportText = reportData.toString();
        reportPart.setContent(reportText, "message/disposition-notification");
        reportPart.setHeader("Content-Type", "message/disposition-notification");        
        reportParts.addBodyPart(reportPart);

        // Convert report parts to MimeBodyPart
        MimeBodyPart report = new MimeBodyPart();
        reportParts.setSubType("report; report-type=disposition-notification");
        report.setContent(reportParts);
        report.setHeader("Content-Type", reportParts.getContentType());

        // Sign the data if needed
        if (signatureProtocol != null) {            
            CertificateFactory certFx = session.getCertificateFactory();

            try {
                X509Certificate senderCert = certFx.getCertificate(mdn,
                        Partnership.PTYPE_SENDER);                
                PrivateKey senderKey = certFx.getPrivateKey(mdn, senderCert);
                MimeBodyPart signedReport = getCryptoHelper().sign(report, senderCert,
                        senderKey, micAlg);
                mdn.setData(signedReport);
            } catch (CertificateNotFoundException cnfe) {
                cnfe.terminate();
                mdn.setData(report);
            } catch (KeyNotFoundException knfe) {
                knfe.terminate();
                mdn.setData(report);
            }
        } else {
            mdn.setData(report);
        }

        // Update the MDN headers with content information
        MimeBodyPart data = mdn.getData();
        mdn.setHeader("Content-Type", data.getContentType());

        //int size = getSize(data);
        //mdn.setHeader("Content-Length", Integer.toString(size));
    }

    public static void parseMDN(Message msg, X509Certificate receiver) throws Exception {
        MessageMDN mdn = msg.getMDN();
        MimeBodyPart mainPart = mdn.getData();
        ICryptoHelper ch = getCryptoHelper();

        if (ch.isSigned(mainPart)) {
            mainPart = getCryptoHelper().verify(mainPart, receiver);
        }

        MimeMultipart reportParts = new MimeMultipart(mainPart.getDataHandler().getDataSource());

        if (reportParts != null) {
            ContentType reportType = new ContentType(reportParts.getContentType());
            
            if (reportType.getBaseType().equalsIgnoreCase("multipart/report")) {
                int reportCount = reportParts.getCount();
                MimeBodyPart reportPart;

                for (int j = 0; j < reportCount; j++) {
                    reportPart = (MimeBodyPart) reportParts.getBodyPart(j);

                    if (reportPart.isMimeType("text/plain")) {
                        mdn.setText(reportPart.getContent().toString());
                    } else if (reportPart.isMimeType("message/disposition-notification")) {
                        InternetHeaders disposition = new InternetHeaders(reportPart
                                .getInputStream());
                        mdn.setAttribute(AS2MessageMDN.MDNA_REPORTING_UA, disposition.getHeader(
                                "Reporting-UA", ", "));
                        mdn.setAttribute(AS2MessageMDN.MDNA_ORIG_RECIPIENT, disposition.getHeader(
                                "Original-Recipient", ", "));
                        mdn.setAttribute(AS2MessageMDN.MDNA_FINAL_RECIPIENT, disposition.getHeader(
                                "Final-Recipient", ", "));
                        mdn.setAttribute(AS2MessageMDN.MDNA_ORIG_MESSAGEID, disposition.getHeader(
                                "Original-Message-ID", ", "));
                        mdn.setAttribute(AS2MessageMDN.MDNA_DISPOSITION, disposition.getHeader(
                                "Disposition", ", "));
                        mdn.setAttribute(AS2MessageMDN.MDNA_MIC, disposition.getHeader(
                                "Received-Content-MIC", ", "));
                    }
                }
            }
        }
    } 
    /**
     *  @description Verify disposition sytus is "processed" then check MIC is matched
     *  @param msg - the original message sent to the partner that the MDN relates to
     *  @return true if mdn processed  
     */
	public static boolean checkMDN(AS2Message msg, String originalMIC) throws DispositionException {
		Log logger = LogFactory.getLog(AS2Util.class.getSimpleName());
		/*
		 * The sender may return an error in the disposition and not set the MIC
		 * so check disposition first
		 */
		String disposition = msg.getMDN().getAttribute(AS2MessageMDN.MDNA_DISPOSITION);
		if (disposition != null && logger.isInfoEnabled())
			logger.info("received MDN [" + disposition + "]" + msg.getLoggingText());
		boolean dispositionHasWarning = false;
		try {
			new DispositionType(disposition).validate();
		} catch (DispositionException de) {
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
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		// get the returned mic from mdn object

		String returnMIC = msg.getMDN().getAttribute(AS2MessageMDN.MDNA_MIC);
		if (returnMIC == null || returnMIC.length() < 1) {
			if (dispositionHasWarning)
				logger.warn("Returned MIC not found but disposition has warning so might be normal.");
			else
				logger.error("Returned MIC not found so cannot validate returned message.");
			return false;
		}

		/* Returned-Content-MIC header and rfc822 headers can contain spaces all over the place.
		 * (not to mention comments!). Simple fix - delete all spaces.
		 * Since the partner could return the algorithm in different case to
		 * what was sent, remove the algorithm before compare
		 * The Algorithm is appended as a part of the MIC by adding a comma then
		 * optionally a space followed by the algorithm
		 */
		String retMicMinusAlg = returnMIC.substring(0, returnMIC.lastIndexOf(",")).replaceAll("\\s+", "");
		String origMicMinusAlg = originalMIC.substring(0, originalMIC.lastIndexOf(",")).replaceAll("\\s+", "");

		if (!retMicMinusAlg.equals(origMicMinusAlg)) {
			logger.error("MIC not matched, original MIC: " + originalMIC + " return MIC: " + returnMIC
					+ msg.getLoggingText());
			return false;
		}
		if (logger.isDebugEnabled())
			logger.debug("mic is matched, mic: " + returnMIC + msg.getLoggingText());

		return true;
	}
	// How many times should this message be sent?
	public static String retries(Map<Object,Object> options, String fallbackRetries) {
		String left;
		if (options == null || (left = (String) options.get(SenderModule.SOPT_RETRIES)) == null) {
				left = fallbackRetries;
		}
			
		if (left == null) left = SenderModule.DEFAULT_RETRIES;
		// Verify it is a valid integer
		try {
			Integer.parseInt(left);
		} catch (Exception e) {
			return SenderModule.DEFAULT_RETRIES;
		}
		return left;
	}

	/* @description Attempts to check if a resend should go ahead and if o decrements the resend count
	 *  and stores the decremented retry count in the options map. If the passed in retry count is null or invalid
	 *  it will fall back to a system default
	 */
    public static boolean resend(Processor processor, Object sourceClass, String how, Message msg, OpenAS2Exception cause, String tries) throws OpenAS2Exception {
		Log logger = LogFactory.getLog(AS2Util.class.getSimpleName());
		logger.debug("RESEND requested.... retries to go: " + tries);
		int retries = -1;
		if (tries == null) tries = SenderModule.DEFAULT_RETRIES;
		try {
			retries = Integer.parseInt(tries);
		} catch (Exception e) {
			logger.error("The retry count is not a valid integer value: " + tries);
		}
    	if (retries >= 0 && retries -- <= 0) return false;
        Map<Object,Object> options = new HashMap<Object,Object>();
        options.put(ResenderModule.OPTION_CAUSE, cause);
        options.put(ResenderModule.OPTION_INITIAL_SENDER, sourceClass);
        options.put(ResenderModule.OPTION_RESEND_METHOD, how);
        options.put(ResenderModule.OPTION_RETRIES, "" + retries);
        processor.handle(ResenderModule.DO_RESEND, msg, options);
        return true;
    }

}