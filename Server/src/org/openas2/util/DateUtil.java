package org.openas2.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;


public class DateUtil {
    private static final Map<String, SimpleDateFormat> formatters = new HashMap<String, SimpleDateFormat>();
        
    public static synchronized String formatDate(String format, Date value) {
        SimpleDateFormat df = getDateFormat(format);
        return df.format(value);
    }
    
    public static synchronized String formatDate(String format) {
        return formatDate(format, new Date());
    }
    
    public static synchronized Date parseDate(String format, String value) throws ParseException {
        SimpleDateFormat df = getDateFormat(format);
        return df.parse(value);
    }

    private static SimpleDateFormat getDateFormat(String format) {
        SimpleDateFormat df = (SimpleDateFormat) formatters.get(format);
        if (df == null) {
            df = new SimpleDateFormat(format);
            formatters.put(format, df);
        }
        return df;
    }
}
