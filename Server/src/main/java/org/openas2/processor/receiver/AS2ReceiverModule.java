package org.openas2.processor.receiver;

import org.openas2.message.NetAttribute;
import org.openas2.params.MessageParameters;
import org.openas2.partner.Partnership;


public class AS2ReceiverModule extends NetModule {
    // Macros for responses
    public static final String MSG_SENDER = "$" + MessageParameters.KEY_SENDER + "." + Partnership.PID_AS2 + "$";
    public static final String MSG_RECEIVER = "$" + MessageParameters.KEY_RECEIVER + "." + Partnership.PID_AS2 + "$";
    public static final String MSG_DATE = "$" + MessageParameters.KEY_HEADERS + ".date" + "$";
    public static final String MSG_SUBJECT = "$" + MessageParameters.KEY_HEADERS + ".subject" + "$";
    public static final String MSG_SOURCE_ADDRESS = "$" + MessageParameters.KEY_ATTRIBUTES + "." + NetAttribute.MA_SOURCE_IP + "$";
    public static final String DP_HEADER = "The message sent to Recipient " + MSG_RECEIVER + " on " + MSG_DATE + " with Subject " + MSG_SUBJECT + " has been received, ";
    public static final String DP_DECRYPTED = DP_HEADER + "the EDI Interchange was successfully decrypted and it's integrity was verified. ";
    public static final String DP_VERIFIED = DP_DECRYPTED + "In addition, the sender of the message, Sender " + MSG_SENDER + " at Location " + MSG_SOURCE_ADDRESS + " was authenticated as the originator of the message. ";

    // Response texts    
    public static final String DISP_PARTNERSHIP_NOT_FOUND = DP_HEADER + "but the Sender " + MSG_SENDER + " and/or Recipient " + MSG_RECEIVER + " are unknown.";
    public static final String DISP_PARSING_MIME_FAILED = DP_HEADER + "but an error occured while parsing the MIME content.";
    public static final String DISP_DECRYPTION_ERROR = DP_HEADER + "but an error occured decrypting the content.";
    public static final String DISP_DECOMPRESSION_ERROR = DP_HEADER + "but an error occured decompressing the content.";
    public static final String DISP_VERIFY_SIGNATURE_FAILED = DP_DECRYPTED + "Authentication of the originator of the message failed.";
    public static final String DISP_CALC_MIC_FAILED = DP_DECRYPTED + "Calculation of the MIC for the message failed.";
    public static final String DISP_STORAGE_FAILED = DP_VERIFIED + " An error occured while storing the data to the file system.";
    public static final String DISP_SUCCESS = DP_VERIFIED + "There is no guarantee however that the EDI Interchange was syntactically correct, or was received by the EDI application/translator.";

    private NetModuleHandler module;


    protected NetModuleHandler getHandler() {
        module = new AS2ReceiverHandler(this);
        return module;
    }


}
