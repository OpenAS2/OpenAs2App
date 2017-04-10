package org.openas2.message;

import org.apache.commons.lang3.RandomStringUtils;
import org.openas2.partner.AS2Partnership;
import org.openas2.partner.CustomIDPartnership;
import org.openas2.partner.Partnership;
import org.openas2.util.DateUtil;

public class AS2MessageMDN extends BaseMessageMDN {
    public static final String MDNA_REPORTING_UA = "REPORTING_UA";
    public static final String MDNA_ORIG_RECIPIENT = "ORIGINAL_RECIPIENT";
    public static final String MDNA_FINAL_RECIPIENT = "FINAL_RECIPIENT";
    public static final String MDNA_ORIG_MESSAGEID = "ORIGINAL_MESSAGE_ID";
    public static final String MDNA_DISPOSITION = "DISPOSITION";
    public static final String MDNA_MIC = "MIC";
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public AS2MessageMDN(AS2Message msg, boolean copyMsgHeaders)
    {
        super(msg);
        if (copyMsgHeaders)
        {
            copyHeaders(msg.getHeaders());
        }
        setHeader("AS2-To", msg.getHeader("AS2-From"));
        setHeader("AS2-From", msg.getHeader("AS2-To"));
    }

    /**
     * Generate Random Message ID based on data in the preconfigured format, sender and receiver Ids.
     * e.g. <OPENAS2-01042017103632+0200-7270@receiverId_senderId>
     *
     * @return a string
     */
    @Override
    public String generateMessageID()
    {
        StringBuilder buf = new StringBuilder();
        String dateFormat = getPartnership().getAttribute(CustomIDPartnership.PA_DATE_FORMAT);
        if (dateFormat == null)
        {
            dateFormat = "ddMMyyyyHHmmssZ";
        }
        buf.append("<OPENAS2-").append(DateUtil.formatDate(dateFormat));

        buf.append("-").append(RandomStringUtils.randomNumeric(4));

        Partnership partnership = getMessage().getPartnership();
        String senderID = partnership.getSenderID(AS2Partnership.PID_AS2);
        String receiverID = partnership.getReceiverID(AS2Partnership.PID_AS2);

        buf.append("@").append(receiverID);
        buf.append("_").append(senderID);

        buf.append(">");

        return buf.toString();
    }


}