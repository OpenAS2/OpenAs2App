package org.openas2.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.processor.receiver.AS2MDNReceiverHandler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;


public class DispositionOptions {
    private static List<String> importanceValues = Arrays.asList("required", "optional");
    private List<String> micalgs = new ArrayList<String>();
    private String micalgImportance;
    private String protocol;
    private String protocolImportance;

    public DispositionOptions(String options) throws OpenAS2Exception {
        parseOptions(options);
    }

    public void setMicalgs(List<String> micalgs) {
        this.micalgs = micalgs;
    }

    /** Per the RFC, there can be multiple algorithm options defined for the micalg disposition.
     * The RFC states : The list of MIC algorithms SHOULD be honored by the recipient from left to right.
     * The original code that has been used for years simply assumed there was only a single algorithm passed.
     * Therefore we will return the first available micalg in the list. This will work for processing the response
     * from  PARTNER because we set the disposition option sent to the partner with just a single algorithm.
     * In the case of a partner sending us multiple options for algorithm, we pick the first one and if not
     * supported, it will generate an error response allowing the partner to fix on their end but some AS2 systems
     * may not support individual settings for algorithms on a per partner basis so we may have to enhance MIC
     * processing in the future to cycle through algorithms
     * @return a string defining the algorithm to use for generating the MIC.
     */
    public String getMicalg() {
        return micalgs.isEmpty()?null:micalgs.get(0);
    }

    public void setMicalgImportance(String micalgImportance) throws OpenAS2Exception {
        if (micalgImportance != null && !importanceValues.contains(micalgImportance)) {
            throw new OpenAS2Exception("MicAlg importance can only be one of:: " + String.join(",", importanceValues) + " --- Given: " + micalgImportance);
        }
        this.micalgImportance = micalgImportance;
    }

    public String getMicalgImportance() {
        return micalgImportance;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocolImportance(String protocolImportance) throws OpenAS2Exception {
        if (protocolImportance != null && !importanceValues.contains(protocolImportance)) {
            throw new OpenAS2Exception("Protocol importance can only be one of:: " + String.join(",", importanceValues) + " --- Given: " + protocolImportance);
        }
        this.protocolImportance = protocolImportance;
    }

    public String getProtocolImportance() {
        return protocolImportance;
    }

    public String makeOptions() {
        StringBuffer options = new StringBuffer();

        if ((getProtocolImportance() == null) && (getProtocol() == null) && (getMicalgImportance() == null) && (getMicalg() == null)) {
            return "";
        }

        options.append("signed-receipt-protocol=").append(getProtocolImportance());
        options.append(", ").append(getProtocol());
        options.append("; signed-receipt-micalg=").append(getMicalgImportance());
        options.append(", ").append(getMicalg());

        Logger logger = LoggerFactory.getLogger(AS2MDNReceiverHandler.class);
        logger.info("DISPOSITION MAKE: " + options.toString());
        return options.toString();
    }

    public void parseOptions(String options) throws OpenAS2Exception {
        setProtocolImportance(null);
        setProtocol(null);
        setMicalgImportance(null);
        //setMicalgs(null);
        if (options == null) {
            return;
        }
        // The RFC specifies that each parameter can have multiple values
        // See section 7.3 of https://www.ietf.org/rfc/rfc4130.txt

        try {
            String[] optionsList = options.split("\\s*;\\s*");
            for (int i = 0; i < optionsList.length; i++) {
                String[] optionKeyValuePair = optionsList[i].split("\\s*=\\s*");
                if (optionKeyValuePair.length > 2) {
                    throw new OpenAS2Exception("Invalid disposition options format - cannot parse into distinct parameters: " + options);
                }
                String optionKey = optionKeyValuePair[0].toLowerCase().trim();
                switch (optionKey) {
                    case "signed-receipt-protocol":
                        // This parameter should only have 2 values
                        String[] protocolKeyValues = optionKeyValuePair[1].split("\\s*,\\s*");
                        if (protocolKeyValues.length != 2) {
                            throw new OpenAS2Exception("Invalid disposition options format for \"signed-receipt-protocol\" - requires 2 values: " + optionsList[i]);
                        }
                        setProtocolImportance(protocolKeyValues[0].trim());
                        setProtocol(protocolKeyValues[1].trim());
                        break;
                    case "signed-receipt-micalg":
                        // This parameter musty have at least 2 values
                        String[] micalgKeyValues = optionKeyValuePair[1].split("\\s*,\\s*");
                        if (micalgKeyValues.length < 2) {
                            throw new OpenAS2Exception("Invalid disposition options format for \"signed-receipt-micalg\" - requires at least 2 values: " + optionsList[i]);
                        }
                        List<String> micAlgValues = new ArrayList<String>();
                        micAlgValues.addAll(Arrays.asList(micalgKeyValues));
                        // Remove the importance value and store it
                        String importance = micAlgValues.remove(0);
                        setMicalgImportance(importance.trim());
                        // Store the remaining values for MIC algorithm
                        setMicalgs(micAlgValues);
                        Logger logger = LoggerFactory.getLogger(AS2MDNReceiverHandler.class);
                        logger.info("DISPOSITION MIC ALG: " + getMicalg() + "   IMPORTANCE: " + getMicalgImportance());
                        break;
                    default:
                        // TODO: Possibly log that we are ignoring this in a trace log if issues are reported around dispositions
                        break;
                }
            }
        } catch (NoSuchElementException nsee) {
            throw new OpenAS2Exception("Invalid disposition options format: " + options);
        }
    }

    public String toString() {
        return makeOptions();
    }
}
