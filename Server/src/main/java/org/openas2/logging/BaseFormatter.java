package org.openas2.logging;

import org.openas2.OpenAS2Exception;
import org.openas2.util.Properties;

import java.io.ByteArrayOutputStream;
import java.io.IOException;


public abstract class BaseFormatter implements Formatter {

    protected String dateFormat = Properties.getProperty("log_date_format", "yyyy-MM-dd HH:mm:ss.SSS");

    @Override
    public void setDateFormat(String dateFormat) {
        this.dateFormat = dateFormat;
    }

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
