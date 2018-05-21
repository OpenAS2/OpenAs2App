package org.openas2.lib.message;

import java.io.IOException;
import java.util.Enumeration;

import javax.mail.MessagingException;
import javax.mail.internet.ContentType;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;

import org.openas2.lib.util.MimeUtil;

public class MDNData {
    // report multipart content type
    public static final String REPORT_SUBTYPE = "report; report-type=disposition-notification";
    public static final String REPORT_TYPE = "multipart/" + REPORT_SUBTYPE;
    // text part content header
    public static final String TEXT_TYPE = "text/plain";
    public static final String TEXT_CHARSET = "us-ascii";
    public static final String TEXT_ENCODING = "7bit";
    // disposition content header
    public static final String DISPOSITION_TYPE = "message/disposition-notification";
    public static final String DISPOSITION_CHARSET = "us-ascii";
    public static final String DISPOSITION_ENCODING = "7bit";

    private EDIINTMessageMDN owner;
    private boolean dirty;
    private String disposition;
    private String finalRecipient;
    private String originalMessageID;
    private String originalRecipient;
    private String receivedContentMIC;
    private String reportingUA;
    private String text;

    public MDNData(EDIINTMessageMDN owner) {
        super();
        this.owner = owner;
    }

    public void update(MimeBodyPart data) throws MessagingException {
        // make sure all old cache values are cleared
        clearCache();

        if (data != null) {
            // verify the content type is multipart/report
            ContentType reportType = new ContentType(data.getContentType());

            if (reportType.getBaseType().equalsIgnoreCase("multipart/report")) {
                // convert the body part to a multipart
                MimeMultipart reportPart = MimeUtil.createMimeMultipart(data);

                // loop through each body part in the multipart/report and
                // extract
                // cache values
                int partCount = reportPart.getCount();
                MimeBodyPart currentPart;

                for (int i = 0; i < partCount; i++) {
                    // get the next body part and check it's content type
                    currentPart = (MimeBodyPart) reportPart.getBodyPart(i);

                    if (currentPart.isMimeType(TEXT_TYPE)) {
                        // found the MDN's text body part, save the value to
                        // cache
                        try {
                            setText(currentPart.getContent().toString());
                        } catch (IOException ioe) {
                            throw new MessagingException("Error getting text content: " + ioe.getMessage());
                        }
                    } else if (currentPart.isMimeType(DISPOSITION_TYPE)) {
                        try {
                            // found the MDN's disposition body part, parse it
                            // and
                            // save values to cache
                            InternetHeaders disposition = new InternetHeaders(currentPart.getInputStream());
                            setReportingUA(disposition.getHeader("Reporting-UA", ", "));
                            setOriginalRecipient(disposition.getHeader("Original-Recipient", ", "));
                            setFinalRecipient(disposition.getHeader("Final-Recipient", ", "));
                            String id = disposition.getHeader("Original-Message-ID", ", ");
                            if ((id != null) && id.startsWith("<") && id.endsWith(">")) {
                                id = id.substring(1, id.length() - 1);
                            }
                            setOriginalMessageID(id);
                            setDisposition(disposition.getHeader("Disposition", ", "));
                            setReceivedContentMIC(disposition.getHeader("Received-Content-MIC", ", "));
                        } catch (IOException ioe) {
                            throw new MessagingException("Error parsing disposition notification: " + ioe.getMessage());
                        }
                    }
                }
            }
        }        
    }

    public MimeBodyPart getData() throws MessagingException {
        try {
            // create the main report multipart
            MimeMultipart reportParts = createReportPart();

            // create the disposition notification part and add it to the report
            MimeBodyPart dispositionPart = createDispositionPart();
            reportParts.addBodyPart(dispositionPart);

            // create the text part and add it to the report
            MimeBodyPart textPart = createTextPart();
            reportParts.addBodyPart(textPart);

            // convert reportParts to a MimeBodyPart and save it
            MimeBodyPart reportPart = MimeUtil.createMimeBodyPart(reportParts);
            
            return reportPart;
        } catch (IOException ioe) {
            throw new MessagingException("Error creating data: " + ioe.getMessage());
        }
    }

    public EDIINTMessageMDN getOwner() {
        return owner;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
        if (dirty) {
            getOwner().setContentType(REPORT_TYPE);
        }
    }
    
    public void setDisposition(String disposition) {
        this.disposition = disposition;
        setDirty(true);
    }

    public String getDisposition() {
        return this.disposition;
    }

    public void setFinalRecipient(String recipient) {
        this.finalRecipient = recipient;
        setDirty(true);
    }

    public String getFinalRecipient() {
        return this.finalRecipient;
    }

    public void setOriginalMessageID(String messageID) {
        this.originalMessageID = messageID;
        setDirty(true);
    }

    public String getOriginalMessageID() {
        return this.originalMessageID;
    }

    public void setOriginalRecipient(String recipient) {
        this.originalRecipient = recipient;
        setDirty(true);
    }

    public String getOriginalRecipient() {
        return this.originalRecipient;
    }

    public void setReceivedContentMIC(String mic) {
        this.receivedContentMIC = mic;
        setDirty(true);
    }

    public String getReceivedContentMIC() {
        return this.receivedContentMIC;
    }

    public void setReportingUA(String reportingUA) {
        this.reportingUA = reportingUA;
        setDirty(true);
    }

    public String getReportingUA() {
        return this.reportingUA;
    }

    public void setText(String text) {
        this.text = text;
        setDirty(true);
    }

    public String getText() {
        return this.text;
    }

    protected void clearCache() {
        this.disposition = null;
        this.finalRecipient = null;
        this.originalMessageID = null;
        this.originalRecipient = null;
        this.receivedContentMIC = null;
        this.reportingUA = null;
        this.text = null;
    }

    protected MimeBodyPart createDispositionPart() throws IOException, MessagingException {
        MimeBodyPart dispositionPart = new MimeBodyPart();

        InternetHeaders dispValues = new InternetHeaders();
        dispValues.setHeader("Reporting-UA", getReportingUA());
        dispValues.setHeader("Original-Recipient", getOriginalRecipient());
        dispValues.setHeader("Final-Recipient", getFinalRecipient());
        dispValues.setHeader("Original-Message-ID", "<" + getOriginalMessageID() + ">");
        dispValues.setHeader("Disposition", getDisposition());
        dispValues.setHeader("Received-Content-MIC", getReceivedContentMIC());

        Enumeration<String> dispEnum = dispValues.getAllHeaderLines();
        StringBuffer dispData = new StringBuffer();

        while (dispEnum.hasMoreElements()) {
            dispData.append(dispEnum.nextElement()).append("\r\n");
        }

        dispData.append("\r\n");

        String dispText = dispData.toString();
        dispositionPart.setContent(dispText, DISPOSITION_TYPE);
        dispositionPart.setHeader("Content-Type", DISPOSITION_TYPE);

        return dispositionPart;
    }

    protected MimeMultipart createReportPart() throws MessagingException {
        MimeMultipart reportParts = new MimeMultipart();
        reportParts.setSubType(REPORT_SUBTYPE);

        return reportParts;
    }

    protected MimeBodyPart createTextPart() throws IOException, MessagingException {
        MimeBodyPart textPart = new MimeBodyPart();        
        String text = getText() + "\r\n";
        textPart.setContent(text, TEXT_TYPE);
        textPart.setHeader("Content-Type", TEXT_TYPE);        

        return textPart;
    }
}