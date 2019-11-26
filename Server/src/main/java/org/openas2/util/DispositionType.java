package org.openas2.util;

import org.openas2.DispositionException;
import org.openas2.OpenAS2Exception;

import java.util.NoSuchElementException;
import java.util.StringTokenizer;


public class DispositionType {
    private String action;
    private String mdnAction;
    private String status;
    private String statusDescription;
    private String statusModifier;

    public DispositionType(String action, String mdnAction, String status, String statusModifier, String statusDescription) {
        super();
        this.action = action;
        this.mdnAction = mdnAction;
        this.status = status;
        this.statusModifier = statusModifier;
        this.statusDescription = statusDescription;
    }

    public DispositionType(String action, String mdnAction, String status) {
        super();
        this.action = action;
        this.mdnAction = mdnAction;
        this.status = status;
        this.statusModifier = null;
        this.statusDescription = null;
    }

    public DispositionType(String disposition) throws OpenAS2Exception {
        super();

        if (disposition != null) {
            parseDisposition(disposition);
        }
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getAction() {
        return action;
    }

    public void setMdnAction(String mdnAction) {
        this.mdnAction = mdnAction;
    }

    public String getMdnAction() {
        return mdnAction;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatus() {
        return status;
    }

    public void setStatusDescription(String statusDescription) {
        this.statusDescription = statusDescription;
    }

    public String getStatusDescription() {
        return statusDescription;
    }

    public void setStatusModifier(String statusModifier) {
        this.statusModifier = statusModifier;
    }

    public String getStatusModifier() {
        return statusModifier;
    }

    public boolean isWarning() {
        String statusMod = getStatusModifier();

        return ((statusMod != null) && statusMod.equalsIgnoreCase("warning"));
    }

    public String toString() {
        return makeDisposition();
    }

    public void validate() throws DispositionException {
        String status = getStatus();

        if (status == null) {
            throw new DispositionException(this, "Disposition status is NULL. Cannot continue.");
        } else if (!status.equalsIgnoreCase("processed")) {
            throw new DispositionException(this, "Disposition status indicates a problem. Returned status is: " + status);
        }

        String statusMod = getStatusModifier();

        if (statusMod != null) {
            if (statusMod.equalsIgnoreCase("error") || statusMod.equalsIgnoreCase("warning")) {
                throw new DispositionException(this, "The recipient is indicating an issue with the received message. Returned status is: " + status);
            }
        }
    }

    protected String makeDisposition() {
        StringBuffer dispBuf = new StringBuffer();
        dispBuf.append(getAction()).append("/").append(getMdnAction());
        dispBuf.append("; ").append(getStatus());

        if (getStatusModifier() != null) {
            dispBuf.append("/").append(getStatusModifier()).append(":");

            if (getStatusDescription() != null) {
                dispBuf.append(getStatusDescription());
            }
        }

        return dispBuf.toString();
    }

    protected void parseDisposition(String disposition) throws OpenAS2Exception {
        StringTokenizer dispTokens = new StringTokenizer(disposition, "/;:", false);

        try {
            setAction(dispTokens.nextToken().toLowerCase());
            setMdnAction(dispTokens.nextToken().toLowerCase());
            setStatus(dispTokens.nextToken().trim().toLowerCase());
            setStatusModifier(null);
            setStatusDescription(null);

            if (dispTokens.hasMoreTokens()) {
                setStatusModifier(dispTokens.nextToken().toLowerCase());

                if (dispTokens.hasMoreTokens()) {
                    setStatusDescription(dispTokens.nextToken().trim().toLowerCase());
                }
            }
        } catch (NoSuchElementException nsee) {
            throw new OpenAS2Exception("Invalid disposition type format: " + disposition);
        }
    }
}
