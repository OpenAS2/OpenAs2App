package org.openas2.processor.receiver;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.activation.DataHandler;
import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.WrappedException;
import org.openas2.lib.util.MimeUtil;
import org.openas2.message.FileAttribute;
import org.openas2.message.InvalidMessageException;
import org.openas2.message.Message;
import org.openas2.params.InvalidParameterException;
import org.openas2.params.MessageParameters;
import org.openas2.params.ParameterParser;
import org.openas2.partner.AS2Partnership;
import org.openas2.partner.Partnership;
import org.openas2.processor.resender.ResenderModule;
import org.openas2.processor.sender.SenderModule;
import org.openas2.util.AS2Util;
import org.openas2.util.ByteArrayDataSource;
import org.openas2.util.IOUtilOld;

public abstract class DirectoryPollingModule extends PollingModule
{
	public static final String PARAM_OUTBOX_DIRECTORY = "outboxdir";
	public static final String PARAM_FILE_EXTENSION_FILTER = "fileextensionfilter";
	public static final String PARAM_ERROR_DIRECTORY = "errordir";
	public static final String PARAM_SENT_DIRECTORY = "sentdir";
	public static final String PARAM_FORMAT = "format";
	public static final String PARAM_DELIMITERS = "delimiters";
	public static final String PARAM_DEFAULTS = "defaults";
	public static final String PARAM_MIMETYPE = "mimetype";
	public static final String PARAM_RESEND_MAX_RETRIES = "resend_max_retries";
	private Map<String, Long> trackedFiles;
	private String outboxDir;
	private String errorDir;
	private String sentDir = null;

	private Log logger = LogFactory.getLog(DirectoryPollingModule.class.getSimpleName());

	public void init(Session session, Map<String, String> options) throws OpenAS2Exception
	{
		super.init(session, options);
		// Check all the directories are configured and actually exist on the file system
		try
		{
			outboxDir = getParameter(PARAM_OUTBOX_DIRECTORY, true);
			IOUtilOld.getDirectoryFile(outboxDir);
			errorDir = getParameter(PARAM_ERROR_DIRECTORY, true);
			IOUtilOld.getDirectoryFile(errorDir);
			sentDir = getParameter(PARAM_SENT_DIRECTORY, false);
			if (sentDir != null)
				IOUtilOld.getDirectoryFile(sentDir);
			String pendingInfoFolder = (String) getSession().getProcessor().getParameters().get("pendingmdninfo");
            IOUtilOld.getDirectoryFile(pendingInfoFolder);
			String pendingFolder = (String) getSession().getProcessor().getParameters().get("pendingmdn");
            IOUtilOld.getDirectoryFile(pendingFolder);

		} catch (IOException e)
		{
			throw new OpenAS2Exception("Failed to initialise directory poller.", e);
		}
	}

	public void poll()
	{
		try
		{
			// scan the directory for new files
			scanDirectory(outboxDir);

			// update tracking info. if a file is ready, process it
			updateTracking();
		} catch (OpenAS2Exception oae)
		{
			oae.terminate();
			forceStop(oae);
		} catch (Exception e)
		{
			new WrappedException(e).terminate();
			forceStop(e);
		}
	}

	protected void scanDirectory(String directory) throws IOException, InvalidParameterException
	{
		File dir = IOUtilOld.getDirectoryFile(directory);
		String extensionFilter = getParameter(PARAM_FILE_EXTENSION_FILTER, "");

		// get a list of entries in the directory
		File[] files = extensionFilter.length() > 0 ? IOUtilOld.getFiles(dir, extensionFilter) : dir.listFiles();
		if (files == null)
		{
			throw new InvalidParameterException("Error getting list of files in directory", this,
					PARAM_OUTBOX_DIRECTORY, dir.getAbsolutePath());
		}

		// iterator through each entry, and start tracking new files
		if (files.length > 0)
		{
			for (int i = 0; i < files.length; i++)
			{
				File currentFile = files[i];

				if (checkFile(currentFile))
				{
					// start watching the file's size if it's not already being
					// watched
					trackFile(currentFile);
				}
			}
		}
	}

	protected boolean checkFile(File file)
	{
		if (file.exists() && file.isFile())
		{
			try
			{
				// check for a write-lock on file, will skip file if it's write
				// locked
				FileOutputStream fOut = new FileOutputStream(file, true);
				fOut.close();
				return true;
			} catch (IOException ioe)
			{
				// a sharing violation occurred, ignore the file for now
			}
		}
		return false;
	}

	protected void trackFile(File file)
	{
		Map<String, Long> trackedFiles = getTrackedFiles();
		String filePath = file.getAbsolutePath();
		if (trackedFiles.get(filePath) == null)
		{
			trackedFiles.put(filePath, new Long(file.length()));
		}
	}

	protected void updateTracking() throws OpenAS2Exception
	{
		// clone the trackedFiles map, iterator through the clone and modify the
		// original to avoid iterator exceptions
		// is there a better way to do this?
		Map<String, Long> trackedFiles = getTrackedFiles();
		Map<String, Long> trackedFilesClone = new HashMap<String, Long>(trackedFiles);

		for (Iterator<Map.Entry<String, Long>> it = trackedFilesClone.entrySet().iterator(); it.hasNext();)
		{
			// get the file and it's stored length
			Map.Entry<String, Long> fileEntry = it.next();
			File file = new File((String) fileEntry.getKey());
			long fileLength = ((Long) fileEntry.getValue()).longValue();

			// if the file no longer exists, remove it from the tracker
			if (!checkFile(file))
			{
				trackedFiles.remove(fileEntry.getKey());
			} else
			{
				// if the file length has changed, update the tracker
				long newLength = file.length();
				if (newLength != fileLength)
				{
					trackedFiles.put((String) fileEntry.getKey(), new Long(newLength));
				} else
				{
					// if the file length has stayed the same, process the file
					// and stop tracking it
					try
					{
						processFile(file);
					} finally
					{
						trackedFiles.remove(fileEntry.getKey());
					}
				}
			}
		}
	}

	protected void processFile(File file) throws OpenAS2Exception
	{

		if (logger.isInfoEnabled())
			logger.info("processing " + file.getAbsolutePath());

		Message msg = createMessage();
		msg.setAttribute(FileAttribute.MA_FILEPATH, file.getAbsolutePath());
		msg.setAttribute(FileAttribute.MA_FILENAME, file.getName());
		msg.setAttribute(FileAttribute.MA_ERROR_DIR, getParameter(PARAM_ERROR_DIRECTORY, true));
		if (getParameter(PARAM_SENT_DIRECTORY, false) != null)
			msg.setAttribute(FileAttribute.MA_SENT_DIR, getParameter(PARAM_SENT_DIRECTORY, false));

		/*
		 * save the source file name into message object, it will be stored into
		 * pending information file for async MDN
		 */
		msg.setAttribute(FileAttribute.MA_PENDINGFILE, file.getName());

		updateMessage(msg, file);
		if (logger.isInfoEnabled())
			logger.info("file assigned to message " + file.getAbsolutePath() + msg.getLogMsgID());

		if (msg.getData() == null)
		{
			throw new InvalidMessageException("No Data");
		}
		if (logger.isTraceEnabled())
			logger.trace("PARTNERSHIP parms: " + msg.getPartnership().getAttributes() + msg.getLogMsgID());
		// Retry count - first try on partnership then directory polling module
		String maxRetryCnt = msg.getPartnership().getAttribute(AS2Partnership.PA_RESEND_MAX_RETRIES);
		if (maxRetryCnt == null || maxRetryCnt.length() < 1)
		{
			maxRetryCnt = getSession().getProcessor().getParameters().get(PARAM_RESEND_MAX_RETRIES);
		}
		if (logger.isTraceEnabled())
			logger.trace("RESEND COUNT extracted from config: " + maxRetryCnt + msg.getLogMsgID());
		Map<Object, Object> options = msg.getOptions();
		options.put(ResenderModule.OPTION_RETRIES, maxRetryCnt);

        if (logger.isTraceEnabled())
			try
			{
				String headers = "";
	        	Enumeration<Header> headersEnum = msg.getData().getAllHeaders();
	        	while (headersEnum.hasMoreElements())
				{
					Header hd = headersEnum.nextElement();
					headers  = ";;" + hd.getName() + "::" + hd.getValue();
					
				}

				logger.trace("Message object in directory polling module. Content-Disposition: " + msg.getContentDisposition()
				    + "\n      Content-Type : " + msg.getContentType()
				    + "\n      HEADERS : " + headers
				    + "\n      Content-Disposition in MSG getData() MIMEPART: "
				    + msg.getData().getContentType()
					+msg.getLogMsgID()	);
			} catch (Exception e){}
		try
		{
			msg.setStatus(Message.MSG_STATUS_MSG_SEND);
			// Transmit the message
			getSession().getProcessor().handle(SenderModule.DO_SEND, msg, options);
		} catch (Exception e)
		{
			msg.setLogMsg("Fatal error sending message: " + org.openas2.logging.Log.getExceptionMsg(e));
			logger.error(msg, e);
			AS2Util.cleanupFiles(msg, true);
		}
	}

	protected abstract Message createMessage();

	public void updateMessage(Message msg, File file) throws OpenAS2Exception
	{
		MessageParameters params = new MessageParameters(msg);

		// Get the parameter that should provide the link between the polled directory and an AS2 sender and recipient
		String defaults = getParameter(PARAM_DEFAULTS, false);
		// Link the file to an AS2 sender and recipient via the Message object associated with the file
		if (defaults != null)
		{
			params.setParameters(defaults);
		}

		String filename = file.getName();
		String format = getParameter(PARAM_FORMAT, false);

		if (format != null)
		{
			String delimiters = getParameter(PARAM_DELIMITERS, ".-");
			params.setParameters(format, delimiters, filename);
		}

		try
		{
			byte[] data = IOUtilOld.getFileBytes(file);
			String contentType = getParameter(PARAM_MIMETYPE, false);
			if (contentType == null)
			{
				contentType = "application/octet-stream";
			} else
			{
				try
				{
					contentType = ParameterParser.parse(contentType, params);
				} catch (InvalidParameterException e)
				{
					msg.setLogMsg("Bad content-type" + contentType);
					logger.error(msg, e);
					contentType = "application/octet-stream";
				}
			}
			ByteArrayDataSource byteSource = new ByteArrayDataSource(data, contentType, null);
			MimeBodyPart body = new MimeBodyPart();
			body.setDataHandler(new DataHandler(byteSource));


			// below statement is not filename related, just want to make it
			// consist with the parameter "mimetype="application/EDI-X12""
			// defined in config.xml 2007-06-01

			body.setHeader("Content-Type", contentType);

			// add below statement will tell the receiver to save the filename
			// as the one sent by sender. 2007-06-01
			String sendFileName = getParameter("sendfilename", false);
			if (sendFileName != null && sendFileName.equals("true"))
			{
				String contentDisposition = "Attachment; filename=\"" + msg.getAttribute(FileAttribute.MA_FILENAME) + "\"";
				body.setHeader("Content-Disposition", contentDisposition);
				msg.setContentDisposition(contentDisposition);
			}

			msg.setData(body);
		} catch (MessagingException me)
		{
			throw new WrappedException(me);
		} catch (IOException ioe)
		{
			throw new WrappedException(ioe);
		}

		// update the message's partnership with any stored information
		getSession().getPartnershipFactory().updatePartnership(msg, true);
		msg.updateMessageID();
		/* Not sure it should be set at this level as there is no encoding of the content at this point so make it configurable */
		if (msg.getPartnership().isSetTransferEncodingOnInitialBodyPart())
		{
			String contentTxfrEncoding = msg.getPartnership().getAttribute(Partnership.PA_CONTENT_TRANSFER_ENCODING);
			if (contentTxfrEncoding == null)
				contentTxfrEncoding = Session.DEFAULT_CONTENT_TRANSFER_ENCODING;
			try
			{
				msg.getData().setHeader("Content-Transfer-Encoding", contentTxfrEncoding);
			} catch (MessagingException e)
			{
				logger.error("Failed to set content transfer encoding in created MimeBodyPart: "
						+ org.openas2.logging.Log.getExceptionMsg(e), e);
			}
		}
		if (logger.isTraceEnabled())
			try
			{
				logger.trace("MimeBodyPart built in polling module:::: " + MimeUtil.toString(msg.getData(), true) + msg.getLogMsgID());
			} catch (Exception e)
			{
				e.printStackTrace();
			}
	}

	public Map<String, Long> getTrackedFiles()
	{
		if (trackedFiles == null)
		{
			trackedFiles = new HashMap<String, Long>();
		}
		return trackedFiles;
	}
}