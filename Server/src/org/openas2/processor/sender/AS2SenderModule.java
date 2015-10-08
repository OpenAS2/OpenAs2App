package org.openas2.processor.sender;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Map;

import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bouncycastle.cms.jcajce.ZlibCompressor;
import org.bouncycastle.mail.smime.SMIMECompressedGenerator;
import org.bouncycastle.mail.smime.SMIMEException;
import org.bouncycastle.operator.OutputCompressor;
import org.openas2.DispositionException;
import org.openas2.OpenAS2Exception;
import org.openas2.WrappedException;
import org.openas2.cert.CertificateFactory;
import org.openas2.lib.helper.ICryptoHelper;
import org.openas2.message.AS2Message;
import org.openas2.message.AS2MessageMDN;
import org.openas2.message.DataHistoryItem;
import org.openas2.message.FileAttribute;
import org.openas2.message.Message;
import org.openas2.message.MessageMDN;
import org.openas2.message.NetAttribute;
import org.openas2.params.InvalidParameterException;
import org.openas2.partner.AS2Partnership;
import org.openas2.partner.Partnership;
import org.openas2.partner.SecurePartnership;
import org.openas2.processor.storage.StorageModule;
import org.openas2.util.AS2Util;
import org.openas2.util.DateUtil;
import org.openas2.util.DispositionOptions;
import org.openas2.util.DispositionType;
import org.openas2.util.IOUtilOld;
import org.openas2.util.Profiler;
import org.openas2.util.ProfilerStub;

public class AS2SenderModule extends HttpSenderModule {
	
	private Log logger = LogFactory.getLog(AS2SenderModule.class.getSimpleName());
    private String calculatedMIC = null;
	
	public boolean canHandle(String action, Message msg, Map<Object, Object> options) {
        if (!action.equals(SenderModule.DO_SEND)) {
            return false;
        }

        return (msg instanceof AS2Message);
    }

    public void handle(String action, Message msg, Map<Object, Object> options) throws OpenAS2Exception {
    	
 
        logger.info("message submitted"+msg.getLoggingText());

        if (!(msg instanceof AS2Message)) {
            throw new OpenAS2Exception("Can't send non-AS2 message");
        }

        // verify all required information is present for sending
        checkRequired(msg);
        
        int retries = retries (options);
        
        try {
            // encrypt and/or sign and/or compress the message if needed
            MimeBodyPart securedData = secure(msg);
            msg.setContentType(securedData.getContentType());

            // Create the HTTP connection and set up headers
            String url = msg.getPartnership().getAttribute(AS2Partnership.PA_AS2_URL);
            HttpURLConnection conn = getConnection(url, true, true, false, "POST");
            
            try {
                updateHttpHeaders(conn, msg);
                msg.setAttribute(NetAttribute.MA_DESTINATION_IP, conn.getURL().getHost());
                msg.setAttribute(NetAttribute.MA_DESTINATION_PORT, Integer.toString(conn.getURL().getPort()));

                logger.info("Connecting to: " + url+msg.getLoggingText());

                // Note: closing this stream causes connection abort errors on some AS2 servers
                OutputStream messageOut = conn.getOutputStream();

                // Transfer the data
                InputStream messageIn = securedData.getInputStream();

                try {
                    ProfilerStub transferStub = Profiler.startProfile();

                    int bytes = IOUtilOld.copy(messageIn, messageOut);

                    Profiler.endProfile(transferStub);
                    logger.info("transferred " + IOUtilOld.getTransferRate(bytes, transferStub)+msg.getLoggingText());
                } finally {
                    messageIn.close();
                }
                //			Check the HTTP Response code
                if ((conn.getResponseCode() != HttpURLConnection.HTTP_OK)
                    && (conn.getResponseCode() != HttpURLConnection.HTTP_CREATED)
                    && (conn.getResponseCode() != HttpURLConnection.HTTP_ACCEPTED)
                    && (conn.getResponseCode() != HttpURLConnection.HTTP_PARTIAL)
                    && (conn.getResponseCode() != HttpURLConnection.HTTP_NO_CONTENT)
                     )
                 {
                	logger.error("error url " +url.toString()
                			+ " rc " +conn.getResponseCode()
                			+ " rm " +conn.getResponseMessage());
                    throw new HttpResponseException(url.toString(), conn.getResponseCode(), conn.getResponseMessage());
                }

                // Asynch MDN 2007-03-12
                // Receive an MDN
				try {
					// Receive an MDN
					if (msg.isRequestingMDN()) {
						// Check if the AsyncMDN is required
						if (msg.getPartnership().getAttribute(
								AS2Partnership.PA_AS2_RECEIPT_OPTION) == null) {
							receiveMDN((AS2Message) msg, conn, getCalculatedMIC()); // go ahead  to receive  sync MDN
				     		logger.info("message sent"+msg.getLoggingText());
						}
					}

				} catch (DispositionException de) { // If a disposition error
					// hasn't been handled, the
					// message transfer
					// was not successful
					throw de;
				} catch (OpenAS2Exception oae) { // Don't resend or fail,
					// just log an error if one
					// occurs while
					// receiving the MDN

					OpenAS2Exception oae2 = new OpenAS2Exception(
							"Message was sent but an error occured while receiving the MDN");
					oae2.initCause(oae);
					oae2.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
					oae2.terminate();
				}

			} finally {
				conn.disconnect();
			}
            
        } catch (HttpResponseException hre) { // Resend if the HTTP Response
												// has an error code
            logger.error("error hre " +hre.getMessage());
            hre.terminate();
            resend(msg, hre, retries);
        } catch (IOException ioe) { // Resend if a network error occurs during
									// transmission
            logger.warn("Error making network connection: " , ioe);
            WrappedException wioe = new WrappedException(ioe);
            wioe.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
            wioe.terminate();

            resend(msg, wioe, retries);
        } catch (Exception e) { // Propagate error if it can't be handled by a resend
            logger.warn("Unkonwn Error making network connection: " , e);
            throw new WrappedException(e);
        }
    }

	private void compress(Message msg, OutputCompressor outputCompressor) throws SMIMEException, OpenAS2Exception
	{
		SMIMECompressedGenerator sCompGen = new SMIMECompressedGenerator();
		String encodeType = msg.getPartnership().getAttribute(Partnership.PA_CONTENT_TRANSFER_ENCODING);
        if (encodeType == null) encodeType="8bit";
        sCompGen.setContentTransferEncoding(encodeType);
		MimeBodyPart smime = sCompGen.generate(msg.getData(), outputCompressor);
		msg.addHeader("Content-Transfer-Encoding", encodeType);
		msg.setData(smime);
		if (logger.isTraceEnabled())
		{
	    	try
			{
				logger.trace("Compressed MIME msg AFTER COMPRESSION Content-Type:" + smime.getContentType());
				logger.trace("Compressed MIME msg AFTER COMPRESSION Content-Type Header:" + smime.getHeader("Content-Type"));
				logger.trace("Compressed MIME msg AFTER COMPRESSION Content-Disposition:" + smime.getDisposition());
			} catch (MessagingException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    	logger.trace("Msg AFTER COMPRESSION Content-Type:" + msg.getContentType());			
		}

	}

    //Asynch MDN 2007-03-12
    //added originalmic

    /**
     * @param msg AS2Message
     * @param conn URLConnection
     * @param originalmic mic value from original msg
     */
    protected void receiveMDN(AS2Message msg, HttpURLConnection conn, String originalmic) throws OpenAS2Exception, IOException {
        try {
            // Create a MessageMDN and copy HTTP headers
            MessageMDN mdn = new AS2MessageMDN(msg);
            copyHttpHeaders(conn, mdn.getHeaders());

            // Receive the MDN data
            InputStream connIn = conn.getInputStream();
            ByteArrayOutputStream mdnStream = new ByteArrayOutputStream();

            try {
                //			Retrieve the message content
                if (mdn.getHeader("Content-Length") != null) {
                    try {
                        int contentSize = Integer.parseInt(mdn.getHeader("Content-Length"));

                        IOUtilOld.copy(connIn, mdnStream, contentSize);
                    } catch (NumberFormatException nfe) {
                        IOUtilOld.copy(connIn, mdnStream);
                    }
                } else {
                    IOUtilOld.copy(connIn, mdnStream);
                }
            } finally {
                connIn.close();
            }

            MimeBodyPart part = new MimeBodyPart(mdn.getHeaders(), mdnStream.toByteArray());

            msg.getMDN().setData(part);

            // get the MDN partnership info
            mdn.getPartnership().setSenderID(AS2Partnership.PID_AS2, mdn.getHeader("AS2-From"));
            mdn.getPartnership().setReceiverID(AS2Partnership.PID_AS2, mdn.getHeader("AS2-To"));
            getSession().getPartnershipFactory().updatePartnership(mdn, false);

            CertificateFactory cFx = getSession().getCertificateFactory();
            X509Certificate senderCert = cFx.getCertificate(mdn, Partnership.PTYPE_SENDER);

            AS2Util.parseMDN(msg, senderCert);

            getSession().getProcessor().handle(StorageModule.DO_STOREMDN, msg, null);

            String disposition = msg.getMDN().getAttribute(AS2MessageMDN.MDNA_DISPOSITION);

            logger.info("received MDN [" + disposition + "]"+msg.getLoggingText());
            
            //Asynch MDN  2007-03-12
            // Verify if the original mic is equal to the mic in returned MDN 
            String returnmic = msg.getMDN().getAttribute(AS2MessageMDN.MDNA_MIC); 
             
            if ( ! returnmic.replaceAll(" ", "").equals(originalmic.replaceAll(" ", ""))) {
            	//file was sent completely but the returned mic was not matched,  
            	// don't know it needs or needs not to be resent ? it's depended on what ! 
            	// anyway, just log the warning message here.
            	/* TODO: RFC 6362 specifies that the sent attachments should be considered invalid and retransmitted
            	 */
                logger.warn("MIC is not matched, original mic: " + originalmic + " return mic: "+ returnmic+msg.getLoggingText()); 
            } 
            else { 
            logger.info("MIC is matched, mic: " + returnmic+msg.getLoggingText()); 
            } 

            try {
                new DispositionType(disposition).validate();
            } catch (DispositionException de) {
                de.setText(msg.getMDN().getText());

                if ((de.getDisposition() != null) && de.getDisposition().isWarning()) {
                    de.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
                    de.terminate();
                } else {
                    throw de;
                }
            }
        } catch (Exception e) {
            if (e instanceof IOException) {
                throw (IOException) e;
            }
            WrappedException we = new WrappedException(e);
            we.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
            throw we;
        }
    }
    
    protected void checkRequired(Message msg) throws InvalidParameterException {
        Partnership partnership = msg.getPartnership();

        try {
            InvalidParameterException.checkValue(msg, "ContentType", msg.getContentType());
            InvalidParameterException.checkValue(msg, "Attribute: " + AS2Partnership.PA_AS2_URL, partnership
                    .getAttribute(AS2Partnership.PA_AS2_URL));
            InvalidParameterException.checkValue(msg, "Receiver: " + AS2Partnership.PID_AS2, partnership
                    .getReceiverID(AS2Partnership.PID_AS2));
            InvalidParameterException.checkValue(msg, "Sender: " + AS2Partnership.PID_AS2, partnership
                    .getSenderID(AS2Partnership.PID_AS2));
            InvalidParameterException.checkValue(msg, "Subject", msg.getSubject());
            InvalidParameterException.checkValue(msg, "Sender: " + Partnership.PID_EMAIL, partnership
                    .getSenderID(Partnership.PID_EMAIL));
            InvalidParameterException.checkValue(msg, "Message Data", msg.getData());
        } catch (InvalidParameterException rpe) {
            rpe.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
            throw rpe;
        }
    }

 

    private void resend(Message msg, OpenAS2Exception cause, int tries) throws OpenAS2Exception {
    	if (resend (SenderModule.DO_SEND, msg, cause, tries)) return;
        // Oh dear, we've run out of reetries, do something interesting.
    	// TODO create a fake failure MDN
    	logger.info("Message abandoned"+msg.getLoggingText());
    }

    // Returns a MimeBodyPart or MimeMultipart object
    protected MimeBodyPart secure(Message msg) throws Exception {
        // Set up encrypt/sign variables
        MimeBodyPart dataBP = msg.getData();
        /* Based on RFC4130, RFC6362 and RFC5042, the MIC is calculated as follows:
         *        Signed message - MIME header fields and content that is to be signed which may or
         *                          may not be encrypted and/or compressed.
         *        
         *        Unsigned encrypted message - data content including all MIME header fields and any applied Content-Transfer-Encoding
         *                           prior to encryption and/or compression
         *  
         *  So essentially, calculate the MIC prior to doing any compression, signing or encryption of the message
         *   but include headers for unsigned messages (see RFC4130 section 7.3.1 for details)
         */

        Partnership partnership = msg.getPartnership();
        
        boolean encrypt = partnership.getAttribute(SecurePartnership.PA_ENCRYPT) != null;
        boolean sign = partnership.getAttribute(SecurePartnership.PA_SIGN) != null;
        
        setCalculatedMIC(calcAndStoreMic(msg, (sign || encrypt)));

        // Check if compression is enabled
    	String compressionType = msg.getPartnership().getAttribute("compression_type");
		if (logger.isTraceEnabled()) logger.trace("Compression type from config: " + compressionType);
    	boolean isCompress = false;
    	OutputCompressor compressor = null;
    	if (compressionType != null)
    	{
    		if (compressionType.equalsIgnoreCase(ICryptoHelper.COMPRESSION_ZLIB))
    		{
    			compressor = new ZlibCompressor();
    			isCompress = true;
    		}
    		else
    			throw new OpenAS2Exception("Unsupported compression type: " + compressionType);
    	}
    	String compressionMode = msg.getPartnership().getAttribute("compression_mode");
    	boolean isCompressBeforeSign = true; // Defaults to compressing the entire message before signing and encryption
    	if (compressionMode != null && compressionMode.equalsIgnoreCase("compress-after-signing"))
    	    isCompressBeforeSign = false;
    	if (isCompress && isCompressBeforeSign)
    	{
    		if (logger.isTraceEnabled()) logger.trace("Compressing outbound message before signing...");
    		compress(msg, compressor);
    	}
        // Encrypt and/or sign the data if requested
		CertificateFactory certFx = getSession().getCertificateFactory();

		// Sign the data if requested
		if (sign)
		{
			X509Certificate senderCert = certFx.getCertificate(msg, Partnership.PTYPE_SENDER);

			PrivateKey senderKey = certFx.getPrivateKey(msg, senderCert);
			String digest = partnership.getAttribute(SecurePartnership.PA_SIGN);

			if (logger.isDebugEnabled())
				logger.debug("Params for creating signed body part:: DATA: " + dataBP + "\n SIGN DIGEST: " + digest
						+ "\n CERT ALG NAME EXTRACTED: " + senderCert.getSigAlgName()
						+ "\n CERT PUB KEY ALG NAME EXTRACTED: " + senderCert.getPublicKey().getAlgorithm());

			dataBP = AS2Util.getCryptoHelper().sign(dataBP, senderCert, senderKey, digest);

			// Asynch MDN 2007-03-12
			DataHistoryItem historyItem = new DataHistoryItem(dataBP.getContentType());
			// *** add one more item to msg history
			msg.getHistory().getItems().add(historyItem);

			if (logger.isDebugEnabled())
				logger.debug("signed data" + msg.getLoggingText());
			if (isCompress && !isCompressBeforeSign)
			{
				if (logger.isDebugEnabled())
					logger.debug("Compressing outbound message after signing...");
				compress(msg, compressor);
			}
		}

    	if (isCompress && !isCompressBeforeSign)
    	{
    		if (logger.isTraceEnabled()) logger.trace("Compressing outbound message after signing...");
    		compress(msg, compressor);
    	}
		// Encrypt the data if requested
		if (encrypt)
		{
			String algorithm = partnership.getAttribute(SecurePartnership.PA_ENCRYPT);

			X509Certificate receiverCert = certFx.getCertificate(msg, Partnership.PTYPE_RECEIVER);
			dataBP = AS2Util.getCryptoHelper().encrypt(dataBP, receiverCert, algorithm);

			// Asynch MDN 2007-03-12
			DataHistoryItem historyItem = new DataHistoryItem(dataBP.getContentType());
			// *** add one more item to msg history
			msg.getHistory().getItems().add(historyItem);

			logger.debug("encrypted data" + msg.getLoggingText());
		}

        return dataBP;
    }

    protected void updateHttpHeaders(HttpURLConnection conn, Message msg) {
        Partnership partnership = msg.getPartnership();

        conn.setRequestProperty("Connection", "close, TE");
        conn.setRequestProperty("User-Agent", "OpenAS2 AS2Sender");

        conn.setRequestProperty("Date", DateUtil.formatDate("EEE, dd MMM yyyy HH:mm:ss Z"));
        conn.setRequestProperty("Message-ID", msg.getMessageID());
        conn.setRequestProperty("Mime-Version", "1.0"); // make sure this is the encoding used in the msg, run TBF1
        conn.setRequestProperty("Content-type", msg.getContentType());
        conn.setRequestProperty("AS2-Version", "1.1"); // RFC6017 - 1.1 supports compression, 1.2 additionally supports EDIINT-Features
        //conn.setRequestProperty("EDIINT-Features", "CEM,multiple-attachments"); // TODO (possibly???)
        conn.setRequestProperty("Content-Transfer-Encoding", msg.getHeader("Content-Transfer-Encoding"));
        conn.setRequestProperty("Recipient-Address", partnership.getAttribute(AS2Partnership.PA_AS2_URL));
        conn.setRequestProperty("AS2-To", partnership.getReceiverID(AS2Partnership.PID_AS2));
        conn.setRequestProperty("AS2-From", partnership.getSenderID(AS2Partnership.PID_AS2));
        conn.setRequestProperty("Subject", msg.getSubject());
        conn.setRequestProperty("From", partnership.getSenderID(Partnership.PID_EMAIL));

        String dispTo = partnership.getAttribute(AS2Partnership.PA_AS2_MDN_TO);

        if (dispTo != null) {
            conn.setRequestProperty("Disposition-Notification-To", dispTo);
        }

        String dispOptions = partnership.getAttribute(AS2Partnership.PA_AS2_MDN_OPTIONS);

        if (dispOptions != null) {
            conn.setRequestProperty("Disposition-Notification-Options", dispOptions);
        }

        //Asynch MDN 2007-03-12
        String receiptOption = partnership.getAttribute(AS2Partnership.PA_AS2_RECEIPT_OPTION);
        if (receiptOption != null) {
            conn.setRequestProperty("Receipt-delivery-option", receiptOption);
        }
        
        //As of 2007-06-01
        
        String contentDisp = msg.getContentDisposition();
        if (contentDisp != null) {
        	conn.setRequestProperty("Content-Disposition", contentDisp);
        }
        
    }
  
    
    //Asynch MDN  2007-03-12
    /** 
     * for storing original mic & outgoing file into pending information file
     * @param msg AS2Message
     * @param mic  
     * @throws WrappedException 
     */
    protected void storePendingInfo(AS2Message msg, String mic) throws WrappedException {
		try {
			
			
			
			String pendingFolder = (String) getSession().getComponent("processor").getParameters().get("pendingmdninfo");
			

			FileOutputStream fos = new FileOutputStream(
					pendingFolder + "/"
               			+ msg.getMessageID().substring(1,
							msg.getMessageID().length() - 1));
			fos.write((mic + "\n").getBytes());
			logger.debug("Original MIC is : " + mic+msg.getLoggingText());

			// input pending folder & original outgoing file name to get and
			// unique file name
			// in order to avoid file overwritting.
			String pendingFile = (String) getSession().getComponent("processor").getParameters().get("pendingmdn")
					+ "/" + msg.getMessageID().substring(1,
							msg.getMessageID().length() - 1);

		    logger.info("Save Original mic & message id. information into folder : "
							+ pendingFile+msg.getLoggingText());
			fos.write(pendingFile.getBytes());
			fos.close();
			msg.setAttribute(FileAttribute.MA_PENDINGFILE, pendingFile);
			msg.setAttribute(FileAttribute.MA_STATUS, FileAttribute.MA_PENDING);

		} catch (Exception e) {
			
			WrappedException we = new WrappedException(e);
            we.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
            throw we;
  
			
		}
	} 
    
    protected String calcAndStoreMic(Message msg, boolean includeHeaders) throws Exception
    {
		// Calculate and get the original mic
		// includeHeaders = (msg.getHistory().getItems().size() > 1);
		
		
		DispositionOptions dispOptions = new DispositionOptions(
				msg.getPartnership().getAttribute(AS2Partnership.PA_AS2_MDN_OPTIONS));
		String mic = AS2Util.getCryptoHelper().calculateMIC(
				msg.getData(), dispOptions.getMicalg(),
				includeHeaders);

        if (msg.getPartnership().getAttribute(
				AS2Partnership.PA_AS2_RECEIPT_OPTION) != null) {
        	// if yes : PA_AS2_RECEIPT_OPTION) != null
			// then keep the original mic & message id.
			// then wait for the another HTTP call by receivers

			storePendingInfo((AS2Message) msg, mic);
		}
        return mic;

    }

    private String getCalculatedMIC()
	{
		return calculatedMIC;
	}

	private void setCalculatedMIC(String calculatedMIC)
	{
		this.calculatedMIC = calculatedMIC;
	}

}