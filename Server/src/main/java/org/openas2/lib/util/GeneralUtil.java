package org.openas2.lib.util;

import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class GeneralUtil {


    public static String[] convert(Enumeration<String> en) {
        List<String> list = Collections.list(en);
        return convert(list);
    }

    static String[] convert(List<String> list) {
        String[] values = new String[0];
        return list.toArray(values);
    }

    public static String[] convertKeys(Map<?, Object> map) {
        String[] keys = new String[0];
        return map.keySet().toArray(keys);
    }

    public static String convert(Map<?, ?> map, String valueDelimiter, String pairDelimiter) {
        StringBuffer strBuf = new StringBuffer();
        Iterator<?> it = map.entrySet().iterator();
        Map.Entry<Object, Object> entry;
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

    public static Object getKey(Map<?, Object> map, Object value) {
        Iterator<?> it = map.entrySet().iterator();
        Map.Entry<Object, Object> entry;
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
