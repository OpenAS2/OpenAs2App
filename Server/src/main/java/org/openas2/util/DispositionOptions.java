package org.openas2.util;

import org.openas2.OpenAS2Exception;

import java.util.NoSuchElementException;
import java.util.StringTokenizer;


public class DispositionOptions {
    private String micalg;
    private String micalgImportance;
    private String protocol;
    private String protocolImportance;

    public DispositionOptions(String options) throws OpenAS2Exception {
        parseOptions(options);
    }

    public void setMicalg(String micalg) {
        this.micalg = micalg;
    }

    // signed-receipt-protocol=optional, pkcs7-signature; signed-receipt-micalg=optional, sha1
    public String getMicalg() {
        return micalg;
    }

    public void setMicalgImportance(String micalgImportance) {
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

    public void setProtocolImportance(String protocolImportance) {
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

        return options.toString();
    }

    public void parseOptions(String options) throws OpenAS2Exception {
        setProtocolImportance(null);
        setProtocol(null);
        setMicalgImportance(null);
        setMicalg(null);
        if (options != null) {

            // TODO: This parsing is far too rigid and will likely fail. The RFC specifies that each parameter can have multiple values
            // See section 7.3 of https://www.ietf.org/rfc/rfc4130.txt

            try {
                StringTokenizer optionTokens = new StringTokenizer(options, "=,;", false);
                if (optionTokens.countTokens() > 5) {

                    optionTokens.nextToken();
                    setProtocolImportance(optionTokens.nextToken().trim());
                    setProtocol(optionTokens.nextToken().trim());
                    optionTokens.nextToken();
                    setMicalgImportance(optionTokens.nextToken().trim());
                    setMicalg(optionTokens.nextToken().trim());


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
