package org.openas2.util;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.util.StringTokenizer;

import javax.mail.MessagingException;
import javax.mail.internet.InternetHeaders;

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

    public static byte[] readData(Socket s, Message msg) throws IOException, MessagingException {
        byte[] data = null;

        // Get the stream and read in the HTTP request and headers
        BufferedInputStream in = new BufferedInputStream(s.getInputStream());
        String[] request = HTTPUtil.readRequest(in);
        msg.setAttribute(MA_HTTP_REQ_TYPE, request[0]);
        msg.setAttribute(MA_HTTP_REQ_URL, request[1]);
        msg.setHeaders(new InternetHeaders(in));
        DataInputStream dataIn = new DataInputStream(in);
        // Retrieve the message content
        if (msg.getHeader("Content-Length") == null) {
        	String transfer_encoding = msg.getHeader("Transfer-Encoding");
        	
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
        				while (dataIn.readByte () != '\n');
        			}
        			msg.setHeader("Content-Length", new Integer(length).toString());
        		}
        		else {
        			HTTPUtil.sendHTTPResponse(s.getOutputStream(), HttpURLConnection.HTTP_LENGTH_REQUIRED,
        					false);
        			throw new IOException("Transfer-Encoding unimplemented: " + transfer_encoding);
        		}
        	}
        	else if (msg.getHeader("Content-Length") == null) { 
        		HTTPUtil.sendHTTPResponse(s.getOutputStream(), HttpURLConnection.HTTP_LENGTH_REQUIRED,
                         false);
            throw new IOException("Content-Length missing");
        	}
        }
        else {
        	    // Receive the transmission's data
        	    int contentSize = Integer.parseInt(msg.getHeader("Content-Length"));
        	    data = new byte[contentSize];
        	    dataIn.readFully(data);
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
            throw new IOException("Invalid HTTP Request");
        }
    }

    public static void sendHTTPResponse(OutputStream out, int responseCode, boolean hasData)
            throws IOException {
        StringBuffer httpResponse = new StringBuffer();
        httpResponse.append(Integer.toString(responseCode)).append(" ");
        httpResponse.append(HTTPUtil.getHTTPResponseMessage(responseCode));
        httpResponse.append("\r\n");
        StringBuffer response = new StringBuffer("HTTP/1.1 ");
        response.append(httpResponse);
        out.write(response.toString().getBytes());
        if (!hasData) { // if no data will be sent, write the HTTP code
            out.write("\r\n".getBytes());
            out.write(httpResponse.toString().getBytes());
        }
    }
}