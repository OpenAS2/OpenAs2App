package org.openas2.message;

import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.params.InvalidParameterException;
import org.openas2.partner.Partnership;

import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.ParseException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public interface Message extends Serializable {

    String MSG_STATUS_MSG_INIT = "initializing_msg";
    String MSG_STATUS_MSG_SEND = "sending_msg";
    String MSG_STATUS_MSG_RESEND = "resending_msg";
    String MSG_STATUS_MDN_SEND = "sending_mdn";
    String MSG_STATUS_MDN_WAIT = "awaiting_mdn";
    String MSG_STATUS_MDN_PARSE = "parsing_mdn";
    String MSG_STATUS_MDN_VERIFY = "verifying_mdn";
    String MSG_STATUS_MDN_PROCESS_INIT = "init_processing_mdn";
    String MSG_STATUS_MSG_CLEANUP = "cleanup";
    String MSG_STATUS_MSG_TERMINATED_IN_ERROR = "terminated_in_error";

    String MSG_STATE_SEND_START = "msg_send_start";
    String MSG_STATE_SEND_EXCEPTION = "msg_send_exception";
    String MSG_STATE_SEND_FAIL = "msg_send_fail";
    String MSG_STATE_SEND_FAIL_RESEND_QUEUED = "msg_send_fail_resend_queued";
    String MSG_STATE_RECEIVE_START = "msg_receive_start";
    String MSG_STATE_RECEIVE_EXCEPTION = "msg_receive_exception";
    String MSG_STATE_RECEIVE_FAIL = "msg_receive_fail";
    String MSG_STATE_MDN_ERROR_RESPONSE_START = "msg_receive_error_sending_mdn_error";
    String MSG_STATE_MDN_SENDING_EXCEPTION = "mdn_sending_exception";
    String MSG_STATE_MDN_RECEIVING_EXCEPTION = "mdn_receiving_exception";
    String MSG_STATE_MDN_SEND_START = "mdn_send_start";
    String MSG_STATE_MDN_RECEIVE_START = "mdn_receive_start";
    String MSG_STATE_MDN_ASYNC_RECEIVE_FAIL = "mdn_asyn_receive_fail";
    String MSG_STATE_MSG_SENT_MDN_RECEIVED_ERROR = "msg_sent_mdn_received_error";
    String MSG_STATE_MSG_SENT_MDN_RECEIVED_OK = "msg_sent_mdn_received_ok";
    String MSG_STATE_MSG_RXD_MDN_SENDING_FAIL = "msg_rxd_mdn_sending_fail";
    String MSG_STATE_MSG_RXD_MDN_SENT_OK = "msg_rxd_mdn_sent_ok";
    String MSG_STATE_MSG_RXD_MDN_NOT_REQUESTED = "msg_rxd_mdn_not_requested_ok";
    String MSG_STATE_MIC_MISMATCH = "msg_sent_mdn_received_mic_mismatch";

    Map<String, String> STATE_MSGS = new HashMap<String, String>() {
        private static final long serialVersionUID = 5L;

        {
            put(MSG_STATE_SEND_START, "Message sending started");
            put(MSG_STATE_SEND_EXCEPTION, "Message sending exception occurred. Resend queued");
            put(MSG_STATE_SEND_FAIL, "Message sending failed.");
            put(MSG_STATE_SEND_FAIL_RESEND_QUEUED, "Message failed to send and resend will be attempted using a different message ID");
            put(MSG_STATE_RECEIVE_START, "Message receiving started");
            put(MSG_STATE_RECEIVE_EXCEPTION, "Processing exception occurred receiving message. Disposition exception thrown.");
            put(MSG_STATE_RECEIVE_FAIL, "Failed to receive inbound message successfully.");
            put(MSG_STATE_MDN_ERROR_RESPONSE_START, "Error processing received message. Sending MDN error response to partner");
            put(MSG_STATE_MDN_SENDING_EXCEPTION, "Processing exception sending MDN. Resend queued");
            put(MSG_STATE_MDN_RECEIVING_EXCEPTION, "Processing exception receiving MDN. Resend queued");
            put(MSG_STATE_MDN_ASYNC_RECEIVE_FAIL, "Detected sent message with no Async MDN received. Sent file was cleaned up but no guarantee the partner received the file.");
            put(MSG_STATE_MDN_SEND_START, "Message received. MDN sending started");
            put(MSG_STATE_MDN_RECEIVE_START, "Message sent. MDN receiving started");
            put(MSG_STATE_MSG_SENT_MDN_RECEIVED_ERROR, "Message sent. Message MDN received indicates an error. Resend queued");
            put(MSG_STATE_MSG_SENT_MDN_RECEIVED_OK, "Message sent. Message MDN success response received.");
            put(MSG_STATE_MSG_RXD_MDN_SENDING_FAIL, "Message was received but failed to successfully send an MDN response to partner");
            put(MSG_STATE_MSG_RXD_MDN_NOT_REQUESTED, "Message received successfully but no MDN requested.");
        }
    };

    String SMIME_TYPE_COMPRESSED_DATA = "smime-type=compressed-data";

    String getPayloadFilename();

    void setPayloadFilename(String filename);

    String extractPayloadFilename() throws ParseException;

    String getStatus();

    void setStatus(String status);

    Map<String, String> getCustomOuterMimeHeaders();

    void setCustomOuterMimeHeaders(Map<String, String> customOuterMimeHeaders);

    void addCustomOuterMimeHeader(String key, String value);

    Map<Object, Object> getOptions();

    void setOption(Object key, Object value);

    Object getOption(Object key);

    void setAttribute(String key, String value);

    String getAttribute(String key);

    Map<String, String> getAttributes();

    void setAttributes(Map<String, String> attributes);

    String getContentType();

    void setContentType(String contentType);

    String getCompressionType();

    void setCompressionType(String compressionType);

    String getContentDisposition();

    void setContentDisposition(String contentDisposition);

    void setData(MimeBodyPart data, DataHistoryItem historyItem) throws OpenAS2Exception;

    DataHistoryItem setData(MimeBodyPart data) throws OpenAS2Exception;

    MimeBodyPart getData();

    void setHeader(String key, String value);

    String getHeader(String key);

    String getHeader(String key, String delimiter);

    InternetHeaders getHeaders();

    void setHeaders(InternetHeaders headers);

    DataHistory getHistory();

    void setHistory(DataHistory history);

    MessageMDN getMDN();

    void setMDN(MessageMDN mdn);

    String getMessageID();

    void setMessageID(String messageID);

    Partnership getPartnership();

    void setPartnership(Partnership partnership);

    String getProtocol();

    boolean isRequestingMDN();

    boolean isConfiguredForMDN();

    boolean isRequestingAsynchMDN();

    boolean isConfiguredForAsynchMDN();

    String getSubject();

    void setSubject(String subject);

    void addHeader(String key, String value);

    String generateMessageID() throws InvalidParameterException;

    void updateMessageID() throws InvalidParameterException;

    String getLogMsgID();

    String getLogMsg();

    void setLogMsg(String msg);

    void trackMsgState(Session session);

    String getCalculatedMIC();

    void setCalculatedMIC(String calculatedMIC);

    String getAppTitle();

}
