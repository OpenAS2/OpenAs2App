package org.openas2.util;

import java.util.List;
import java.util.Map;

import org.openas2.message.Message;
import org.openas2.params.CompositeParameters;
import org.openas2.params.DateParameters;
import org.openas2.params.InvalidParameterException;
import org.openas2.params.MessageParameters;
import org.openas2.params.ParameterParser;
import org.openas2.params.RandomParameters;

public class StringUtil {

    public static String mapToString(Map<Object, Object> map, String keyValContenator, String entryConcatenator)
    {
	if (map == null) return null;
	StringBuffer strBuf = new StringBuffer(10);
	for (Map.Entry<Object, Object> pair: map.entrySet()) {
	    strBuf.append(pair.getKey()).append(keyValContenator);
	    Object val = pair.getValue();
	    if (val instanceof String) strBuf.append(val);
	    else if (val instanceof List) listToString((List)val, ",");
	    else strBuf.append(val.toString());
	    strBuf.append(entryConcatenator);
	}
        return strBuf.toString();
    }

    public static String listToString(List myList, String entryConcatenator)
    {
	StringBuffer strBuf = new StringBuffer(10);
	for (int i = 0; i < myList.size()-1; i++)
	{
	    strBuf.append(myList.get(i)).append(entryConcatenator);
	}
        return strBuf.append(myList.get(myList.size()-1)).toString();
    }

    public static String mapToString(Map<String, List<String>> map, String keyValContenator, String entryConcatenator, String listConcatenator)
    {
	if (map == null) return null;
	StringBuffer strBuf = new StringBuffer(10);
	for (Map.Entry<String, List<String>> pair: map.entrySet()) {
	    strBuf.append(pair.getKey()).append(keyValContenator);
	    Object val = pair.getValue();
	    if (val instanceof String) strBuf.append(val);
	    else if (val instanceof List) listToString((List)val, listConcatenator);
	    else strBuf.append(val.toString());
	    strBuf.append(entryConcatenator);
	}
        return strBuf.toString();
    }

}
