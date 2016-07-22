package org.openas2.message;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.ParseException;

import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.params.InvalidParameterException;
import org.openas2.partner.Partnership;


public interface Message extends Serializable {
	
    public static final String MSG_STATUS_MSG_INIT = "initializing_msg";
    public static final String MSG_STATUS_MSG_SEND = "sending_msg";
    public static final String MSG_STATUS_MSG_RESEND = "resending_msg";
    public static final String MSG_STATUS_MDN_SEND = "sending_mdn";
    public static final String MSG_STATUS_MDN_WAIT = "awaiting_mdn";
    public static final String MSG_STATUS_MDN_PARSE = "parsing_mdn";
    public static final String MSG_STATUS_MDN_VERIFY = "verifying_mdn";
    public static final String MSG_STATUS_MDN_PROCESS_INIT = "init_processing_mdn";
    public static final String MSG_STATUS_MSG_CLEANUP = "cleanup";
    
    public static final String MSG_STATE_SEND_START = "msg_send_start";
    public static final String MSG_STATE_SEND_EXCEPTION = "msg_send_exception";
    public static final String MSG_STATE_SEND_FAIL = "msg_send_fail";
    public static final String MSG_STATE_RECEIVE_START = "msg_receive_start";
    public static final String MSG_STATE_RECEIVE_EXCEPTION = "msg_receive_exception";
    public static final String MSG_STATE_RECEIVE_FAIL = "msg_receive_fail";
    public static final String MSG_STATE_MDN_ERROR_RESPONSE_START = "msg_receive_error_sending_mdn_error";
    public static final String MSG_STATE_MDN_SENDING_EXCEPTION = "mdn_sending_exception";
    public static final String MSG_STATE_MDN_RECEIVING_EXCEPTION = "mdn_receiving_exception";
    public static final String MSG_STATE_MDN_SEND_START = "mdn_send_start";
    public static final String MSG_STATE_MDN_RECEIVE_START = "mdn_receive_start";
    public static final String MSG_STATE_MSG_SENT_MDN_RECEIVED_ERROR = "msg_sent_mdn_received_error";
    public static final String MSG_STATE_MSG_SENT_MDN_RECEIVED_OK = "msg_sent_mdn_received_ok";
    public static final String MSG_STATE_MSG_RXD_MDN_SENDING_FAIL = "msg_rxd_mdn_sending_fail";
    public static final String MSG_STATE_MSG_RXD_MDN_SENT_OK = "msg_rxd_mdn_sent_ok";
    public static final String MSG_STATE_MIC_MISMATCH = "msg_sent_mdn_received_mic_mismatch";
    
	public static Map<String, String> STATE_MSGS = new HashMap<String, String>()
	{
		private static final long serialVersionUID = 5L;

		{
			put(MSG_STATE_SEND_START, "Message sending started");
			put(MSG_STATE_SEND_EXCEPTION, "Message sending exception occurred. Resend queued");
			put(MSG_STATE_SEND_FAIL, "Message sending failed.");
			put(MSG_STATE_RECEIVE_START, "Message receiving started");
			put(MSG_STATE_RECEIVE_EXCEPTION, "Processing exception occurred receiving message. Resend queued");
			put(MSG_STATE_RECEIVE_FAIL, "Failed to receive inbound message successfully.");
			put(MSG_STATE_MDN_ERROR_RESPONSE_START,
					"Error processing received message. Sending MDN error response to partner");
			put(MSG_STATE_MDN_SENDING_EXCEPTION, "Processing exception sending MDN. Resend queued");
			put(MSG_STATE_MDN_RECEIVING_EXCEPTION, "Processing exception receiving MDN. Resend queued");
			put(MSG_STATE_MDN_SEND_START, "Message recieved. MDN sending started");
			put(MSG_STATE_MDN_RECEIVE_START, "Message sent. MDN receiving started");
			put(MSG_STATE_MSG_SENT_MDN_RECEIVED_ERROR,
					"Message sent. Message MDN received indicates an error. Resend queued");
			put(MSG_STATE_MSG_SENT_MDN_RECEIVED_OK, "Message sent. Message MDN success response received.");
			put(MSG_STATE_MSG_RXD_MDN_SENDING_FAIL,
					"Message was received but failed to successfully send an MDN response to partner");
			put(MSG_STATE_MSG_RXD_MDN_SENT_OK, "Message received and MDN sent succesfully.");
		}
	};

    public static final String SMIME_TYPE_COMPRESSED_DATA = "smime-type=compressed-data";
    
    public String getPayloadFilename();
    public void setPayloadFilename(String filename);
    public String extractPayloadFilename() throws ParseException;
	
	public void setStatus(String status);

    public String getStatus();
    
	public Map<String, String> getCustomOuterMimeHeaders();

	public void setCustomOuterMimeHeaders(Map<String, String> customOuterMimeHeaders);

	public void addCustomOuterMimeHeader(String key, String value);

	public Map<Object, Object> getOptions();

	public void setOption(Object key, Object value);

    public Object getOption(Object key);
    
    public void setAttribute(String key, String value);

    public String getAttribute(String key);

    public void setAttributes(Map<String, String> attributes);

    public Map<String, String> getAttributes();

    public void setContentType(String contentType);

    public String getContentType();

    public String getCompressionType();

    public void setCompressionType(String compressionType);
    
    public void setContentDisposition(String contentDisposition);

    public String getContentDisposition();

    public void setData(MimeBodyPart data, DataHistoryItem historyItem) throws OpenAS2Exception;
    
	public DataHistoryItem setData(MimeBodyPart data) throws OpenAS2Exception;

    public MimeBodyPart getData();

    public void setHeader(String key, String value);

    public String getHeader(String key);

    public String getHeader(String key, String delimiter);

    public void setHeaders(InternetHeaders headers);

    public InternetHeaders getHeaders();

    public void setHistory(DataHistory history);

    public DataHistory getHistory();

    public void setMDN(MessageMDN mdn);

    public MessageMDN getMDN();

    public void setMessageID(String messageID);

    public String getMessageID();

    public void setPartnership(Partnership partnership);

    public Partnership getPartnership();

    public String getProtocol();

    public boolean isRequestingMDN();

    public boolean isConfiguredForMDN();

    public boolean isRequestingAsynchMDN();

    public boolean isConfiguredForAsynchMDN();

    public void setSubject(String subject);

    public String getSubject();

    public void addHeader(String key, String value);

    public String generateMessageID() throws InvalidParameterException;

    public void updateMessageID() throws InvalidParameterException;
    
    public String getLogMsgID();

    public String getLogMsg();

    public void setLogMsg(String msg);
    
    public void trackMsgState(Session session);

    public String getCalculatedMIC();

	public void setCalculatedMIC(String calculatedMIC);

}
