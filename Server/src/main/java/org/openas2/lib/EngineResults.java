package org.openas2.lib;

import org.openas2.lib.message.Disposition;
import org.openas2.lib.partner.IPartnership;

public class EngineResults {
    public static final int STATUS_NONE = 0;
    public static final int STATUS_OK = 1;
    public static final int STATUS_ERROR = 2;
    private OpenAS2Exception exception;
    private IPartnership partnership;
    private int encryption;
    private int signature;

    public IPartnership getPartnership() {
        return partnership;
    }

    public void setPartnership(IPartnership partnership) {
        this.partnership = partnership;
    }

    public int getEncryption() {
        return encryption;
    }

    public void setEncryption(int encryption) {
        this.encryption = encryption;
    }

    public OpenAS2Exception getException() {
        return exception;
    }

    public void setException(OpenAS2Exception exception) {
        this.exception = exception;
    }

    public int getSignature() {
        return signature;
    }

    public void setSignature(int signature) {
        this.signature = signature;
    }

    public boolean errorOccurred() {
        if (getException() != null) {
            return true;
        }
        if (getPartnership() == null) {
            return true;
        }
        if (getEncryption() == STATUS_ERROR) {
            return true;
        }
        if (getSignature() == STATUS_ERROR) {
            return true;
        }
        return false;
    }

    public String getStatusDescription(int status) {
        switch (status) {
        case STATUS_NONE:
            return "None";

        case STATUS_OK:
            return "Ok";

        case STATUS_ERROR:
            return "Error";
        }

        return "Unknown";
    }

    public String getDisposition() {
        // if no error occured, return the processed disposition
        if (!errorOccurred()) {
            return Disposition.DISP_PROCESSED;
        }

        // an error occurred finding the partnership
        if (getPartnership() == null) {
            return Disposition.DISP_AUTHENTICATION_FAILED;
        }

        // an error occurred during decryption
        if (getEncryption() == STATUS_ERROR) {
            return Disposition.DISP_DECRYPTION_FAILED;
        }

        // an error occurred during signature verification
        if (getSignature() == STATUS_ERROR) {
            return Disposition.DISP_SIGNATURE_FAILED;
        }

        // an unexpected error occured if a result hasn't been returned yet
        return Disposition.DISP_UNEXPECTED_ERROR;
    }

    public String toString() {
        StringBuffer strBuf = new StringBuffer();
        strBuf.append("Partnership: ").append(getPartnership());
        strBuf.append("  Encryption: ").append(getStatusDescription(getEncryption()));
        strBuf.append("  Signature: ").append(getStatusDescription(getSignature()));

        if (getException() != null) {
            strBuf.append("  Exception: ").append(getException().toString());
        }

        return strBuf.toString();
    }

}