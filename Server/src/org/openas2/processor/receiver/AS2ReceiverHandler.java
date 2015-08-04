package org.openas2.processor.receiver;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

import javax.activation.DataHandler;
import javax.mail.internet.ContentType;
import javax.mail.internet.MimeBodyPart;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.DispositionException;
import org.openas2.OpenAS2Exception;
import org.openas2.WrappedException;
import org.openas2.cert.CertificateFactory;
import org.openas2.lib.helper.ICryptoHelper;
import org.openas2.message.AS2Message;
import org.openas2.message.Message;
import org.openas2.message.MessageMDN;
import org.openas2.message.NetAttribute;
import org.openas2.partner.AS2Partnership;
import org.openas2.partner.ASXPartnership;
import org.openas2.partner.Partnership;
import org.openas2.processor.sender.SenderModule;
import org.openas2.processor.storage.StorageModule;
import org.openas2.util.AS2Util;
import org.openas2.util.ByteArrayDataSource;
import org.openas2.util.DispositionType;
import org.openas2.util.HTTPUtil;
import org.openas2.util.IOUtilOld;
import org.openas2.util.Profiler;
import org.openas2.util.ProfilerStub;

public class AS2ReceiverHandler implements NetModuleHandler {
    private AS2ReceiverModule module;

	private Log logger = LogFactory.getLog(AS2ReceiverHandler.class.getSimpleName());

    
    public AS2ReceiverHandler(AS2ReceiverModule module) {
        super();
        this.module = module;
    }

    public String getClientInfo(Socket s) {
        return " " + s.getInetAddress().getHostAddress() + " " + Integer.toString(s.getPort());
    }

    public AS2ReceiverModule getModule() {
        return module;
    }

    public void handle(NetModule owner, Socket s) {
        logger.info("incoming connection"+getClientInfo(s));

        AS2Message msg = createMessage(s);

        byte[] data = null;

        // Time the transmission
        ProfilerStub transferStub = Profiler.startProfile();

        // Read in the message request, headers, and data
        try {
            data = HTTPUtil.readData(s, msg);
            
            
        } catch (Exception e) {
            NetException ne = new NetException(s.getInetAddress(), s.getPort(), e);
            ne.terminate();
        }

        Profiler.endProfile(transferStub);

        if (data != null) {
        	logger.info("received " + IOUtilOld.getTransferRate(data.length, transferStub)+getClientInfo(s)+msg.getLoggingText());

            // TODO store HTTP request, headers, and data to file in Received folder -> use message-id for filename?
            try {
                // Put received data in a MIME body part
                ContentType receivedContentType = null;

                try {
                    /*
                     * receivedPart = new MimeBodyPart(msg.getHeaders(), data); msg.setData(receivedPart);
                     * receivedContentType = new ContentType(receivedPart.getContentType());
                     */
                    receivedContentType = new ContentType(msg.getHeader("Content-Type"));

                    MimeBodyPart receivedPart = new MimeBodyPart();
                    receivedPart.setDataHandler(new DataHandler(new ByteArrayDataSource(data, receivedContentType
                            .toString(), null)));
                    receivedPart.setHeader("Content-Type", receivedContentType.toString());
                    msg.setData(receivedPart);
                } catch (Exception e) {
                	e.printStackTrace();
                    throw new DispositionException(new DispositionType("automatic-action", "MDN-sent-automatically",
                            "processed", "Error", "unexpected-processing-error"),
                            AS2ReceiverModule.DISP_PARSING_MIME_FAILED, e);
                }

                // Extract AS2 ID's from header, find the message's partnership and update the message
                try {
                    msg.getPartnership().setSenderID(AS2Partnership.PID_AS2, msg.getHeader("AS2-From"));
                    msg.getPartnership().setReceiverID(AS2Partnership.PID_AS2, msg.getHeader("AS2-To"));

                    getModule().getSession().getPartnershipFactory().updatePartnership(msg, false);
                } catch (OpenAS2Exception oae) {
                    throw new DispositionException(new DispositionType("automatic-action", "MDN-sent-automatically",
                            "processed", "Error", "authentication-failed"),
                            AS2ReceiverModule.DISP_PARTNERSHIP_NOT_FOUND, oae);
                }

                // Decrypt and verify signature of the data, and attach data to the message
                decryptAndVerify(msg);

                // Process the received message
                try {
                    getModule().getSession().getProcessor().handle(StorageModule.DO_STORE, msg, null);
                } catch (OpenAS2Exception oae) {
                	oae.printStackTrace();
                    throw new DispositionException(new DispositionType("automatic-action", "MDN-sent-automatically",
                            "processed", "Error", "unexpected-processing-error"),
                            AS2ReceiverModule.DISP_STORAGE_FAILED, oae);
                }

                // Transmit a success MDN if requested
                try {
                    if (msg.isRequestingMDN()) {
                            sendMDN(s, msg, new DispositionType("automatic-action", "MDN-sent-automatically", "processed"),
                                    AS2ReceiverModule.DISP_SUCCESS);
                     } else {
                        BufferedOutputStream out = new BufferedOutputStream(s.getOutputStream());
                        HTTPUtil.sendHTTPResponse(out, HttpURLConnection.HTTP_OK, false);
                        out.flush();
                        out.close();
                        logger.info("sent HTTP OK"+getClientInfo(s)+msg.getLoggingText());
                    }
                } catch (Exception e) {
                	e.printStackTrace();
                    throw new WrappedException("Error creating and returning MDN, message was stilled processed", e);
                }
            } catch (DispositionException de) {
                sendMDN(s, msg, de.getDisposition(), de.getText());
                getModule().handleError(msg, de);
            } catch (OpenAS2Exception oae) {
                getModule().handleError(msg, oae);
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

    protected void decryptAndVerify(Message msg) throws OpenAS2Exception {
        CertificateFactory certFx = getModule().getSession().getCertificateFactory();
        ICryptoHelper ch;

        try {
            ch = AS2Util.getCryptoHelper();
        } catch (Exception e) {
            throw new WrappedException(e);
        }

        try {
            if (ch.isEncrypted(msg.getData())) {
                // Decrypt
            	if (logger.isDebugEnabled()) logger.debug("decrypting :::"+msg.getLoggingText());

                X509Certificate receiverCert = certFx.getCertificate(msg, Partnership.PTYPE_RECEIVER);
                PrivateKey receiverKey = certFx.getPrivateKey(msg, receiverCert);
                msg.setData(AS2Util.getCryptoHelper().decrypt(msg.getData(), receiverCert, receiverKey));
                new ContentType(msg.getData().getContentType());
            }
        } catch (Exception e) {
        	e.printStackTrace();
            throw new DispositionException(new DispositionType("automatic-action", "MDN-sent-automatically",
                    "processed", "Error", "decryption-failed"), AS2ReceiverModule.DISP_DECRYPTION_ERROR, e);
        }

        try {
            if (ch.isSigned(msg.getData())) {
            	if (logger.isDebugEnabled()) logger.debug("verifying signature"+msg.getLoggingText());

                X509Certificate senderCert = certFx.getCertificate(msg, Partnership.PTYPE_SENDER);
                msg.setData(AS2Util.getCryptoHelper().verify(msg.getData(), senderCert));
            }
        } catch (Exception e) {
        	logger.debug("Error decrypting received message.");
        	e.printStackTrace();
            throw new DispositionException(new DispositionType("automatic-action", "MDN-sent-automatically",
                    "processed", "Error", "integrity-check-failed"), AS2ReceiverModule.DISP_VERIFY_SIGNATURE_FAILED, e);
        }
    }

    protected void sendMDN(Socket s, AS2Message msg, DispositionType disposition, String text) {
        boolean mdnBlocked = false;

        mdnBlocked = (msg.getPartnership().getAttribute(ASXPartnership.PA_BLOCK_ERROR_MDN) != null);

        if (!mdnBlocked) {
            try {
            	
            	MessageMDN mdn = AS2Util.createMDN(getModule().getSession(), msg, disposition, text);

                BufferedOutputStream out;
              	out = new BufferedOutputStream(s.getOutputStream());
                //if asyncMDN requested, close connection and initiate separate MDN send 
                if (msg.isRequestingAsynchMDN() ) {
                    HTTPUtil.sendHTTPResponse(out, HttpURLConnection.HTTP_OK, false);
                	out.write("Content-Length: 0\r\n\r\n".getBytes()); 
                	out.flush();
                	out.close();
                	if (logger.isInfoEnabled())
                	  logger.info("setup to send asynch MDN [" + disposition.toString() + "]"+getClientInfo(s)+msg.getLoggingText());
                    getModule().getSession().getProcessor().handle(SenderModule.DO_SENDMDN, msg, null);
                    return;
                }
                
                //  otherwise, send sync MDN back on same connection
                HTTPUtil.sendHTTPResponse(out, HttpURLConnection.HTTP_OK, true);


                // make sure to set the content-length header
                ByteArrayOutputStream data = new ByteArrayOutputStream();
                MimeBodyPart part = mdn.getData();
                IOUtilOld.copy(part.getInputStream(), data);
                mdn.setHeader("Content-Length", Integer.toString(data.size()));


                Enumeration<String> headers = mdn.getHeaders().getAllHeaderLines();
                String header;

                while (headers.hasMoreElements()) {
                    header = (String) headers.nextElement() + "\r\n";
                    out.write(header.getBytes());
                }

                out.write("\r\n".getBytes());
                
 
                data.writeTo(out);
                out.flush();
                out.close();

                // Save sent MDN  for later examination
				getModule().getSession().getProcessor().handle(StorageModule.DO_STOREMDN, msg, null);
				if (logger.isInfoEnabled()) 
					logger.info("sent MDN [" + disposition.toString() + "]"+getClientInfo(s)+msg.getLoggingText());
            } catch (Exception e) {
                WrappedException we = new WrappedException("Error sending MDN", e);
                we.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
                we.terminate();
            }
        }
    }
    

 
}