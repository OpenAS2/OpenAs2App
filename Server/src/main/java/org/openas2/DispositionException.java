package org.openas2;

import org.openas2.util.DispositionType;

public class DispositionException extends OpenAS2Exception {
    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private DispositionType disposition;
    private String text;

    public DispositionException(DispositionType disposition, String text, Throwable cause) {
        super(disposition.toString());
        initCause(cause);
        this.disposition = disposition;
        this.text = text;
    }

    public DispositionException(DispositionType disposition, String text) {
        super(disposition.toString());
        this.disposition = disposition;
        this.text = text;
    }

    public DispositionType getDisposition() {
        return disposition;
    }

    public void setDisposition(DispositionType disposition) {
        this.disposition = disposition;
    }

    public String getText() {
        return text;
    }

    public void setText(String string) {
        text = string;
    }

}
