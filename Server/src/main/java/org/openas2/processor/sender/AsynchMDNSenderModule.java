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

		if (logger.isDebugEnabled()) logger.debug("ASYNC MDN send started...");
		if (options == null) options = new HashMap<Object, Object>();
		options.put("DIRECTION", "RECEIVE");
		sendAsyncMDN((AS2Message) msg, options);
	}

	protected void updateHttpHeaders(HttpURLConnection conn, Message msg) {

		MessageMDN mdn = msg.getMDN();
		conn.setRequestProperty("Connection", "close, TE");
		conn.setRequestProperty("User-Agent", msg.getAppTitle() + " (AsynchMDNSender)");

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
		conn.setRequestProperty("AS2-To", mdn.getHeader("AS2-To"));
		conn.setRequestProperty("AS2-From", mdn.getHeader("AS2-From"));
		conn.setRequestProperty("Subject", msg.getHeader("Subject"));
		conn.setRequestProperty("From", mdn.getHeader("From"));

	}

	private void sendAsyncMDN(AS2Message msg, Map<Object, Object> options)
			throws OpenAS2Exception {

		DispositionType disposition = new DispositionType("automatic-action",
				"MDN-sent-automatically", "processed");
		String url = msg.getAsyncMDNurl();

		try {

			MessageMDN mdn = msg.getMDN();

			// Create a HTTP connection
			if (logger.isDebugEnabled()) logger.debug("ASYNC MDN attempting connection to: " + url + msg.getLogMsgID());
			HttpURLConnection conn = getConnection(url, true, true, false,
					"POST");

			try {

				if (logger.isInfoEnabled()) logger.info("connected to " + url + msg.getLogMsgID());

				conn.setRequestProperty("Connection", "close, TE");
				conn.setRequestProperty("User-Agent", msg.getAppTitle() + " (AsyncMDNSenderModule)");
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
					if (logger.isTraceEnabled())
						logger.trace("Set HTTP response request property: " + header.getName() + " -> " + headerValue + msg.getLogMsgID());
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
					if (logger.isInfoEnabled()) logger.info("transferred "
							+ IOUtilOld.getTransferRate(bytes, transferStub)
							+ msg.getLogMsgID());
				} finally {
					messageIn.close();
				}

				int respCode = conn.getResponseCode();
				// Check the HTTP Response code
				if ((respCode != HttpURLConnection.HTTP_OK)
						&& (respCode != HttpURLConnection.HTTP_CREATED)
						&& (respCode != HttpURLConnection.HTTP_ACCEPTED)
						&& (respCode != HttpURLConnection.HTTP_PARTIAL)
						&& (respCode != HttpURLConnection.HTTP_NO_CONTENT)) {
					if (logger.isErrorEnabled())
					{
						msg.setLogMsg("Error sending AsyncMDN [" + disposition.toString()
							+ "] HTTP response code: " + respCode);
						logger.error(msg);
					}
					throw new HttpResponseException(url.toString(),
							respCode, conn.getResponseMessage());
				}

				if (logger.isInfoEnabled()) logger.info("sent AsyncMDN [" + disposition.toString()
						+ "] OK " + msg.getLogMsgID());

				// log & store mdn into backup folder.
				getSession().getProcessor().handle(StorageModule.DO_STOREMDN, msg, null);
				// Log significant msg state
				msg.setOption("STATE", Message.MSG_STATE_MSG_RXD_MDN_SENT_OK);
				msg.trackMsgState(getSession());

			} finally {
				conn.disconnect();
			}
		} catch (HttpResponseException hre)
		{
			// Resend if the HTTP Response has an error code
			logger.warn("HTTP exception sending ASYNC MDN: " + org.openas2.logging.Log.getExceptionMsg(hre) + msg.getLogMsgID(), hre);
			hre.terminate();
			resend(msg, hre);
			// Log significant msg state
			msg.setOption("STATE", Message.MSG_STATE_MDN_SENDING_EXCEPTION);
			msg.trackMsgState(getSession());
		} catch (IOException ioe)
		{
			logger.warn("IO exception sending ASYNC MDN: " + org.openas2.logging.Log.getExceptionMsg(ioe) + msg.getLogMsgID(), ioe);
			// Resend if a network error occurs during transmission
			WrappedException wioe = new WrappedException(ioe);
			wioe.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
			wioe.terminate();

			resend(msg, wioe);
			// Log significant msg state
			msg.setOption("STATE", Message.MSG_STATE_MDN_SENDING_EXCEPTION);
			msg.trackMsgState(getSession());
		} catch (Exception e) {
			logger.warn("Unexpected exception sending ASYNC MDN: " + org.openas2.logging.Log.getExceptionMsg(e) + msg.getLogMsgID(), e);
			// Propagate error if it can't be handled by a resend
			// log & store mdn into backup folder.
			getSession().getProcessor().handle(StorageModule.DO_STOREMDN, msg, null);
			// Log significant msg state
			msg.setOption("STATE", Message.MSG_STATE_MDN_SENDING_EXCEPTION);
			msg.trackMsgState(getSession());
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
