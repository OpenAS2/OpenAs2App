package org.openas2.lib;

import java.security.Key;
import java.security.cert.Certificate;

import javax.mail.internet.MimeBodyPart;

import org.openas2.Session;
import org.openas2.lib.cert.ICertificateChooser;
import org.openas2.lib.helper.EDIINTHelper;
import org.openas2.lib.helper.ICryptoHelper;
import org.openas2.lib.message.AS1Message;
import org.openas2.lib.message.AS1MessageMDN;
import org.openas2.lib.message.AS2Message;
import org.openas2.lib.message.AS2MessageMDN;
import org.openas2.lib.message.Disposition;
import org.openas2.lib.message.DispositionException;
import org.openas2.lib.message.DispositionOptions;
import org.openas2.lib.message.EDIINTMessage;
import org.openas2.lib.message.EDIINTMessageMDN;
import org.openas2.lib.message.MDNData;
import org.openas2.lib.partner.IPartnershipChooser;
import org.openas2.message.Message;
import org.openas2.partner.Partnership;
import org.openas2.util.Properties;

public class MDNEngine {
    private EDIINTHelper ediintHelper;
    private ICertificateChooser certificateChooser;
    private IPartnershipChooser partnershipChooser;

    public MDNEngine(EDIINTHelper ediintHelper, ICertificateChooser certificateChooser,
            IPartnershipChooser partnershipChooser) {
        super();
        this.ediintHelper = ediintHelper;
        this.certificateChooser = certificateChooser;
        this.partnershipChooser = partnershipChooser;
    }

    public EDIINTMessageMDN generateMDN(EDIINTMessage msg, EngineResults results)
            throws MDNException {
        if (msg instanceof AS1Message) {
            return createAS1MDN((AS1Message) msg, results);
        } else if (msg instanceof AS2Message) {
            return createAS2MDN((AS2Message) msg, results);
        } else {
            throw new MDNException("Unsupported message type: " + msg.getClass().getName());
        }
    }

    public EDIINTHelper getEDIINTHelper() {
        return ediintHelper;
    }

    public void setEDIINTHelper(EDIINTHelper ediintHelper) {
        this.ediintHelper = ediintHelper;
    }

    public ICryptoHelper getCryptoHelper() {
        return getEDIINTHelper().getCryptoHelper();
    }

    public ICertificateChooser getCertificateChooser() {
        return certificateChooser;
    }

    public void setCertificateChooser(ICertificateChooser certificateChooser) {
        this.certificateChooser = certificateChooser;
    }

    public IPartnershipChooser getPartnershipChooser() {
        return partnershipChooser;
    }

    public void setPartnershipChooser(IPartnershipChooser partnershipChooser) {
        this.partnershipChooser = partnershipChooser;
    }

    protected AS1MessageMDN createAS1MDN(AS1Message msg, EngineResults results) {
        throw new UnsupportedOperationException("Not Implemented");
    }

    protected AS2MessageMDN createAS2MDN(AS2Message msg, EngineResults results) throws MDNException {
        // create the mdn and copy over the header values
        AS2MessageMDN mdn = new AS2MessageMDN();
        mdn.setDefaults();
        mdn.setAS2From(msg.getAS2To());
        mdn.setAS2To(msg.getAS2From());
        if (results.getPartnership() != null) {
            mdn.setFrom(results.getPartnership().getSender().getContactEmail());
        } else {
            mdn.setFrom("unknown");
        }

        // generate the MDN's subject
        mdn.setSubject(generateMDNSubject(mdn, msg, results));

        // generate the MDN data
        MDNData mdnData = mdn.getMDNData();
        mdnData.setReportingUA(Properties.getProperty(Properties.APP_TITLE_PROP, "OpenAS2 Server"));
        mdnData.setOriginalRecipient("rfc822; " + msg.getAS2To());
        if (results.getPartnership() != null) {
            mdnData.setFinalRecipient("rfc822; "
                    + results.getPartnership().getReceiver().getAs2Id());
        } else {
            mdnData.setFinalRecipient("rfc822; " + msg.getAS2To());
        }
        mdnData.setOriginalMessageID(msg.getMessageID());
        mdnData.setDisposition(results.getDisposition());

        // generate the MDN text
        mdnData.setText(generateMDNText(mdn, msg, results));

        // check the message's disposition options
        DispositionOptions dispOptions = null;
        try {
            dispOptions = new DispositionOptions(msg.getDispositionNotificationOptions());
        } catch (OpenAS2Exception oae) {
            throw new MDNException("Invalid disposition options", oae);
        }

        // calculate the MIC if requested
        try {
            if (dispOptions.getMicAlgorithm() != null) {
                // NOTE: data headers must be included in MIC if the original
                // message was signed or encrypted
                boolean includeHeaders = results.getEncryption() != EngineResults.STATUS_NONE
                        && results.getSignature() != EngineResults.STATUS_NONE;
                String mic = getCryptoHelper().calculateMIC(msg.getData(),
                        dispOptions.getMicAlgorithm(), includeHeaders, ((Message)msg).getPartnership().isPreventCanonicalization());
                mdnData.setReceivedContentMIC(mic);
            }
        } catch (Exception e) {
            throw new MDNException("Error calculating MIC", e);
        }

        // sign the MDN data if requested
        try {
            if (dispOptions.getProtocol() != null) {
                ICertificateChooser certChooser = getCertificateChooser();
                Certificate senderCert = certChooser.getSenderCertificate(mdn);
                Key senderKey = certChooser.getSenderKey(mdn);
        		Partnership p = ((Message)msg).getPartnership();
                String contentTxfrEncoding =  p.getAttribute(Partnership.PA_CONTENT_TRANSFER_ENCODING);
                boolean isRemoveCmsAlgorithmProtectionAttr = "true".equalsIgnoreCase(p.getAttribute(Partnership.PA_REMOVE_PROTECTION_ATTRIB));
        		if (contentTxfrEncoding == null)
        			contentTxfrEncoding = Session.DEFAULT_CONTENT_TRANSFER_ENCODING;
                // sign the data using CryptoHelper
                MimeBodyPart signedData = getCryptoHelper().sign(mdn.getData(), senderCert,
                        senderKey, dispOptions.getMicAlgorithm(),contentTxfrEncoding, false, isRemoveCmsAlgorithmProtectionAttr);
                mdn.setData(signedData);
                mdn.setContentType(signedData.getContentType());
            }
        } catch (Exception e) {
            throw new MDNException("Error signing MDN", e);
        }

        // update the message ID to include the sender and receiver AS2 ID's
        mdn.setMessageID(mdn.generateMessageID());

        return mdn;
    }

    protected String generateMDNText(EDIINTMessageMDN mdn, EDIINTMessage msg, EngineResults results)
            throws MDNException {
        // get the MDN's disposition
        Disposition disposition = null;
        try {
            disposition = new Disposition(mdn.getMDNData().getDisposition());
        } catch (DispositionException de) {
            throw new MDNException("Error generating MDN text: " + de.getMessage(), de);
        }

        // Append the sender and receiver ID's to the message
        StringBuffer text = new StringBuffer();
        text.append("Sender ID:    ").append(msg.getSenderID()).append(System.lineSeparator());
        text.append("Receiver ID:  ").append(msg.getReceiverID())
                .append(System.lineSeparator());

        // Append true/false of whether the message was decrypted and/or
        // verified
        String decryption = results.getStatusDescription(results.getEncryption());
        text.append("Decryption:   ").append(decryption).append(System.lineSeparator());
        String verification = results.getStatusDescription(results.getSignature());
        text.append("Verification: ").append(verification).append(System.lineSeparator());

        text.append(System.lineSeparator());
        // If the message was not processed successfully
        if (disposition.isError()) {
            text.append("The message could not be processed: ");
        } else if (disposition.isWarning()) {
            text.append("The message was processed with a warning: ");
        } else {
            text.append("The message was processed successfully: ");
        }
        text.append(disposition);

        return text.toString();
    }

    protected String generateMDNSubject(EDIINTMessageMDN mdn, EDIINTMessage msg,
            EngineResults results) throws MDNException {
        StringBuffer buf = new StringBuffer();
        buf.append(msg.getSenderID()).append(" -> ").append(msg.getReceiverID());
        buf.append(" - ").append(msg.getMessageID());
        return buf.toString();
    }
}