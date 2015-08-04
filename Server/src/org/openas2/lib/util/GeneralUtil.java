package org.openas2.lib.util;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class GeneralUtil {

    public static String convert(Object[] array, String delimiter) {
        StringBuffer buffer = new StringBuffer();
        for (int i = 0; i < array.length; i++) {
            if (buffer.length() > 0) {
                buffer.append(delimiter);
            }
            buffer.append(array[i].toString());
        }

        return buffer.toString();
    }

    public static boolean contains(String[] array, String value) {
        for (int i = 0; i < array.length; i++) {
            if (array[i] == null) {
                if (value == null) {
                    return true;
                }
            } else if (value != null && array[i].equals(value)) {
                return true;
            }
        }
        return false;
    }

    public static String[] convert(Enumeration<String> en) {
        List<String> list = Collections.list(en);        
        return convert(list);
    }
    
    public static String[] convert(List<String> list) {
	    String[] values = new String[0];
		return (String[]) list.toArray(values);
	}
    
    public static String convert(List<Object> list, String delimiter) {
        return convert(list.toArray(), delimiter);
    }
    
    public static String[] convertKeys(Map<?, Object> map) {
        String[] keys = new String[0];
        return (String[]) map.keySet().toArray(keys);
    }
    
    public static String convert(Map<?, ?> map, String valueDelimiter, String pairDelimiter) {
		StringBuffer strBuf = new StringBuffer();
		Iterator<?> it = map.entrySet().iterator();
		Map.Entry<Object,Object> entry;
		while (it.hasNext()) {
			entry = (Entry<Object, Object>) it.next();
			strBuf.append(entry.getKey().toString()).append(valueDelimiter);
			strBuf.append(entry.getValue().toString());
			if (it.hasNext()) {
			    strBuf.append(pairDelimiter);
			}
		}
		return strBuf.toString();
	}
    
    public static String convertTrace(Exception e) {
        StringWriter writer = new StringWriter();
        e.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    public static Object getKey(Map<?,Object> map, Object value) {
        Iterator<?> it = map.entrySet().iterator();
        Map.Entry<Object,Object> entry;
        Object currentValue;
        while (it.hasNext()) {
            entry = (Entry<Object, Object>) it.next();
            currentValue = entry.getValue();
            if (currentValue == null && value == null) {
                return entry.getKey();
            } else if (currentValue != null && currentValue.equals(value)) {
                return entry.getKey();
            }
        }
        return null;
    }
}