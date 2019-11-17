package org.openas2.processor.msgtracking;

import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.message.AS2MessageMDN;
import org.openas2.message.FileAttribute;
import org.openas2.message.Message;
import org.openas2.message.MessageMDN;
import org.openas2.partner.Partnership;
import org.openas2.processor.BaseProcessorModule;
import org.openas2.processor.resender.ResenderModule;

import java.util.HashMap;
import java.util.Map;

public abstract class BaseMsgTrackingModule extends BaseProcessorModule implements TrackingModule {

    public void handle(String action, Message msg, Map<Object, Object> options) throws OpenAS2Exception {

        Map<String, String> fields = buildMap(msg, options);
        persist(msg, fields);

    }

    public boolean canHandle(String action, Message msg, Map<Object, Object> options) {
        return action.equals(getModuleAction());
    }

    public void init(Session session, Map<String, String> options) throws OpenAS2Exception {
        super.init(session, options);
    }

    protected abstract String getModuleAction();

    protected abstract void persist(Message msg, Map<String, String> map);

    protected Map<String, String> buildMap(Message msg, Map<Object, Object> options) {
        Map<String, String> map = new HashMap<String, String>();
        String msgId = msg.getMessageID();
        MessageMDN mdn = msg.getMDN();
        if (mdn != null) {
            map.put(FIELDS.MDN_ID, mdn.getMessageID());
            map.put(FIELDS.MDN_RESPONSE, msg.getMDN().getText());
            // Make sure we log against the original message ID since MDN can have different ID
            String originalMsgId = mdn.getAttribute(AS2MessageMDN.MDNA_ORIG_MESSAGEID);
            if (originalMsgId != null && !msgId.equals(originalMsgId)) {
                msgId = originalMsgId;
            }
        }
        map.put(FIELDS.MSG_ID, msgId);
        map.put(FIELDS.PRIOR_MSG_ID, msg.getAttribute(FIELDS.PRIOR_MSG_ID));
        // Default DIRECTION to SEND for now...
        String direction = (String) options.get(FIELDS.DIRECTION);
        map.put(FIELDS.DIRECTION, direction == null ? "SEND" : direction);
        String isResend = (String) options.get(FIELDS.IS_RESEND);
        if (isResend != null) {
            map.put(FIELDS.IS_RESEND, isResend);
            map.put(FIELDS.RESEND_COUNT, (String) options.get(ResenderModule.OPTION_RETRIES));
        }
        //map.put(FIELDS.RESEND_COUNT, );
        String sender = msg.getPartnership().getSenderID(Partnership.PID_AS2);
        if (sender == null) {
            sender = mdn.getPartnership().getSenderID(Partnership.PID_AS2);
        }
        map.put(FIELDS.SENDER_ID, sender);
        String receiver = msg.getPartnership().getReceiverID(Partnership.PID_AS2);
        if (receiver == null) {
            receiver = mdn.getPartnership().getReceiverID(Partnership.PID_AS2);
        }
        map.put(FIELDS.RECEIVER_ID, receiver);
        map.put(FIELDS.STATUS, msg.getStatus());
        String state = (String) options.get("STATE");
        map.put(FIELDS.STATE, state);
        map.put(FIELDS.STATE_MSG, Message.STATE_MSGS.get(state));
        map.put(FIELDS.SIGNATURE_ALGORITHM, msg.getPartnership().getAttribute(Partnership.PA_SIGNATURE_ALGORITHM));
        map.put(FIELDS.ENCRYPTION_ALGORITHM, msg.getPartnership().getAttribute(Partnership.PA_ENCRYPTION_ALGORITHM));
        map.put(FIELDS.COMPRESSION, msg.getPartnership().getAttribute(Partnership.PA_COMPRESSION_TYPE));
        map.put(FIELDS.FILE_NAME, msg.getPayloadFilename());
        map.put(FIELDS.SENT_FILE_NAME, msg.getAttribute(FileAttribute.MA_FILENAME));
        map.put(FIELDS.CONTENT_TYPE, msg.getContentType());
        map.put(FIELDS.CONTENT_TRANSFER_ENCODING, msg.getHeader("Content-Transfer-Encoding"));
        map.put(FIELDS.MDN_MODE, (msg.getPartnership().isAsyncMDN() ? "ASYNC" : "SYNC"));

        return map;
    }

    public static class FIELDS {
        public static final String MSG_ID = "msg_id";
        public static final String PRIOR_MSG_ID = "prior_msg_id";
        public static final String MDN_ID = "mdn_id";
        public static final String DIRECTION = "direction";
        public static final String IS_RESEND = "is_resend";
        public static final String RESEND_COUNT = "resend_count";
        public static final String SENDER_ID = "sender_id";
        public static final String RECEIVER_ID = "receiver_id";
        public static final String STATUS = "status";
        public static final String STATE = "state";
        public static final String SIGNATURE_ALGORITHM = "signature_algorithm";
        public static final String ENCRYPTION_ALGORITHM = "encryption_algorithm";
        public static final String COMPRESSION = "compression";
        public static final String FILE_NAME = "file_name";
        public static final String SENT_FILE_NAME = "sent_file_name";
        public static final String CONTENT_TYPE = "content_type";
        public static final String CONTENT_TRANSFER_ENCODING = "content_transfer_encoding";
        public static final String MDN_MODE = "mdn_mode";
        public static final String MDN_RESPONSE = "mdn_response";
        public static final String STATE_MSG = "state_msg";
        public static final String CREATE_DT = "create_dt";
        public static final String UPDATE_DT = "update_dt";
    }

}
