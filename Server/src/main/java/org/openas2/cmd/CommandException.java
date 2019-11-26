package org.openas2.cmd;

import org.openas2.OpenAS2Exception;

public class CommandException extends OpenAS2Exception {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public CommandException(String msg) {
        super(msg);
    }
}
