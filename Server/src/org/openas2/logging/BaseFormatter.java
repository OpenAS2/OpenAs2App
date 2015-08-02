package org.openas2.logging;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.openas2.OpenAS2Exception;


public abstract class BaseFormatter implements Formatter {
    public String format(Level level, String msg) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        format(level, msg, baos);

        String output = new String(baos.toByteArray());

        try {
            baos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return output;
    }

    public String format(OpenAS2Exception exception, boolean terminated) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        format(exception, terminated, baos);

        String output = new String(baos.toByteArray());

        try {
            baos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return output;
    }
}
