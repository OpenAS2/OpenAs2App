/**
 *
 */
package org.openas2.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author joseph mcverry
 *
 */
public class ByteCoder {

    public static String encode(String inStr) {
        StringBuffer sb = new StringBuffer();
        byte[] me = inStr.getBytes();
        int i;
        for (i = 0; i < me.length; i++) {
            sb.append(".");
            sb.append(me[i]);
            sb.append(".");
        }
        return sb.toString();
    }

    public static String decode(String inStr) {
        StringBuffer sb = new StringBuffer();
        Pattern pttrn = Pattern.compile(".[0-9]+.");
        Matcher match = pttrn.matcher(inStr);
        byte me;
        while (match.find()) {
            String mtch = match.group();
            me = (byte) Integer.parseInt(mtch.substring(1, mtch.length() - 1));
            sb.append((char) me);
        }
        return sb.toString();
    }

    public static void main(String[] args) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < args.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(args[i]);
        }
        String in = encode(sb.toString());
        String out = decode(in);
        if (sb.toString().equals(out)) {
            System.out.println("success");
        } else {
            System.out.println("failed expected:" + in + "\ngot:" + out);
        }
    }
}
