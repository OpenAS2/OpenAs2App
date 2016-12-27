package org.openas2.lib.message;

import java.util.NoSuchElementException;
import java.util.StringTokenizer;

import org.openas2.lib.OpenAS2Exception;


/*
 * Example: signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional, sha1 
 */
public class DispositionOptions {
    private String micAlgorithm;
    private String micAlgorithmImportance;
    private String protocol;
    private String protocolImportance;

    public DispositionOptions(String options) throws OpenAS2Exception {
        parseOptions(options);
    }

    public void setMicAlgorithm(String micalg) {
        this.micAlgorithm = micalg;
    }
    
    public String getMicAlgorithm() {
        return micAlgorithm;
    }

    public void setMicAlgorithmImportance(String micalgImportance) {
        this.micAlgorithmImportance = micalgImportance;
    }

    public String getMicAlgorithmImportance() {
        return micAlgorithmImportance;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocolImportance(String protocolImportance) {
        this.protocolImportance = protocolImportance;
    }

    public String getProtocolImportance() {
        return protocolImportance;
    }

    public String makeOptions() {
        StringBuffer options = new StringBuffer();

        if ((getProtocolImportance() == null) && (getProtocol() == null) &&
                (getMicAlgorithmImportance() == null) && (getMicAlgorithm() == null)) {
            return new String("");
        }

        options.append("signed-receipt-protocol=").append(getProtocolImportance());
        options.append(", ").append(getProtocol());
        options.append("; signed-receipt-micalg=").append(getMicAlgorithmImportance());
        options.append(", ").append(getMicAlgorithm());

        return options.toString();
    }

    public void parseOptions(String options) throws OpenAS2Exception {
        setProtocolImportance(null);
        setProtocol(null);
        setMicAlgorithmImportance(null);
        setMicAlgorithm(null);
		if (options != null) {
		
        try {
            StringTokenizer optionTokens = new StringTokenizer(options, "=,;", false);
            if (optionTokens.countTokens() > 5) {
            
            optionTokens.nextToken();
            setProtocolImportance(optionTokens.nextToken().trim());       
                setProtocol(optionTokens.nextToken().trim());
            optionTokens.nextToken();
            setMicAlgorithmImportance(optionTokens.nextToken().trim()); 
            setMicAlgorithm(optionTokens.nextToken().trim());
               
            
            }
        } catch (NoSuchElementException nsee) {
            throw new OpenAS2Exception("Invalid disposition options format: " + options);
        }
		}
    }

    public String toString() {
        return makeOptions();
    }
}
