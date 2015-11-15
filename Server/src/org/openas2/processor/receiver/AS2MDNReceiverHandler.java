package org.openas2.processor.receiver;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.activation.DataHandler;
import javax.mail.MessagingException;
import javax.mail.internet.ContentType;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.DispositionException;
import org.openas2.OpenAS2Exception;
import org.openas2.WrappedException;
import org.openas2.cert.CertificateFactory;
import org.openas2.message.AS2Message;
import org.openas2.message.AS2MessageMDN;
import org.openas2.message.MessageMDN;
import org.openas2.partner.AS2Partnership;
import org.openas2.partner.Partnership;
import org.openas2.processor.resender.ResenderModule;
import org.openas2.processor.sender.SenderModule;
import org.openas2.processor.storage.StorageModule;
import org.openas2.util.AS2Util;
import org.openas2.util.ByteArrayDataSource;
import org.openas2.util.HTTPUtil;
import org.openas2.util.IOUtilOld;

public class AS2MDNReceiverHandler implements NetModuleHandler {
    private AS2MDNReceiverModule module;

    private Log logger = LogFactory.getLog(AS2MDNReceiverHandler.class.getSimpleName());

    
    public AS2MDNReceiverHandler(AS2MDNReceiverModule module) {
        super();
        this.module = module;
    }

    public String getClientInfo(Socket s) {
        return " " + s.getInetAddress().getHostAddress() + " " + Integer.toString(s.getPort());
    }

    public AS2MDNReceiverModule getModule() {
        return module;
    }

    public void handle(NetModule owner, Socket s) {
    	
    	if (logger.isInfoEnabled()) logger.info("incoming connection"+ " [" + getClientInfo(s) + "]");

        AS2Message msg = new AS2Message();
        
        byte[] data = null;


        // Read in the message request, headers, and data
        try {
            data = HTTPUtil.readData(s, msg);
            //Asynch MDN 2007-03-12
            //check if the requested URL is defined in attribute "as2_receipt_option"  
            //in one of partnerships, if yes, then process incoming AsyncMDN 
            if (logger.isInfoEnabled())
            	logger.info("incoming connection for receiving AsyncMDN"+ " [" + getClientInfo(s) + "]" + msg.getLoggingText());
			ContentType receivedContentType;
                
            MimeBodyPart receivedPart = new MimeBodyPart(msg.getHeaders(), data); 
            msg.setData(receivedPart);
            receivedContentType = new ContentType(receivedPart.getContentType());
                 
            receivedContentType = new ContentType(msg.getHeader("Content-Type"));

            //MimeBodyPart receivedPart = new MimeBodyPart();
            receivedPart.setDataHandler(new DataHandler(new ByteArrayDataSource(data, receivedContentType
                        .toString(), null)));
            receivedPart.setHeader("Content-Type", receivedContentType.toString());
                
            msg.setData(receivedPart);
   			receiveMDN(msg, data, s.getOutputStream());

            
        } catch (Exception e) {
            NetException ne = new NetException(s.getInetAddress(), s.getPort(), e);
            ne.terminate();
        }

    }

 
/**
 * method for receiving & processing Async MDN sent from receiver.
 */ 
 protected void receiveMDN(AS2Message msg, byte[] data, OutputStream out)
			throws OpenAS2Exception, IOException {
		try {
			// Create a MessageMDN and copy HTTP headers
			MessageMDN mdn = new AS2MessageMDN(msg); 
			// copy headers from msg to MDN from msg 
			mdn.setHeaders(msg.getHeaders()); 
			MimeBodyPart part = new MimeBodyPart(mdn.getHeaders(), data); 
			msg.getMDN().setData(part); 
			 
			// get the MDN partnership info 
			mdn.getPartnership().setSenderID(AS2Partnership.PID_AS2, mdn.getHeader("AS2-From")); 
			mdn.getPartnership().setReceiverID(AS2Partnership.PID_AS2, mdn.getHeader("AS2-To")); 
			getModule().getSession().getPartnershipFactory().updatePartnership(mdn, false); 
			 
			CertificateFactory cFx = getModule().getSession().getCertificateFactory(); 
			X509Certificate senderCert = cFx.getCertificate(mdn, Partnership.PTYPE_SENDER); 
			 
			AS2Util.parseMDN(msg, senderCert); 
			 
			// in order to name & save the mdn with the original AS2-From + AS2-To + Message id., 
			// the 3 msg attributes have to be reset before calling MDNFileModule 
			msg.getPartnership().setReceiverID(AS2Partnership.PID_AS2, mdn.getHeader("AS2-From")); 
			msg.getPartnership().setSenderID(AS2Partnership.PID_AS2, mdn.getHeader("AS2-To")); 
			getModule().getSession().getPartnershipFactory().updatePartnership(msg, false); 
			msg.setMessageID(msg.getMDN().getAttribute(AS2MessageMDN.MDNA_ORIG_MESSAGEID)); 
			getModule().getSession().getProcessor().handle(StorageModule.DO_STOREMDN, msg, null); 

			// use original message ID to open the pending information file from pendinginfo folder.
			String ORIG_MESSAGEID = msg.getMDN().getAttribute(
					AS2MessageMDN.MDNA_ORIG_MESSAGEID);
			String pendinginfofile = (String) this.getModule().getSession().getComponent("processor").getParameters().get("pendingmdninfo")
					+ "/"
					+ ORIG_MESSAGEID.substring(1, ORIG_MESSAGEID.length() - 1);
			if (logger.isDebugEnabled()) logger.debug("Pending info file to retrieve data from in Async MDN receiver: " + pendinginfofile);
			BufferedReader pendinginfo = new BufferedReader(new FileReader(pendinginfofile));

			// Get the original MIC from the first line of pending information file
			String originalMIC = pendinginfo.readLine();
			// Get the retry count for number of resends to go from the second line of pending information file
			String retries = pendinginfo.readLine();
			if (logger.isDebugEnabled()) logger.debug("RETRY COUNT from pending info file: " + retries);
			msg.setOption(ResenderModule.OPTION_RETRIES, retries);
			// Get the original pending file from the third line of pending information file
			File fpendingfile = new File(pendinginfo.readLine());
			pendinginfo.close();
			
			if (logger.isTraceEnabled())
			{
				logger.trace("Pending MDN Info file to process: " + fpendingfile.getAbsolutePath());
			}

			try {
				boolean responseVerifiedOK = AS2Util.checkMDN(msg, originalMIC);
				/* If the MDN was successfully received send correct HTTP response irrespective of possible error
				 * conditions due to disposition errors or MIC mismatch
				 */
				HTTPUtil.sendHTTPResponse(out, HttpURLConnection.HTTP_OK, false);
				if (responseVerifiedOK) {
					HTTPUtil.sendHTTPResponse(out, HttpURLConnection.HTTP_OK, false);
					// delete the pendinginfo & pending file if mic is matched
					File fpendinginfofile = new File(pendinginfofile);
					if (logger.isDebugEnabled())
						logger.debug("delete pendinginfo file : " + fpendinginfofile.getAbsolutePath()
								+ msg.getLoggingText());

					fpendinginfofile.delete();

					if (logger.isDebugEnabled())
						logger.debug("delete pending file : " + fpendingfile.getName() + " from pending folder : "
								+ fpendingfile.getParent() + msg.getLoggingText());
					fpendingfile.delete();
				} else
				{
					if (logger.isTraceEnabled()) logger.trace("Sending HTTP_OK response....");
				}
			} catch (DispositionException de) {
				/* Issue with disposition but still send OK at HTTP level to indicate message received */
				HTTPUtil.sendHTTPResponse(out, HttpURLConnection.HTTP_OK, false);
				// If a disposition exception occurs then there must have been an error response in the disposition
				// Error may require manual intervention but keep trying....
				if (!AS2Util.resend(module.getSession().getProcessor(), this, SenderModule.DO_SEND, msg, de
						                      , retries))
		        {
		            // we've run out of retries, do something interesting.
		        	logger.warn("Message abandoned after retry limit reached." + msg.getLoggingText());
		    		return;
		        }
			} catch (OpenAS2Exception oae) {
				// Don't resend or fail, just log an error if one occurs while receiving the MDN
				HTTPUtil.sendHTTPResponse(out, HttpURLConnection.HTTP_BAD_REQUEST, false);
				OpenAS2Exception oae2 = new OpenAS2Exception(
						"Message was sent but an error occured while receiving the MDN");
				oae2.initCause(oae);
				oae2.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
				oae2.terminate();
			}
		} catch (Exception e) {
			logger.error("Unexpected error in Async MDN receiver.", e);
			HTTPUtil.sendHTTPResponse(out, HttpURLConnection.HTTP_BAD_REQUEST, false);
			if (e instanceof IOException) {
				throw (IOException) e;
			}
			WrappedException we = new WrappedException(e);
			we.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
			throw we;
		}
	} 
 
 
	   // Copy headers from an Http connection to an InternetHeaders object
    protected void copyHttpHeaders(HttpURLConnection conn, InternetHeaders headers) {
        Iterator<Map.Entry<String,List<String>>> connHeadersIt = conn.getHeaderFields().entrySet().iterator();
        Iterator<String> connValuesIt;
        Map.Entry<String,List<String>> connHeader;
        String headerName;

        while (connHeadersIt.hasNext()) {
            connHeader = connHeadersIt.next();
            headerName = (String) connHeader.getKey();

            if (headerName != null) {
                connValuesIt = ((Collection) connHeader.getValue()).iterator();

                while (connValuesIt.hasNext()) {
                    String value = connValuesIt.next();

                    if (headers.getHeader(headerName) == null) {
                        headers.setHeader(headerName, value);
                    } else {
                        headers.addHeader(headerName, value);
                    }
                }
            }
        }
    }
    public void reparse(AS2Message msg, HttpURLConnection conn)
    {
    // Create a MessageMDN and copy HTTP headers
    MessageMDN mdn = new AS2MessageMDN(msg);
    copyHttpHeaders(conn, mdn.getHeaders());

    // Receive the MDN data
    ByteArrayOutputStream mdnStream = null;
    try {
    InputStream connIn = conn.getInputStream();
     mdnStream = new ByteArrayOutputStream();

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
        connIn.close();
       
    }
    catch (IOException ioe)
    {
    	logger.error(ioe.getMessage(), ioe);
    }
    finally {
    }

    MimeBodyPart part = null;
	try {
		part = new MimeBodyPart(mdn.getHeaders(), mdnStream.toByteArray());
	} catch (MessagingException e) {
		logger.error("Error creating MIME body part.", e);
	}

    msg.getMDN().setData(part);

    // get the MDN partnership info
    mdn.getPartnership().setSenderID(AS2Partnership.PID_AS2, mdn.getHeader("AS2-From"));
    mdn.getPartnership().setReceiverID(AS2Partnership.PID_AS2, mdn.getHeader("AS2-To"));
    }
    
    
 }