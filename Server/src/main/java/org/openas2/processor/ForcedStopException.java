package org.openas2.processor;

import org.openas2.WrappedException;


public class ForcedStopException extends WrappedException {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    public ForcedStopException(Exception source) {
        super(source);
    }
}
