package org.openas2.util;

import org.openas2.message.Message;
import org.openas2.params.CompositeParameters;
import org.openas2.params.DateParameters;
import org.openas2.params.InvalidParameterException;
import org.openas2.params.MessageMDNParameters;
import org.openas2.params.MessageParameters;
import org.openas2.params.ParameterParser;
import org.openas2.params.RandomParameters;

import java.util.List;
import java.util.Map;

public class StringUtil {

    public static String mapToString(Map<Object, Object> map, String keyValContenator, String entryConcatenator) {
        if (map == null) {
            return null;
        }
        StringBuffer strBuf = new StringBuffer(10);
        for (Map.Entry<Object, Object> pair : map.entrySet()) {
            strBuf.append(pair.getKey()).append(keyValContenator);
            Object val = pair.getValue();
            if (val instanceof String) {
                strBuf.append(val);
            } else if (val instanceof List) {
                listToString((List) val, ",");
            } else {
                strBuf.append(val.toString());
            }
            strBuf.append(entryConcatenator);
        }
        return strBuf.toString();
    }

    public static String listToString(List myList, String entryConcatenator) {
        StringBuffer strBuf = new StringBuffer(10);
        for (int i = 0; i < myList.size() - 1; i++) {
            strBuf.append(myList.get(i)).append(entryConcatenator);
        }
        return strBuf.append(myList.get(myList.size() - 1)).toString();
    }

    public static String mapToString(Map<String, List<String>> map, String keyValContenator, String entryConcatenator, String listConcatenator) {
        if (map == null) {
            return null;
        }
        StringBuffer strBuf = new StringBuffer(10);
        for (Map.Entry<String, List<String>> pair : map.entrySet()) {
            strBuf.append(pair.getKey()).append(keyValContenator);
            Object val = pair.getValue();
            if (val instanceof String) {
                strBuf.append(val);
            } else if (val instanceof List) {
                listToString((List) val, listConcatenator);
            } else {
                strBuf.append(val.toString());
            }
            strBuf.append(entryConcatenator);
        }
        return strBuf.toString();
    }

    /*
     * Returns a string where identified parameters are replaced with their values
     * @param paramString - the string containing parameters to be parsed and replaced
     * @param msg - optional parameter, The message object associated with AS2 messages
     * @return the parsed string
     * @throws InvalidParameterException - a parameter found in the string cannot be identified
     */
    public static String parseParameterisedString(String paramString, Message msg) throws InvalidParameterException {
        paramString = paramString.replaceAll("%home%", Properties.getProperty(Properties.APP_BASE_DIR_PROP, "%home%"));
        CompositeParameters compParams = new CompositeParameters(false).add("date", new DateParameters()).add("rand", new RandomParameters());
        if (msg != null) {
            compParams.add("msg", new MessageParameters(msg));

            if (msg.getMDN() != null) {
                compParams.add("mdn", new MessageMDNParameters(msg.getMDN()));
            }
        }

        return ParameterParser.parse(paramString, compParams);
    }

    public static String removeDoubleQuotes(String srcString) {
        if (srcString == null) {
            return null;
        }
        return srcString.replaceAll("^\"([^\"]+)\"$", "$1");
    }

}
