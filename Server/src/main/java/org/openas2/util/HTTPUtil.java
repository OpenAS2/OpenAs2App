package org.openas2.util;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.SocketException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.internet.InternetHeaders;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.WrappedException;
import org.openas2.message.Message;

public class HTTPUtil {
    public static final String MA_HTTP_REQ_TYPE = "HTTP_REQUEST_TYPE";
    public static final String MA_HTTP_REQ_URL = "HTTP_REQUEST_URL";

    public static String getHTTPResponseMessage(int responseCode) {
        String msg = "Unknown";

        switch (responseCode) {
        case 100:
            msg = "Continue";

            break;

        case 101:
            msg = "Switching Protocols";

            break;

        case 200:
            msg = "OK";

            break;

        case 201:
            msg = "Created";

            break;

        case 202:
            msg = "Accepted";

            break;

        case 203:
            msg = "Non-Authoritative Information";

            break;

        case 204:
            msg = "No Content";

            break;

        case 205:
            msg = "Reset Content";

            break;

        case 206:
            msg = "Partial Content";

            break;

        case 300:
            msg = "Multiple Choices";

            break;

        case 301:
            msg = "Moved Permanently";

            break;

        case 302:
            msg = "Found";

            break;

        case 303:
            msg = "See Other";

            break;

        case 304:
            msg = "Not Modified";

            break;

        case 305:
            msg = "Use Proxy";

            break;

        case 307:
            msg = "Temporary Redirect";

            break;

        case 400:
            msg = "Bad Request";

            break;

        case 401:
            msg = "Unauthorized";

            break;

        case 402:
            msg = "Payment Required";

            break;

        case 403:
            msg = "Forbidden";

            break;

        case 404:
            msg = "Not Found";

            break;

        case 405:
            msg = "Method Not Allowed";

            break;

        case 406:
            msg = "Not Acceptable";

            break;

        case 407:
            msg = "Proxy Authentication Required";

            break;

        case 408:
            msg = "Request Time-out";

            break;

        case 409:
            msg = "Conflict";

            break;

        case 410:
            msg = "Gone";

            break;

        case 411:
            msg = "Length Required";

            break;

        case 412:
            msg = "Precondition Failed";

            break;

        case 413:
            msg = "Request Entity Too Large";

            break;

        case 414:
            msg = "Request-URI Too Large";

            break;

        case 415:
            msg = "Unsupported Media Type";

            break;

        case 416:
            msg = "Requested range not satisfiable";

            break;

        case 417:
            msg = "Expectation Failed";

            break;

        case 500:
            msg = "Internal Server Error";

            break;

        case 501:
            msg = "Not Implemented";

            break;

        case 502:
            msg = "Bad Gateway";

            break;

        case 503:
            msg = "Service Unavailable";

            break;

        case 504:
            msg = "Gateway Time-out";

            break;

        case 505:
            msg = "HTTP Version not supported";

            break;
        }

        return msg;
    }

    public static byte[] readHTTP(InputStream inStream, OutputStream outStream, InternetHeaders headerCache, List<String> httpRequest) throws IOException, MessagingException {
        byte[] data = null;
        Log logger = LogFactory.getLog(HTTPUtil.class.getSimpleName());

        // Get the stream and read in the HTTP request and headers
        BufferedInputStream in = new BufferedInputStream(inStream);
        String[] request = HTTPUtil.readRequest(in);
        for (int i = 0; i < request.length; i++)
		{
            httpRequest.add(request[i]);			
		}
        headerCache.load(in);
		if (logger.isTraceEnabled())
			logger.trace("HTTP received request: " + request[0] + "  " + request[1]
						+ "\n\tHeaders: " + printHeaders(headerCache.getAllHeaders(), "==", ";;")		
					);

        DataInputStream dataIn = new DataInputStream(in);
        // Retrieve the message content
        if (headerCache.getHeader("Content-Length") == null) {
        	String transfer_encoding = headerCache.getHeader("Transfer-Encoding", ",");
        	
        	if (transfer_encoding != null) {
        		if (transfer_encoding.replaceAll("\\s+", "").equalsIgnoreCase("chunked")) {
        			int length = 0;
        			data = null;
        			for (;;) {
        				// First get hex chunk length; followed by CRLF
        				int blocklen = 0;
        				for (;;) {
        					int ch = dataIn.readByte ();
        					if (ch == '\n') {
        						break;
        					}
        					if (ch >= 'a' && ch <= 'f') {
        						ch -= ('a' - 10);      					 
        					}
        					else if (ch >= 'A' && ch <= 'F') {
        						ch -= ('A' - 10);
        					}
        					else if (ch >= '0' && ch <= '9') {
        						ch -= '0';
        					}
        					else {
        						continue;
        					}
        					blocklen = (blocklen * 16) + ch;
        				}
        				// Zero length is end of chunks
        				if (blocklen == 0) break;
        				// Ok, now read new chunk
        				int newlen = length + blocklen;
        				byte [] newdata = new byte [newlen];
        				if (length > 0)
        					System.arraycopy(data, 0, newdata, 0, length);
        				dataIn.readFully (newdata, length, blocklen);
        				data = newdata;
        				length = newlen;
        				// And now the CRLF after the chunk;
        				while (dataIn.readByte() != '\n');
        			}
                    headerCache.setHeader("Content-Length", Integer.toString(length));
                }
        		else {
        			if (outStream != null) 
        				HTTPUtil.sendHTTPResponse(outStream, HttpURLConnection.HTTP_LENGTH_REQUIRED, null);
        			throw new IOException("Transfer-Encoding unimplemented: " + transfer_encoding);
        		}
        	}
        	else { 
        		return null;
        	}
        }
        else {
        	    // Receive the transmission's data
        	    int contentSize = Integer.parseInt(headerCache.getHeader("Content-Length", ","));
        	    data = new byte[contentSize];
        	    dataIn.readFully(data);
        	}
        return data;
    }

    /*
     * TODO: Move this out of HTTPUtil class so that class does not depend on AS2
     * specific stuff
     */
    public static byte[] readData(InputStream inStream, OutputStream outStream, Message msg)
	    throws IOException, MessagingException {
	List<String> request = new ArrayList<String>(2);
	byte[] data = readHTTP(inStream, outStream, msg.getHeaders(), request);

	msg.setAttribute(MA_HTTP_REQ_TYPE, request.get(0));
	msg.setAttribute(MA_HTTP_REQ_URL, request.get(1));
	if (data == null) {
	    String healthCheckUri = Properties.getProperty("health_check_uri", "healthcheck");
	    if ("GET".equalsIgnoreCase(request.get(0)) && request.get(1).matches("^[/]{0,1}" + healthCheckUri + "*")) {
		if (outStream != null) {
		    HTTPUtil.sendHTTPResponse(outStream, HttpURLConnection.HTTP_OK, null);
		    msg.setAttribute("isHealthCheck", "true"); // provide means for caller to know what happened
		}
		return null;
	    } else {
		HTTPUtil.sendHTTPResponse(outStream, HttpURLConnection.HTTP_LENGTH_REQUIRED, null);
		Log logger = LogFactory.getLog(HTTPUtil.class.getSimpleName());
		logger.error("Inbound HTTP request does not provide means to determine data length: " + request.get(0)
			+ " " + request.get(1) + "\n\tHeaders: "
			+ printHeaders(msg.getHeaders().getAllHeaders(), "==", ";;"));
		throw new IOException(
			"Content-Length missing and no \"Transfer-Encoding\" header found to determine how to read message body.");
	    }
	}
	return data;
    }

    public static String[] readRequest(InputStream in) throws IOException {
        int byteBuf = in.read();
        StringBuffer strBuf = new StringBuffer();

        while ((byteBuf != -1) && (byteBuf != '\r')) {
            strBuf.append((char) byteBuf);
            byteBuf = in.read();
        }

        if (byteBuf != -1) {
            in.read(); // read in the \n
        }

        StringTokenizer tokens = new StringTokenizer(strBuf.toString(), " ");
        int tokenCount = tokens.countTokens();

        if (tokenCount >= 3) {
            String[] requestParts = new String[tokenCount];

            for (int i = 0; i < tokenCount; i++) {
                requestParts[i] = tokens.nextToken();
            }

            return requestParts;
        } else if (tokenCount == 2) {
            String[] requestParts = new String[3];
            requestParts[0] = tokens.nextToken();
            requestParts[1] = "/";
            requestParts[2] = tokens.nextToken();
            return requestParts;
        } else {
            throw new IOException("Invalid HTTP Request: Token Count - " + tokenCount + "::: String length - " + strBuf.length() + " ::: String - "+ strBuf.toString());
        }
    }

    /*
     * Sends an HTTP response on the connection passed as a parameter with the specified response code.
     * If there are headers in the enumeration then it will send the headers
     * @param out The HTTP output stream
     * @param responsCode The HTTP response code to be sent
     * @param data Data if any. Can be null
     * @param headers Headers if any to be sent
     */
    public static void sendHTTPResponse(OutputStream out, int responseCode, ByteArrayOutputStream data, Enumeration<String> headers)
            throws IOException {
        StringBuffer httpResponse = new StringBuffer();
        httpResponse.append(Integer.toString(responseCode)).append(" ");
        httpResponse.append(HTTPUtil.getHTTPResponseMessage(responseCode));
        httpResponse.append("\r\n");
        StringBuffer response = new StringBuffer("HTTP/1.1 ");
        response.append(httpResponse);
        out.write(response.toString().getBytes());
        String header;

        if (headers != null) {
            boolean removeHeaderFolding = "true".equals(Properties.getProperty("remove_http_header_folding", "true"));
            while (headers.hasMoreElements()) {
                header = (String) headers.nextElement();
                // Support https://tools.ietf.org/html/draft-ietf-httpbis-p1-messaging-13#section-3.2
                if (removeHeaderFolding) {
                	header = header.replaceAll("\r\n[ \t]*", " ");
                }
                out.write((header + "\r\n").getBytes());
            }
        }

        if (data == null || data.size() < 1) {
            // if no data will be sent, write the HTTP code or zero Content-Length
            boolean sendHttpCodeAsString = "true".equals(Properties.getProperty("send_http_code_as_string_when_no_data", "false"));
    	    if (sendHttpCodeAsString) {
    		byte[] responseCodeBytes = httpResponse.toString().getBytes();
    		out.write(("Content-Length: " + responseCodeBytes.length + "\r\n\r\n").getBytes()); 
                out.write(responseCodeBytes);
    	    }
    	    else out.write("Content-Length: 0\r\n\r\n".getBytes()); 
    		
        }
        else {
            data.writeTo(out);
        }
        out.flush();
    }

    /*
     * Sends an HTTP response on the connection passed as a parameter with the specified response code.
    * @param out The HTTP output stream
     * @param responsCode The HTTP response code to be sent
     * @param data Data if any. Can be null
     */
    public static void sendHTTPResponse(OutputStream out, int responseCode, String data)
            throws IOException {
	ByteArrayOutputStream dataOS = null;
	if (data != null) {
	    dataOS = new ByteArrayOutputStream();
	    dataOS.write(data.getBytes());
	}
        
	sendHTTPResponse(out, responseCode, dataOS, null);
    }

    public static String printHeaders(Enumeration<Header> hdrs, String nameValueSeparator, String valuePairSeparator)
    {
        String headers = "";
		while (hdrs.hasMoreElements()) {
			Header h = hdrs.nextElement();
			headers = headers + valuePairSeparator + h.getName() + nameValueSeparator + h.getValue();
		}

    	return(headers);

    }
    
    /**
     * Simple method to query a site and return the response code and content (if any) in a hashmap.
     * The returned map key for response code - "response_code"
     * The returned map key for data - "response_content"
     * @param urlStr - the string representation of the URL connection
     * @param params - any parameters to be appended to URL
     * @return - the response data if any
     * @throws Exception
     */
	public static Map<String, String> querySite(String urlStr, String method, Map<String, String> params,  Map<String, String> properties) throws Exception
	{
		StringBuilder result = new StringBuilder();
		Map<String, String> responseWrapper = new HashMap<String, String>();
		boolean doOutput = (params != null && !params.isEmpty())?true:false;
		HttpURLConnection conn = getConnection(urlStr, true, true, false, method);
		conn.setRequestMethod(method);
		if (properties != null) {
			for (Map.Entry<String, String> entry : params.entrySet())
			{
				conn.setRequestProperty(entry.getKey(), entry.getValue());				
			}			
		}
		if (doOutput)
		{
			DataOutputStream out = new DataOutputStream(conn.getOutputStream());
			out.writeBytes(getParamsString(params));
			out.flush();
			out.close();
		}
		
		//Now get the response
		responseWrapper.put("response_code", "" + conn.getResponseCode());

		BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
		String line;
		while ((line = rd.readLine()) != null)
		{
			result.append(line);
		}
		rd.close();
		conn.disconnect();
		responseWrapper.put("response_content", "" + result.toString());

		return responseWrapper;
	}
    
    /**
     * Simple method to query a localhost URL with HTTPS using an overridden name verifier to avoid SSL exceptions
     * Return the response code and content (if any) in a hashmap.
     * The returned map key for response code - "response_code"
     * The returned map key for data - "response_content"
     * @param urlStr - the string representation of the URL connection
     * @param params - any parameters to be appended to URL
     * @return - the response data if any
     * @throws Exception
     */
	public static Map<String, String> querySiteSSLVerifierOverride(String urlStr, String method,
			Map<String, String> params, Map<String, String> properties) throws Exception
	{
		HostnameVerifier hnv = HttpsURLConnection.getDefaultHostnameVerifier();
		HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier()
		{

			@Override
			public boolean verify(String hostname, SSLSession session)
			{
				return true;
			}
		});
		Map<String, String> responseWrapper = querySite(urlStr, method, params,  properties);
		// reset back to original
		HttpsURLConnection.setDefaultHostnameVerifier(hnv);

		return responseWrapper;
	}
    
    public static String getParamsString(Map<String, String> params) 
          throws UnsupportedEncodingException
    {
            StringBuilder result = new StringBuilder();
     
            for (Map.Entry<String, String> entry : params.entrySet()) {
              result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
              result.append("=");
              result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
              result.append("&");
            }
     
            String resultString = result.toString();
            return resultString.length() > 0
              ? resultString.substring(0, resultString.length() - 1)
              : resultString;
    }
    
    public static boolean isLocalhostBound(InetAddress addr) {
        // Check if the address is a valid special local or loop back
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress())
            return true;

        // Check if the address is defined on any interface
        try {
            return NetworkInterface.getByInetAddress(addr) != null;
        } catch (SocketException e) {
            return false;
        }
    }


    public static HttpURLConnection getConnection(String url, boolean output, boolean input,
            boolean useCaches, String requestMethod) throws OpenAS2Exception
    {
        	if (url == null) throw new OpenAS2Exception("HTTP getConnection method received empty URL string.");
            try {
                initializeProxyAuthenticator();
                HttpURLConnection conn;
                URL urlObj = new URL(url);
                if (urlObj.getProtocol().equalsIgnoreCase("https"))
                {
                	HttpsURLConnection connS = (HttpsURLConnection) urlObj.openConnection(getProxy("https"));
                	String selfSignedCN = System.getProperty("org.openas2.cert.TrustSelfSignedCN");
    				if (selfSignedCN != null)
    				{
    					File file = new File("jssecacerts");
    					if (file.isFile() == false)
    					{
    			            char SEP = File.separatorChar;
    			            File dir = new File(System.getProperty("java.home") + SEP + "lib" + SEP + "security");
    			            /* Check if this is a JDK home */
    			            if (!dir.isDirectory())
    			            {
    			                dir = new File(System.getProperty("java.home") + SEP + "jre" + SEP + "lib" + SEP + "security");
    			            }
    			            if (!dir.isDirectory()) {
    			            	throw new OpenAS2Exception(
    			                        "The JSSE folder could not be identified. Please check that JSSE is installed.");
    			            }
    			            file = new File(dir, "jssecacerts");
    						if (file.isFile() == false)
    						{
    							file = new File(dir, "cacerts");
    						}
    					}
    					InputStream in = new FileInputStream(file);
    					try
    					{
    						KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
    						ks.load(in, "changeit".toCharArray());
    						in.close();
    						SSLContext context = SSLContext.getInstance("TLS");
    						TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory
    								.getDefaultAlgorithm());
    						tmf.init(ks);
    						X509TrustManager defaultTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];
    						SelfSignedTrustManager tm = new SelfSignedTrustManager(defaultTrustManager);
    						tm.setTrustCN(selfSignedCN);
    						context.init(null, new TrustManager[] { tm }, null);
    						connS.setSSLSocketFactory(context.getSocketFactory());
    					} catch (Exception e)
    					{
    			        	throw new OpenAS2Exception("Self-signed certificate URL connection failed connecting to : " + url, e);
    					}
    				}
    				conn = connS;
    			} else
    			{
    				conn = (HttpURLConnection) urlObj.openConnection(getProxy("http"));
    			}
                conn.setDoOutput(output);
                conn.setDoInput(input);
                conn.setUseCaches(useCaches);
                conn.setRequestMethod(requestMethod);

                return conn;
            } catch (IOException ioe) {
            	throw new WrappedException("URL connection failed connecting to: " + url, ioe);
            }
        }
        
        private static Proxy getProxy(String protocol) throws OpenAS2Exception
        {
            String proxyHost =Properties.getProperty(protocol + ".proxyHost", null);
            if (proxyHost == null) proxyHost = System.getProperty(protocol + ".proxyHost");
           if (proxyHost == null) return Proxy.NO_PROXY;
            String proxyPort =Properties.getProperty(protocol + ".proxyPort", null);
            if (proxyPort == null) proxyPort = System.getProperty(protocol + ".proxyPort");
            if (proxyPort == null) throw new OpenAS2Exception("Missing PROXY port since Proxy host is set");
            int port = Integer.parseInt(proxyPort);
            return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, port));

        }

        private static void initializeProxyAuthenticator() {
            String proxyUser1 = Properties.getProperty("http.proxyUser", null);
            final String proxyUser = proxyUser1 == null?System.getProperty("http.proxyUser"):proxyUser1;
            String proxyPwd1 =Properties.getProperty("http.proxyPassword", null);
            final String proxyPassword = proxyPwd1 == null?System.getProperty("http.proxyPassword"):proxyPwd1;

            if (proxyUser != null && proxyPassword != null) {
                Authenticator.setDefault(
                  new Authenticator() {
                    public PasswordAuthentication getPasswordAuthentication() {
                      return new PasswordAuthentication(
                        proxyUser, proxyPassword.toCharArray()
                      );
                    }
                  }
                );
            }
        }
        
        // Copy headers from an Http connection to an InternetHeaders object
        public static void copyHttpHeaders(HttpURLConnection conn, InternetHeaders headers) {
            Iterator<Map.Entry<String,List<String>>> connHeadersIt = conn.getHeaderFields().entrySet().iterator();
            Iterator<String> connValuesIt;
            Map.Entry<String,List<String>> connHeader;
            String headerName;

            while (connHeadersIt.hasNext()) {
                connHeader = connHeadersIt.next();
                headerName = connHeader.getKey();

                if (headerName != null) {
                    connValuesIt = connHeader.getValue().iterator();

                    while (connValuesIt.hasNext()) {
                        String value = connValuesIt.next();

                        String[] existingVals = headers.getHeader(headerName);
                        if (existingVals == null) {
                            headers.setHeader(headerName, value);
                        }
                        else
                        {
                        	// Avoid duplicates of the same value since headers that exist in the HTTP headers
                        	// may already have been inserted in the Message object
                        	boolean exists = false;
                        	for (int i = 0; i < existingVals.length; i++)
    						{
    							if (value.equals(existingVals[i]))
    							{
    								exists = true;
    							}
    						}
                            if (!exists) headers.addHeader(headerName, value);
                        }
                    }
                }
            }
        }

    	private static class SelfSignedTrustManager implements X509TrustManager
    	{

    		private final X509TrustManager tm;
    		private String[] trustCN = null;


    		SelfSignedTrustManager(X509TrustManager tm)
    		{
    			this.tm = tm;
    		}

    		public X509Certificate[] getAcceptedIssuers()
    		{
    			return tm.getAcceptedIssuers();
    		}

    		public void checkClientTrusted(X509Certificate[] chain, String authType)
    		{
    			throw new UnsupportedOperationException();
    		}

    		public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException
    		{
    			if (chain.length == 1)
    			{
    				// Only ignore the check for self signed certs where CN (Canonical Name) matches
    				String dn = chain[0].getIssuerDN().getName();
    				for (int i = 0; i < trustCN.length; i++)
    				{
    					if (dn.contains("CN="+trustCN[i]))
    						return;
    				}
    			}
    		    tm.checkServerTrusted(chain, authType);
    		}

    		public void setTrustCN(String trustCN)
    		{
    			this.trustCN = trustCN.split(",");
    		}

    	}
}