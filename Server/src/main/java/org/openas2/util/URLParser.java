package org.openas2.util;
/**
 * @author James Wang (wangwenpin@users.sourceforge.net)
 */

import org.openas2.lib.OpenAS2Exception;

public class URLParser {
	String url = "";
	String host = "?";
	int port = 80;
	String resourcename = "?";
	public URLParser(String url) throws OpenAS2Exception {
		if (!url.toUpperCase().startsWith("HTTP://")) {
			throw new OpenAS2Exception("bad url for URLParser,  see "+url);
		}
		url = url.substring(7);
		int pos1 = url.indexOf(":");
		int pos2 = url.indexOf("/");
		if (pos1 < 0) {
			port = 80;
			host = url.substring(0, pos2);
		} else {
			port = Integer.parseInt(url.substring(pos1 + 1, pos2));
			host = url.substring(0, pos1);
		}
		resourcename = url.substring(pos2);	}
	public String getHOST() {
		return host;
	}
	public int getPORT() {
		return port;
	}
	public String getRESOURCE() {
		return resourcename;
	}

	/**
	 * @param args
	 * @throws OpenAS2Exception 
	 */
	public static void main(String[] args) throws OpenAS2Exception {
		
		URLParser UP = new URLParser("http://www.google.com/search?ie=UTF-8&oe=UTF-8&sourceid=navclient&gfns=1&q=urlparser+java");
	
		System.out.println("host : " + UP.getHOST());
		System.out.println("port : " + UP.getPORT());
		System.out.println("resource : " + UP.getRESOURCE());
	}
}