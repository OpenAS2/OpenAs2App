package org.openas2.processor.sender;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.Map;

import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
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
import org.openas2.processor.resender.ResenderModule;
import org.openas2.util.AS2Util;
import org.openas2.util.DateUtil;
import org.openas2.util.DispositionOptions;
import org.openas2.util.IOUtilOld;
import org.openas2.util.Profiler;
import org.openas2.util.ProfilerStub;

public class AS2SenderModule extends HttpSenderModule
{

	private Log logger = LogFactory.getLog(AS2SenderModule.class.getSimpleName());

	public boolean canHandle(String action, Message msg, Map<Object, Object> options)
	{
		if (!action.equals(SenderModule.DO_SEND))
		{
			return false;
		}

		return (msg instanceof AS2Message);
	}

	public void handle(String action, Message msg, Map<Object, Object> options) throws OpenAS2Exception
	{

		if (logger.isInfoEnabled())
			logger.info("message sender invoked" + msg.getLogMsgID());
		boolean isResend = Message.MSG_STATUS_MSG_RESEND.equals(msg.getStatus());

		if (!(msg instanceof AS2Message))
		{
			throw new OpenAS2Exception("Can't send non-AS2 message");
		}

		// verify all required information is present for sending
		checkRequired(msg);
		// Store options on the message object
		if (options != null)
			msg.getOptions().putAll(options);
		if (logger.isTraceEnabled())
			logger.trace("Retry count from options: " + options);
		// Get the resend retry count
		String retries = AS2Util.retries(options, getParameter(SenderModule.SOPT_RETRIES, false));

		// encrypt and/or sign and/or compress the message if needed
		MimeBodyPart securedData;
		try
		{
			securedData = secure(msg);
			storePendingInfo((AS2Message) msg, isResend);
		} catch (Exception e)
		{
			throw new OpenAS2Exception("Error setting up message for sending.", e);
		}
		if (logger.isTraceEnabled())
			try
			{
				String headers = "";
				Enumeration<Header> headersEnum = msg.getData().getAllHeaders();
				while (headersEnum.hasMoreElements())
				{
					Header hd = headersEnum.nextElement();
					headers = ";;" + hd.getName() + "::" + hd.getValue();

				}

				logger.trace("Message object in sender module. Content-Disposition: " + msg.getContentDisposition()
						+ "\n      Content-Type : " + msg.getContentType() + "\n      HEADERS : " + headers
						+ "\n      Content-Disposition in MSG getData() MIMEPART: " + msg.getData().getContentType()
						+ msg.getLogMsgID());
			} catch (Exception e)
			{
			}
		HttpURLConnection conn = null;
		try
		{
			try
			{
				// Create the HTTP connection and set up headers
				String url = msg.getPartnership().getAttribute(AS2Partnership.PA_AS2_URL);
				conn = getConnection(url, true, true, false, "POST");

				sendMessage(conn, msg, securedData, retries);
			} catch (HttpResponseException hre)
			{
				// Will have been logged so just resend
				resend(msg, hre, retries);
				return;
			} catch (Exception e)
			{
				msg.setLogMsg("Unexpected error sending file: " + org.openas2.logging.Log.getExceptionMsg(e));
				logger.error(msg, e);
				resend(msg, new OpenAS2Exception(org.openas2.logging.Log.getExceptionMsg(e)), retries);
				return;
			}
			// Receive an MDN
			if (msg.isConfiguredForMDN())
			{
				msg.setStatus(Message.MSG_STATUS_MDN_WAIT);
				// Check if the AsyncMDN is required
				if (msg.getPartnership().getAttribute(AS2Partnership.PA_AS2_RECEIPT_OPTION) == null)
				{
					// Create a MessageMDN and copy HTTP headers
					MessageMDN mdn = new AS2MessageMDN((AS2Message) msg);
					copyHttpHeaders(conn, mdn.getHeaders());

					// Receive the MDN data
					InputStream connIn = null;
					try
					{
						connIn = conn.getInputStream();
					} catch (IOException e1)
					{
						msg.setLogMsg("Failed to get input stream for receiving MDN: "
								+ org.openas2.logging.Log.getExceptionMsg(e1));
						logger.error(msg, e1);
						resend(msg, new OpenAS2Exception(org.openas2.logging.Log.getExceptionMsg(e1)), retries);
					}
					ByteArrayOutputStream mdnStream = new ByteArrayOutputStream();

					try
					{
						// Retrieve the message content
						if (mdn.getHeader("Content-Length") != null)
						{
							try
							{
								int contentSize = Integer.parseInt(mdn.getHeader("Content-Length"));

								IOUtilOld.copy(connIn, mdnStream, contentSize);
							} catch (NumberFormatException nfe)
							{
								IOUtilOld.copy(connIn, mdnStream);
							}
						} else
						{
							IOUtilOld.copy(connIn, mdnStream);
						}
					} catch (IOException ioe)
					{
						// What to do???
						resend(msg, new OpenAS2Exception(org.openas2.logging.Log.getExceptionMsg(ioe)), retries);
					} finally
					{
						try
						{
							if (connIn != null)
								connIn.close();
						} catch (IOException e)
						{
						}
					}

					msg.setStatus(Message.MSG_STATUS_MDN_PROCESS_INIT);
					try
					{
						AS2Util.processMDN((AS2Message) msg, mdnStream.toByteArray(), null, false, getSession(), this);
					} catch (Exception e)
					{
						if (Message.MSG_STATUS_MDN_PROCESS_INIT.equals(msg.getStatus())
								|| Message.MSG_STATUS_MDN_PARSE.equals(msg.getStatus())
								|| !(e instanceof OpenAS2Exception))
						{
							/*
							 * Cannot identify the target if in init or parse
							 * state so not sure what the best course of action
							 * is apart from do nothing
							 */
							msg.setLogMsg("Unhandled error condition receiving asynchronous MDN. Message and asociated files cleanup will be attempted but may be in an unknown state.");
							logger.error(msg, e);
						}
						/*
						 * Most likely a resend abort of max resend reached if
						 * OpenAS2Exception so do not log as should have been
						 * logged upstream ... just clean up the mess
						 */
						else
						{
							// Must have received MDN successfully
							msg.setLogMsg("Exception receiving asynchronous MDN. Message and asociated files cleanup will be attempted but may be in an unknown state.");
							logger.error(msg, e);

						}
						AS2Util.cleanupFiles(msg, true);
					}
				}
			}

		} finally
		{
			if (conn != null)
				conn.disconnect();
		}
	}

	protected void checkRequired(Message msg) throws InvalidParameterException
	{
		Partnership partnership = msg.getPartnership();

		try
		{
			InvalidParameterException.checkValue(msg, "ContentType", msg.getContentType());
			InvalidParameterException.checkValue(msg, "Attribute: " + AS2Partnership.PA_AS2_URL,
					partnership.getAttribute(AS2Partnership.PA_AS2_URL));
			InvalidParameterException.checkValue(msg, "Receiver: " + AS2Partnership.PID_AS2,
					partnership.getReceiverID(AS2Partnership.PID_AS2));
			InvalidParameterException.checkValue(msg, "Sender: " + AS2Partnership.PID_AS2,
					partnership.getSenderID(AS2Partnership.PID_AS2));
			InvalidParameterException.checkValue(msg, "Subject", msg.getSubject());
			InvalidParameterException.checkValue(msg, "Sender: " + Partnership.PID_EMAIL,
					partnership.getSenderID(Partnership.PID_EMAIL));
			InvalidParameterException.checkValue(msg, "Message Data", msg.getData());
		} catch (InvalidParameterException rpe)
		{
			rpe.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
			throw rpe;
		}
	}

	private void sendMessage(HttpURLConnection conn, Message msg, MimeBodyPart securedData, String retries)
			throws Exception
	{

		updateHttpHeaders(conn, msg, securedData);
		msg.setAttribute(NetAttribute.MA_DESTINATION_IP, conn.getURL().getHost());
		msg.setAttribute(NetAttribute.MA_DESTINATION_PORT, Integer.toString(conn.getURL().getPort()));

		if (logger.isInfoEnabled())
			logger.info("Connecting to: " + conn.getURL() + msg.getLogMsgID());

		// Note: closing this stream causes connection abort errors on some AS2
		// servers
		OutputStream messageOut = conn.getOutputStream();

		// Transfer the data
		InputStream messageIn = securedData.getInputStream();

		try
		{
			ProfilerStub transferStub = Profiler.startProfile();

			int bytes = IOUtilOld.copy(messageIn, messageOut);

			Profiler.endProfile(transferStub);
			if (logger.isInfoEnabled())
				logger.info("transferred " + IOUtilOld.getTransferRate(bytes, transferStub) + msg.getLogMsgID());
		} finally
		{
			messageIn.close();
		}
		// Check the HTTP Response code
		int rc = conn.getResponseCode();
		if ((rc != HttpURLConnection.HTTP_OK) && (rc != HttpURLConnection.HTTP_CREATED)
				&& (rc != HttpURLConnection.HTTP_ACCEPTED) && (rc != HttpURLConnection.HTTP_PARTIAL)
				&& (rc != HttpURLConnection.HTTP_NO_CONTENT))
		{
			msg.setLogMsg("Error sending message. URL: " + conn.getURL().toString() + " ::: Response Code: " + rc
					+ " ::: Response Message: " + conn.getResponseMessage());
			logger.error(msg);
			throw new HttpResponseException(conn.getURL().toString(), rc, conn.getResponseMessage());
		}
	}

	private void resend(Message msg, OpenAS2Exception cause, String tries) throws OpenAS2Exception
	{
		AS2Util.resend(getSession().getProcessor(), this, SenderModule.DO_SEND, msg, cause, tries, false);
	}

	// Returns a MimeBodyPart or MimeMultipart object
	protected MimeBodyPart secure(Message msg) throws Exception
	{
		// Set up encrypt/sign variables
		MimeBodyPart dataBP = msg.getData();
		/*
		 * Based on RFC4130, RFC6362 and RFC5042, the MIC is calculated as
		 * follows: Signed message - MIME header fields and content that is to
		 * be signed which may or may not be encrypted and/or compressed.
		 * 
		 * Unsigned encrypted message - data content including all MIME header
		 * fields and any applied Content-Transfer-Encoding prior to encryption
		 * and/or compression
		 * 
		 * So essentially, calculate the MIC before doing any compression or
		 * encryption if message not being signed otherwise calculate right
		 * before signing of the message but include headers for unsigned
		 * messages (see RFC4130 section 7.3.1 for details)
		 */

		Partnership partnership = msg.getPartnership();

		boolean encrypt = partnership.getAttribute(SecurePartnership.PA_ENCRYPT) != null;
		boolean sign = partnership.getAttribute(SecurePartnership.PA_SIGN) != null;

		if (!sign)
			calcAndStoreMic(msg, dataBP, (sign || encrypt));

		// Check if compression is enabled
		String compressionType = msg.getPartnership().getAttribute("compression_type");
		if (logger.isTraceEnabled())
			logger.trace("Compression type from config: " + compressionType);
		boolean isCompress = false;
		if (compressionType != null)
		{
			if (compressionType.equalsIgnoreCase(ICryptoHelper.COMPRESSION_ZLIB))
			{
				isCompress = true;
			} else
				throw new OpenAS2Exception("Unsupported compression type: " + compressionType);
		}
		String compressionMode = msg.getPartnership().getAttribute("compression_mode");
		boolean isCompressBeforeSign = true; // Defaults to compressing the
												// entire message before signing
												// and encryption
		if (compressionMode != null && compressionMode.equalsIgnoreCase("compress-after-signing"))
			isCompressBeforeSign = false;
		if (isCompress && isCompressBeforeSign)
		{
			if (logger.isTraceEnabled())
				logger.trace("Compressing outbound message before signing...");
			dataBP = AS2Util.getCryptoHelper().compress(msg, dataBP, compressionType);
		}
		// Encrypt and/or sign the data if requested
		CertificateFactory certFx = getSession().getCertificateFactory();

		// Sign the data if requested
		if (sign)
		{
			calcAndStoreMic(msg, dataBP, (sign || encrypt));
			X509Certificate senderCert = certFx.getCertificate(msg, Partnership.PTYPE_SENDER);

			PrivateKey senderKey = certFx.getPrivateKey(msg, senderCert);
			String digest = partnership.getAttribute(SecurePartnership.PA_SIGN);

			if (logger.isDebugEnabled())
				logger.debug("Params for creating signed body part:: DATA: " + dataBP + "\n SIGN DIGEST: " + digest
						+ "\n CERT ALG NAME EXTRACTED: " + senderCert.getSigAlgName()
						+ "\n CERT PUB KEY ALG NAME EXTRACTED: " + senderCert.getPublicKey().getAlgorithm()
						+ msg.getLogMsgID());

			dataBP = AS2Util.getCryptoHelper().sign(dataBP, senderCert, senderKey, digest
					, msg.getPartnership().isNoSetTransferEncodingForSigning(), msg.getPartnership().isRenameDigestToOldName());

			DataHistoryItem historyItem = new DataHistoryItem(dataBP.getContentType());
			// *** add one more item to msg history
			msg.getHistory().getItems().add(historyItem);

			if (logger.isDebugEnabled())
				logger.debug("signed data" + msg.getLogMsgID());
		}

		if (isCompress && !isCompressBeforeSign)
		{
			if (logger.isTraceEnabled())
				logger.trace("Compressing outbound message after signing...");
			dataBP = AS2Util.getCryptoHelper().compress(msg, dataBP, compressionType);
		}
		// Encrypt the data if requested
		if (encrypt)
		{
			String algorithm = partnership.getAttribute(SecurePartnership.PA_ENCRYPT);

			X509Certificate receiverCert = certFx.getCertificate(msg, Partnership.PTYPE_RECEIVER);
			dataBP = AS2Util.getCryptoHelper().encrypt(dataBP, receiverCert, algorithm, msg.getPartnership().isNoSetTransferEncodingForEncryption());

			// Asynch MDN 2007-03-12
			DataHistoryItem historyItem = new DataHistoryItem(dataBP.getContentType());
			// *** add one more item to msg history
			msg.getHistory().getItems().add(historyItem);

			if (logger.isDebugEnabled())
				logger.debug("encrypted data" + msg.getLogMsgID());
		}

		return dataBP;
	}

	protected void updateHttpHeaders(HttpURLConnection conn, Message msg, MimeBodyPart securedData)
	{
		Partnership partnership = msg.getPartnership();

		conn.setRequestProperty("Connection", "close, TE");
		conn.setRequestProperty("User-Agent", Session.TITLE + " (AS2Sender)");

		conn.setRequestProperty("Date", DateUtil.formatDate("EEE, dd MMM yyyy HH:mm:ss Z"));
		conn.setRequestProperty("Message-ID", msg.getMessageID());
		conn.setRequestProperty("Mime-Version", "1.0"); // make sure this is the
														// encoding used in the
														// msg, run TBF1
		try
		{
			conn.setRequestProperty("Content-type", securedData.getContentType());
		} catch (MessagingException e)
		{
			conn.setRequestProperty("Content-type", msg.getContentType());
		}
		conn.setRequestProperty("AS2-Version", "1.1"); // RFC6017 - 1.1 supports
														// compression, 1.2
														// additionally supports
														// EDIINT-Features
		// conn.setRequestProperty("EDIINT-Features",
		// "CEM,multiple-attachments"); // TODO (possibly implement???)
		conn.setRequestProperty("Content-Transfer-Encoding", msg.getHeader("Content-Transfer-Encoding"));
		conn.setRequestProperty("Recipient-Address", partnership.getAttribute(AS2Partnership.PA_AS2_URL));
		conn.setRequestProperty("AS2-To", partnership.getReceiverID(AS2Partnership.PID_AS2));
		conn.setRequestProperty("AS2-From", partnership.getSenderID(AS2Partnership.PID_AS2));
		conn.setRequestProperty("Subject", msg.getSubject());
		conn.setRequestProperty("From", partnership.getSenderID(Partnership.PID_EMAIL));

		String dispTo = partnership.getAttribute(AS2Partnership.PA_AS2_MDN_TO);

		if (dispTo != null)
		{
			conn.setRequestProperty("Disposition-Notification-To", dispTo);
		}

		String dispOptions = partnership.getAttribute(AS2Partnership.PA_AS2_MDN_OPTIONS);

		if (dispOptions != null)
		{
			conn.setRequestProperty("Disposition-Notification-Options", dispOptions);
		}

		String receiptOption = partnership.getAttribute(AS2Partnership.PA_AS2_RECEIPT_OPTION);
		if (receiptOption != null)
		{
			conn.setRequestProperty("Receipt-Delivery-Option", receiptOption);
		}

		String contentDisp;
		try
		{
			contentDisp = securedData.getDisposition();
		} catch (MessagingException e)
		{
			contentDisp = msg.getContentDisposition();
		}
		if (contentDisp != null)
		{
			conn.setRequestProperty("Content-Disposition", contentDisp);
		}

	}

	/**
	 * @description Stores metadata into pending information file and storing
	 *              message object from first send attempt. The message object
	 *              is written to a separate file to avoid repeated rewrites of
	 *              possibly very large objects since it contains the original
	 *              file data
	 * @param msg
	 *            AS2Message
	 * @param mic
	 * @throws WrappedException
	 */
	protected void storePendingInfo(AS2Message msg, boolean isResend) throws Exception
	{
		ObjectOutputStream oos = null;
		File pfo = null;
		File pfi = null;

		try
		{
			String pendingInfoFile = AS2Util.buildPendingFileName(msg, getSession().getProcessor(), "pendingmdninfo");
			String pendingFile = isResend ? msg.getAttribute(FileAttribute.MA_PENDINGFILE) : AS2Util
					.buildPendingFileName(msg, getSession().getProcessor(), "pendingmdn");
			msg.setAttribute(FileAttribute.MA_PENDINGFILE, pendingFile);
			if (!isResend)
			{
				// Write the object to a file to keep a lot of the original
				// static metadata intact for resends
				String pendingMsgObjFile = pendingFile + ".object";
				oos = new ObjectOutputStream(new FileOutputStream(pendingMsgObjFile));
				oos.writeObject(msg);
				oos.flush();
				oos.close();
			}
			msg.setAttribute(FileAttribute.MA_PENDINGINFO, pendingInfoFile);
			oos = new ObjectOutputStream(new FileOutputStream(pendingInfoFile));
			oos.writeObject(msg.getCalculatedMIC());
			String retries = (String) msg.getOption(ResenderModule.OPTION_RETRIES);
			oos.writeObject((retries == null ? "" : retries));

			if (logger.isInfoEnabled())
				logger.info("Save Original mic & message id information into file: " + pendingInfoFile
						+ msg.getLogMsgID());
			oos.writeObject(msg.getAttribute(FileAttribute.MA_FILENAME));
			oos.writeObject(pendingFile);
			oos.writeObject(msg.getAttribute(FileAttribute.MA_ERROR_DIR));
			String sentDir = msg.getAttribute(FileAttribute.MA_SENT_DIR);
			oos.writeObject((sentDir == null ? "" : sentDir));
			if (logger.isTraceEnabled())
				logger.trace("Pending info file written to:" + pendingInfoFile + "\n        Original MIC: "
						+ msg.getCalculatedMIC() + "\n        Retry Count: " + retries
						+ "\n        Original file name : " + msg.getAttribute(FileAttribute.MA_FILENAME)
						+ "\n        Pending message file : " + pendingFile + "\n        Error directory: "
						+ msg.getAttribute(FileAttribute.MA_ERROR_DIR) + "\n        Sent directory: "
						+ msg.getAttribute(FileAttribute.MA_SENT_DIR) + msg.getLogMsgID());

			if (Message.MSG_STATUS_MSG_SEND.equals(msg.getStatus()))
			{
				// this is first attempt to send so move from polling directory
				// to pending directory
				pfi = new File(msg.getAttribute(FileAttribute.MA_FILEPATH));
				pfo = new File(pendingFile);
				IOUtilOld.moveFile(pfi, pfo, true, false);
			}
			msg.setAttribute(FileAttribute.MA_STATUS, FileAttribute.MA_PENDING);

		} catch (Exception e)
		{
			msg.setLogMsg("Error setting up pending information files: " + org.openas2.logging.Log.getExceptionMsg(e));
			logger.error(msg, e);
			throw new Exception("Unable to set up pending information files.");
		} finally
		{
			if (oos != null)
				try
				{
					oos.close();
				} catch (IOException e)
				{
				}
			if (pfi != null)
				pfi = null;
			if (pfo != null)
				pfo = null;
		}
	}

	protected void calcAndStoreMic(Message msg, MimeBodyPart mbp, boolean includeHeaders) throws Exception
	{
		// Calculate and get the original mic
		// includeHeaders = (msg.getHistory().getItems().size() > 1);

		DispositionOptions dispOptions = new DispositionOptions(msg.getPartnership().getAttribute(
				AS2Partnership.PA_AS2_MDN_OPTIONS));
		msg.setCalculatedMIC(AS2Util.getCryptoHelper().calculateMIC(mbp, dispOptions.getMicalg()
				, includeHeaders, msg.getPartnership().isPreventCanonicalization()));
	}

}
