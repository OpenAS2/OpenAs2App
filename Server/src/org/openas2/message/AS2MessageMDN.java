package org.openas2.message;

import java.text.DecimalFormat;

import org.openas2.partner.AS2Partnership;
import org.openas2.partner.CustomIDPartnership;
import org.openas2.partner.Partnership;
import org.openas2.util.DateUtil;
import org.openas2.util.RandomUtil;

public class AS2MessageMDN extends BaseMessageMDN {
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	public static final String MDNA_REPORTING_UA = "REPORTING_UA";
    public static final String MDNA_ORIG_RECIPIENT = "ORIGINAL_RECIPIENT";
    public static final String MDNA_FINAL_RECIPIENT = "FINAL_RECIPIENT";
    public static final String MDNA_ORIG_MESSAGEID = "ORIGINAL_MESSAGE_ID";
    public static final String MDNA_DISPOSITION = "DISPOSITION";
    public static final String MDNA_MIC = "MIC";

    public AS2MessageMDN(AS2Message msg) {
        super(msg);
        setHeader("AS2-To", msg.getHeader("AS2-From"));
        setHeader("AS2-From", msg.getHeader("AS2-To"));
    }

    public String generateMessageID() {
        StringBuffer buf = new StringBuffer();
        String dateFormat = getPartnership().getAttribute(CustomIDPartnership.PA_DATE_FORMAT);
        if (dateFormat == null) {
            dateFormat = "ddMMyyyyHHmmssZ";
        }
        buf.append("<OPENAS2-").append(DateUtil.formatDate(dateFormat));

        DecimalFormat randomFormatter = new DecimalFormat("0000");
        buf.append("-").append(
                randomFormatter.format(RandomUtil.getRandomGenerator().nextInt(10000)));

        if (getMessage() != null) {
            Partnership partnership = getMessage().getPartnership();
            String senderID = partnership.getSenderID(AS2Partnership.PID_AS2);
            String receiverID = partnership.getReceiverID(AS2Partnership.PID_AS2);

            buf.append("@").append(receiverID);
            buf.append("_").append(senderID);
        }

        buf.append(">");

        return buf.toString();
    }
}