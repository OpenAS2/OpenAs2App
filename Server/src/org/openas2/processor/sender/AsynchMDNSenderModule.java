package org.openas2.processor.sender;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import javax.mail.Header;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.WrappedException;
import org.openas2.message.AS2Message;
import org.openas2.message.Message;
import org.openas2.message.MessageMDN;
import org.openas2.processor.resender.ResenderModule;
import org.openas2.processor.storage.StorageModule;
import org.openas2.util.DateUtil;
import org.openas2.util.DispositionType;
import org.openas2.util.IOUtilOld;
import org.openas2.util.Profiler;
import org.openas2.util.ProfilerStub;

public class AsynchMDNSenderModule extends HttpSenderModule {

	private Log logger = LogFactory.getLog(AsynchMDNSenderModule.class.getSimpleName());

	public boolean canHandle(String action, Message msg,
			Map<Object, Object> options) {
		if (!action.equals(SenderModule.DO_SENDMDN)) {
			return false;
		}

		return (msg instanceof AS2Message);
	}

	public void handle(String action, Message msg, Map<Object, Object> options)
			throws OpenAS2Exception {

		try {
			sendAsyncMDN((AS2Message) msg, options);

		} finally {
			logger.debug("asynch mdn message sent");
		}

	}

	protected void updateHttpHeaders(HttpURLConnection conn, Message msg) {

		conn.setRequestProperty("Connection", "close, TE");
		conn.setRequestProperty("User-Agent", "OpenAS2 AsynchMDNSender");

		conn.setRequestProperty("Date",
				DateUtil.formatDate("EEE, dd MMM yyyy HH:mm:ss Z"));
		conn.setRequestProperty("Message-ID", msg.getMessageID());
		conn.setRequestProperty("Mime-Version", "1.0"); // make sure this is the
														// encoding used in the
														// msg, run TBF1
		conn.setRequestProperty("Content-type", msg.getHeader("Content-type"));
		conn.setRequestProperty("AS2-Version", "1.1");
		conn.setRequestProperty("Recipient-Address",
				msg.getHeader("Recipient-Address"));
		conn.setRequestProperty("AS2-To", msg.getHeader("AS2-To"));
		conn.setRequestProperty("AS2-From", msg.getHeader("AS2-From"));
		conn.setRequestProperty("Subject", msg.getHeader("Subject"));
		conn.setRequestProperty("From", msg.getHeader("From"));

	}

	private void sendAsyncMDN(AS2Message msg, Map<Object, Object> options)
			throws OpenAS2Exception {

		logger.info("Async MDN submitted" + msg.getLoggingText());
		DispositionType disposition = new DispositionType("automatic-action",
				"MDN-sent-automatically", "processed");

		try {

			MessageMDN mdn = msg.getMDN();

			// Create a HTTP connection
			String url = msg.getAsyncMDNurl();
			HttpURLConnection conn = getConnection(url, true, true, false,
					"POST");

			try {

				logger.info("connected to " + url + msg.getLoggingText());

				conn.setRequestProperty("Connection", "close, TE");
				conn.setRequestProperty("User-Agent", "OpenAS2 AS2Sender");
				// Copy all the header from mdn to the RequestProperties of conn
				Enumeration<Header> headers = mdn.getHeaders().getAllHeaders();
				Header header = null;
				while (headers.hasMoreElements()) {
					header = headers.nextElement();
					String headerValue = header.getValue();
					headerValue.replace('\t', ' ');
					headerValue.replace('\n', ' ');
					headerValue.replace('\r', ' ');
					conn.setRequestProperty(header.getName(), headerValue);
				}

				// Note: closing this stream causes connection abort errors on
				// some AS2 servers
				OutputStream messageOut = conn.getOutputStream();

				// Transfer the data
				InputStream messageIn = mdn.getData().getInputStream();
				try {
					ProfilerStub transferStub = Profiler.startProfile();

					int bytes = IOUtilOld.copy(messageIn, messageOut);
					Profiler.endProfile(transferStub);
					logger.info("transferred "
							+ IOUtilOld.getTransferRate(bytes, transferStub)
							+ msg.getLoggingText());
				} finally {
					messageIn.close();
				}

				// Check the HTTP Response code
				if ((conn.getResponseCode() != HttpURLConnection.HTTP_OK)
						&& (conn.getResponseCode() != HttpURLConnection.HTTP_CREATED)
						&& (conn.getResponseCode() != HttpURLConnection.HTTP_ACCEPTED)
						&& (conn.getResponseCode() != HttpURLConnection.HTTP_PARTIAL)
						&& (conn.getResponseCode() != HttpURLConnection.HTTP_NO_CONTENT)) {
					logger.error("sent AsyncMDN [" + disposition.toString()
							+ "] Fail " + msg.getLoggingText());
					throw new HttpResponseException(url.toString(),
							conn.getResponseCode(), conn.getResponseMessage());
				}

				logger.info("sent AsyncMDN [" + disposition.toString()
						+ "] OK " + msg.getLoggingText());

				// log & store mdn into backup folder.
				getSession().getProcessor().handle(StorageModule.DO_STOREMDN, msg, null);

			} finally {
				conn.disconnect();
			}
		} catch (HttpResponseException hre) { // Resend if the HTTP Response has
												// an error code
			hre.terminate();
			resend(msg, hre);
		} catch (IOException ioe) { // Resend if a network error occurs during
									// transmission

			WrappedException wioe = new WrappedException(ioe);
			wioe.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
			wioe.terminate();

			resend(msg, wioe);
		} catch (Exception e) { // Propagate error if it can't be handled by a
								// resend
			throw new WrappedException(e);
		}
	}

	protected void resend(Message msg, OpenAS2Exception cause)
			throws OpenAS2Exception {
		Map<Object, Object> options = new HashMap<Object, Object>();
		options.put(ResenderModule.OPTION_CAUSE, cause);
		options.put(ResenderModule.OPTION_INITIAL_SENDER, this);
		getSession().getProcessor().handle(ResenderModule.DO_RESEND, msg, options);
	}

}