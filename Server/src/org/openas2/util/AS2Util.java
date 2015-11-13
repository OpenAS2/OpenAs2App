package org.openas2.util;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

import javax.mail.internet.ContentType;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;

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
}