package org.openas2.lib.util;

import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Random;

public class RandomUtil {
	private static final Random randomInstance = new Random();
	
	public static Random getInstance() {
		return randomInstance;
	}
	
	public static String nextDecimal(int length) {
		if (length == 0) {
			return "";
		}
		byte[] format = new byte[length];
		Arrays.fill(format, (byte) '0');
		DecimalFormat randomFormatter = new DecimalFormat(new String(format));
		return randomFormatter.format(getInstance().nextInt((int) Math.pow(10, length)));
	}
	
	public static byte[] nextBytes(int length) {
	    Random r = getInstance();
	    byte[] buf = new byte[length];
	    r.nextBytes(buf);
	    return buf;
	}
}
