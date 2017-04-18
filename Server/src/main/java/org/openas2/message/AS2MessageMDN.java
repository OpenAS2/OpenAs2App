package org.openas2.message;

import org.apache.commons.lang3.RandomStringUtils;
import org.openas2.params.InvalidParameterException;
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
     *
     * @return a string
     * @throws InvalidParameterException 
     */
    @Override
    public String generateMessageID() throws InvalidParameterException
    {
        return org.openas2.util.AS2Util.generateMessageID(getMessage());
    }


}