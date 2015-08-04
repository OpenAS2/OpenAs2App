package org.openas2.processor.sender;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.mail.internet.InternetHeaders;

import org.openas2.OpenAS2Exception;
import org.openas2.WrappedException;


public abstract class HttpSenderModule extends BaseSenderModule implements SenderModule {
	
	  public static final String PARAM_READ_TIMEOUT = "readtimeout";
	  public static final String PARAM_CONNECT_TIMEOUT = "connecttimeout";
	  
	
    public HttpURLConnection getConnection(String url, boolean output, boolean input,
        boolean useCaches, String requestMethod) throws OpenAS2Exception {
        try {
        	System.setProperty("sun.net.client.defaultReadTimeout", getParameter(PARAM_READ_TIMEOUT, "60000"));
            System.setProperty("sun.net.client.defaultConnectTimeout", getParameter(PARAM_CONNECT_TIMEOUT, "60000"));
            URL urlObj = new URL(url);
            HttpURLConnection conn;
            conn = (HttpURLConnection) urlObj.openConnection();
            conn.setDoOutput(output);
            conn.setDoInput(input);
            conn.setUseCaches(useCaches);
            conn.setRequestMethod(requestMethod);

            return conn;
        } catch (IOException ioe) {
            throw new WrappedException(ioe);
        }
    }

    // Copy headers from an Http connection to an InternetHeaders object
    protected void copyHttpHeaders(HttpURLConnection conn, InternetHeaders headers) {
        Iterator<Map.Entry<String,List<String>>> connHeadersIt = conn.getHeaderFields().entrySet().iterator();
        Iterator<String> connValuesIt;
        Map.Entry<String,List<String>> connHeader;
        String headerName;

        while (connHeadersIt.hasNext()) {
            connHeader = (Entry<String, List<String>>) connHeadersIt.next();
            headerName = (String) connHeader.getKey();

            if (headerName != null) {
                connValuesIt = ((Collection) connHeader.getValue()).iterator();

                while (connValuesIt.hasNext()) {
                    String value = (String) connValuesIt.next();

                    if (headers.getHeader(headerName) == null) {
                        headers.setHeader(headerName, value);
                    } else {
                        headers.addHeader(headerName, value);
                    }
                }
            }
        }
    }
}
