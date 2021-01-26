package org.openas2.util;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.net.URISyntaxException;
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

import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHost;
import org.apache.http.NameValuePair;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.ssl.SSLContexts;
import org.openas2.OpenAS2Exception;
import org.openas2.WrappedException;
import org.openas2.message.Message;


/**
 * @author Christopher
 */
public class HTTPUtil {
    public static final String MA_HTTP_REQ_TYPE = "HTTP_REQUEST_TYPE";
    public static final String MA_HTTP_REQ_URL = "HTTP_REQUEST_URL";

    public static final String HTTP_PROP_REMOVE_HEADER_FOLDING = "remove_http_header_folding";
    public static final String HTTP_PROP_SSL_PROTOCOLS = "http_ssl_protocols";
    public static final String HTTP_PROP_OVERRIDE_SSL_CHECKS = "http_override_ssl_checks";

    public static final String PARAM_READ_TIMEOUT = "readtimeout";
    public static final String PARAM_CONNECT_TIMEOUT = "connecttimeout";
    public static final String PARAM_SOCKET_TIMEOUT = "sockettimeout";
    public static final String PARAM_HTTP_USER = "http_user";
    public static final String PARAM_HTTP_PWD = "http_password";

    public static final String HEADER_CONTENT_TYPE = "Content-Type";
    public static final String HEADER_USER_AGENT = "User-Agent";
    public static final String HEADER_CONNECTION = "Connection";

    public abstract static class Method {
        public static final String GET = "GET";
        public static final String HEAD = "HEAD";
        public static final String POST = "POST";
        public static final String PUT = "PUT";
        public static final String DELETE = "DELETE";
        public static final String TRACE = "TRACE";
        public static final String CONNECT = "CONNECT";
    }

    private static final Map<Integer, String> httpResponseCodeToPhrase = new HashMap<Integer, String>() {
        private static final long serialVersionUID = 1L;

        {
            put(100, "Continue");
            put(101, "Switching Protocols");
            put(200, "OK");
            put(201, "Created");
            put(202, "Accepted");
            put(203, "Non-Authoritative Information");
            put(204, "No Content");
            put(205, "Reset Content");
            put(206, "Partial Content");
            put(300, "Multiple Choices");
            put(301, "Moved Permanently");
            put(302, "Found");
            put(303, "See Other");
            put(304, "Not Modified");
            put(305, "Use Proxy");
            put(307, "Temporary Redirect");
            put(400, "Bad Request");
            put(401, "Unauthorized");
            put(402, "Payment Required");
            put(403, "Forbidden");
            put(404, "Not Found");
            put(405, "Method Not Allowed");
            put(406, "Not Acceptable");
            put(407, "Proxy Authentication Required");
            put(408, "Request Time-out");
            put(409, "Conflict");
            put(410, "Gone");
            put(411, "Length Required");
            put(412, "Precondition Failed");
            put(413, "Request Entity Too Large");
            put(414, "Request-URI Too Large");
            put(415, "Unsupported Media Type");
            put(416, "Requested range not satisfiable");
            put(417, "Expectation Failed");
            put(500, "Internal Server Error");
            put(501, "Not Implemented");
            put(502, "Bad Gateway");
            put(503, "Service Unavailable");
            put(504, "Gateway Time-out");
            put(505, "HTTP Version not supported");
        }
    };

    public static String getHTTPResponseMessage(int responseCode) {
        String code = httpResponseCodeToPhrase.get(responseCode);
        return (code == null) ? "Unknown" : code;
    }

    public static byte[] readHTTP(InputStream inStream, OutputStream outStream, InternetHeaders headerCache, List<String> httpRequest) throws IOException, MessagingException {
        byte[] data = null;
        Log logger = LogFactory.getLog(HTTPUtil.class.getSimpleName());

        // Get the stream and read in the HTTP request and headers
        BufferedInputStream in = new BufferedInputStream(inStream);
        String[] request = HTTPUtil.readRequest(in);
        for (int i = 0; i < request.length; i++) {
            httpRequest.add(request[i]);
        }
        headerCache.load(in);
        if (logger.isTraceEnabled()) {
            logger.trace("HTTP received request: " + request[0] + "  " + request[1] + "\n\tHeaders: " + printHeaders(headerCache.getAllHeaders(), "==", ";;"));
        }

        DataInputStream dataIn = new DataInputStream(in);
        // Retrieve the message content
        if (headerCache.getHeader("Content-Length") == null) {
            String transfer_encoding = headerCache.getHeader("Transfer-Encoding", ",");

            if (transfer_encoding != null) {
                if (transfer_encoding.replaceAll("\\s+", "").equalsIgnoreCase("chunked")) {
                    int length = 0;
                    data = null;
                    for (; ; ) {
                        // First get hex chunk length; followed by CRLF
                        int blocklen = 0;
                        for (; ; ) {
                            int ch = dataIn.readByte();
                            if (ch == '\n') {
                                break;
                            }
                            if (ch >= 'a' && ch <= 'f') {
                                ch -= ('a' - 10);
                            } else if (ch >= 'A' && ch <= 'F') {
                                ch -= ('A' - 10);
                            } else if (ch >= '0' && ch <= '9') {
                                ch -= '0';
                            } else {
                                continue;
                            }
                            blocklen = (blocklen * 16) + ch;
                        }
                        // Zero length is end of chunks
                        if (blocklen == 0) {
                            break;
                        }
                        // Ok, now read new chunk
                        int newlen = length + blocklen;
                        byte[] newdata = new byte[newlen];
                        if (length > 0) {
                            System.arraycopy(data, 0, newdata, 0, length);
                        }
                        dataIn.readFully(newdata, length, blocklen);
                        data = newdata;
                        length = newlen;
                        // And now the CRLF after the chunk;
                        while (dataIn.readByte() != '\n') {
                            ;
                        }
                    }
                    headerCache.setHeader("Content-Length", Integer.toString(length));
                } else {
                    if (outStream != null) {
                        HTTPUtil.sendHTTPResponse(outStream, HttpURLConnection.HTTP_LENGTH_REQUIRED, null);
                    }
                    throw new IOException("Transfer-Encoding unimplemented: " + transfer_encoding);
                }
            } else {
                return null;
            }
        } else {
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
    public static byte[] readData(InputStream inStream, OutputStream outStream, Message msg) throws IOException, MessagingException {
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
                logger.error("Inbound HTTP request does not provide means to determine data length: " + request.get(0) + " " + request.get(1) + "\n\tHeaders: " + printHeaders(msg.getHeaders().getAllHeaders(), "==", ";;"));
                throw new IOException("Content-Length missing and no \"Transfer-Encoding\" header found to determine how to read message body.");
            }
        }
        cleanIdHeaders(msg.getHeaders());
        return data;
    }

    /**
     * Cleans specific headers to ensure AS2 compatibility
     *
     * @param hdrs Headers to be cleaned
     */
    public static void cleanIdHeaders(InternetHeaders hdrs) {
        // Handle the case where the AS2 ID could be encapsulated in double quotes per RFC4130
        // some AS2 applications will send the quoted AND the unquoted ID so need
        String[] idHeaders = {"AS2-From", "AS2-To"};
        for (int i = 0; i < idHeaders.length; i++) {
            // Target only the first entry if there is more than one to get a single value
            String value = StringUtil.removeDoubleQuotes(hdrs.getHeader(idHeaders[i], null));
            // Delete all headers with the same key
            hdrs.removeHeader(idHeaders[i]);
            // Add back as a single value without quotes
            hdrs.setHeader(idHeaders[i], value);
        }
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
            throw new IOException("Invalid HTTP Request: Token Count - " + tokenCount + "::: String length - " + strBuf.length() + " ::: String - " + strBuf.toString());
        }
    }


    /**
     * Execute a request via HTTP
     *
     * @param method         GET, PUT, POST, DELETE, etc
     * @param url            The remote connection string
     * @param headers        HTTP headers to be sent
     * @param params         Parameters for the get. Can be null.
     * @param inputStream    Source stream for retrieving request data
     * @param options        Any additional options for affecting request behaviour. Can NOT be null.
     * @param noChunkMaxSize The maximum size before chunking would need to be utilised. 0 disables check for chunking
     * @return ResponseWrapper
     * @throws Exception
     */
    public static ResponseWrapper execRequest(String method, String url, Enumeration<Header> headers, NameValuePair[] params, InputStream inputStream, Map<String, String> options, long noChunkMaxSize) throws Exception {

        HttpClientBuilder httpBuilder = HttpClientBuilder.create();
        URL urlObj = new URL(url);
        /*
         * httpClient is used for this request only,
         * set a connection manager that manages just one connection.
         */
        if (urlObj.getProtocol().equalsIgnoreCase("https")) {
            /*
             * Note: registration of a custom SSLSocketFactory via httpBuilder.setSSLSocketFactory is ignored when a connection manager is set.
             * The custom SSLSocketFactory needs to be registered together with the connection manager.
             */
            SSLConnectionSocketFactory sslCsf = buildSslFactory(urlObj, options);
            httpBuilder.setConnectionManager(new BasicHttpClientConnectionManager(RegistryBuilder.<ConnectionSocketFactory>create().register("http", PlainConnectionSocketFactory.getSocketFactory()).register("https", sslCsf).build()));
        } else {
            httpBuilder.setConnectionManager(new BasicHttpClientConnectionManager());
        }

        RequestBuilder rb = getRequestBuilder(method, urlObj, params, headers);
        RequestConfig.Builder rcBuilder = buildRequestConfig(options);
        setProxyConfig(httpBuilder, rcBuilder, urlObj.getProtocol());
        rb.setConfig(rcBuilder.build());

        String httpUser = options.get(HTTPUtil.PARAM_HTTP_USER);
        String httpPwd = options.get(HTTPUtil.PARAM_HTTP_PWD);
        if (httpUser != null) {
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(httpUser, httpPwd));
            httpBuilder.setDefaultCredentialsProvider(credentialsProvider);
        }

        if (inputStream != null) {
            if (noChunkMaxSize > 0L) {
                ByteArrayOutputStream bout = new ByteArrayOutputStream();
                long copied = IOUtils.copyLarge(inputStream, bout, 0L, noChunkMaxSize + 1, new byte[8192]);
                if (copied > noChunkMaxSize) {
                    throw new IOException("Mime inputstream too big to put in memory (more than " + noChunkMaxSize + " bytes).");
                }
                ByteArrayEntity bae = new ByteArrayEntity(bout.toByteArray(), null);
                rb.setEntity(bae);
            } else {
                InputStreamEntity ise = new InputStreamEntity(inputStream);
                // Use a BufferedEntity for BasicAuth connections to avoid the NonRepeatableRequestExceotion
                if (httpUser != null) {
                    rb.setEntity(new BufferedHttpEntity(ise));
                } else {
                    rb.setEntity(ise);
                }
            }
        }
        final HttpUriRequest request = rb.build();

        BasicHttpContext localcontext = new BasicHttpContext();
        BasicScheme basicAuth = new BasicScheme();
        localcontext.setAttribute("preemptive-auth", basicAuth);
        try (CloseableHttpClient httpClient = httpBuilder.build()) {
            ProfilerStub transferStub = Profiler.startProfile();
            try (CloseableHttpResponse response = httpClient.execute(request, localcontext)) {
                ResponseWrapper resp = new ResponseWrapper(response);
                Profiler.endProfile(transferStub);
                resp.setTransferTimeMs(transferStub.getMilliseconds());
                for (org.apache.http.Header header : response.getAllHeaders()) {
                    resp.addHeaderLine(header.toString());
                }
                return resp;
            }
        }
    }

    private static SSLConnectionSocketFactory buildSslFactory(URL urlObj, Map<String, String> options) throws Exception {

        boolean overrideSslChecks = "true".equalsIgnoreCase(options.get(HTTPUtil.HTTP_PROP_OVERRIDE_SSL_CHECKS));
        SSLContext sslcontext;
        String selfSignedCN = System.getProperty("org.openas2.cert.TrustSelfSignedCN");
        if ((selfSignedCN != null && selfSignedCN.contains(urlObj.getHost())) || overrideSslChecks) {
            File file = getTrustedCertsKeystore();
            KeyStore ks = null;
            try (InputStream in = new FileInputStream(file)) {
                ks = KeyStore.getInstance(KeyStore.getDefaultType());
                ks.load(in, "changeit".toCharArray());
            }
            try {
                // Trust own CA and all self-signed certs
                sslcontext = SSLContexts.custom().loadTrustMaterial(ks, new TrustSelfSignedStrategy()).build();
                // Allow TLSv1 protocol only by default
            } catch (Exception e) {
                throw new OpenAS2Exception("Self-signed certificate URL connection failed connecting to : " + urlObj.toString(), e);
            }
        } else {
            sslcontext = SSLContexts.createSystemDefault();
        }
        // String [] protocols = Properties.getProperty(HTTP_PROP_SSL_PROTOCOLS,
        // "TLSv1").split("\\s*,\\s*");
        HostnameVerifier hnv = SSLConnectionSocketFactory.getDefaultHostnameVerifier();
        if (overrideSslChecks) {
            hnv = new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };
        }
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslcontext, null, null, hnv);
        return sslsf;
    }

    private static RequestBuilder getRequestBuilder(String method, URL urlObj, NameValuePair[] params, Enumeration<Header> headers) throws URISyntaxException {

        RequestBuilder req = null;
        if (method == null || method.equalsIgnoreCase(Method.GET)) {
            //default get
            req = RequestBuilder.get();
        } else if (method.equalsIgnoreCase(Method.POST)) {
            req = RequestBuilder.post();
        } else if (method.equalsIgnoreCase(Method.HEAD)) {
            req = RequestBuilder.head();
        } else if (method.equalsIgnoreCase(Method.PUT)) {
            req = RequestBuilder.put();
        } else if (method.equalsIgnoreCase(Method.DELETE)) {
            req = RequestBuilder.delete();
        } else if (method.equalsIgnoreCase(Method.TRACE)) {
            req = RequestBuilder.trace();
        } else {
            throw new IllegalArgumentException("Illegal HTTP Method: " + method);
        }
        req.setUri(urlObj.toURI());
        if (params != null && params.length > 0) {
            req.addParameters(params);
        }
        if (headers != null) {
            boolean removeHeaderFolding = "true".equals(Properties.getProperty(HTTP_PROP_REMOVE_HEADER_FOLDING, "true"));
            while (headers.hasMoreElements()) {
                Header header = headers.nextElement();
                String headerValue = header.getValue();
                if (removeHeaderFolding) {
                    headerValue = headerValue.replaceAll("\r\n[ \t]*", " ");
                }
                req.setHeader(header.getName(), headerValue);
            }
        }
        return req;
    }

    private static RequestConfig.Builder buildRequestConfig(Map<String, String> options) {

        String connectTimeOutStr = options.get(PARAM_CONNECT_TIMEOUT);
        String socketTimeOutStr = options.get(PARAM_SOCKET_TIMEOUT);
        RequestConfig.Builder rcBuilder = RequestConfig.custom();
        if (connectTimeOutStr != null) {
            rcBuilder.setConnectTimeout(Integer.parseInt(connectTimeOutStr));
        }
        if (socketTimeOutStr != null) {
            rcBuilder.setSocketTimeout(Integer.parseInt(socketTimeOutStr));
        }
        return rcBuilder;
    }

    /*
     * Sends an HTTP response on the connection passed as a parameter with the
     * specified response code. If there are headers in the enumeration then it will
     * send the headers
     *
     * @param out The HTTP output stream
     *
     * @param responsCode The HTTP response code to be sent
     *
     * @param data Data if any. Can be null
     *
     * @param headers Headers if any to be sent
     */
    public static void sendHTTPResponse(OutputStream out, int responseCode, ByteArrayOutputStream data, Enumeration<String> headers) throws IOException {
        StringBuffer httpResponse = new StringBuffer();
        httpResponse.append(responseCode).append(" ");
        httpResponse.append(HTTPUtil.getHTTPResponseMessage(responseCode));
        httpResponse.append("\r\n");
        StringBuffer response = new StringBuffer("HTTP/1.1 ");
        response.append(httpResponse);
        out.write(response.toString().getBytes());
        String header;

        if (headers != null) {
            boolean removeHeaderFolding = "true".equals(Properties.getProperty("remove_http_header_folding", "true"));
            while (headers.hasMoreElements()) {
                header = headers.nextElement();
                // Support
                // https://tools.ietf.org/html/draft-ietf-httpbis-p1-messaging-13#section-3.2
                if (removeHeaderFolding) {
                    header = header.replaceAll("\r\n[ \t]*", " ");
                }
                out.write((header + "\r\n").getBytes());
            }
        }

        if (data == null || data.size() < 1) {
            // if no data will be sent, write the HTTP code or zero Content-Length
            boolean sendHttpCodeAsString = "true".equals(Properties.getProperty("send_http_code_as_string_when_no_data", "true"));
            if (sendHttpCodeAsString) {
                byte[] responseCodeBytes = httpResponse.toString().getBytes();
                out.write(("Content-Length: " + responseCodeBytes.length + "\r\n\r\n").getBytes());
                out.write(responseCodeBytes);
            } else {
                out.write("Content-Length: 0\r\n\r\n".getBytes());
            }

        } else {
            out.write(("\r\n").getBytes()); //Add null line before body per RFC822
            data.writeTo(out);
        }
        out.flush();
    }

    /*
     * Sends an HTTP response on the connection passed as a parameter with the
     * specified response code.
     *
     * @param out The HTTP output stream
     *
     * @param responsCode The HTTP response code to be sent
     *
     * @param data Data if any. Can be null
     */
    public static void sendHTTPResponse(OutputStream out, int responseCode, String data) throws IOException {
        ByteArrayOutputStream dataOS = null;
        if (data != null) {
            dataOS = new ByteArrayOutputStream();
            dataOS.write(data.getBytes());
        }

        sendHTTPResponse(out, responseCode, dataOS, null);
    }

    public static String printHeaders(Enumeration<Header> hdrs, String nameValueSeparator, String valuePairSeparator) {
        String headers = "";
        while (hdrs.hasMoreElements()) {
            Header h = hdrs.nextElement();
            headers = headers + valuePairSeparator + h.getName() + nameValueSeparator + h.getValue();
        }

        return (headers);

    }

    public static File getTrustedCertsKeystore() throws OpenAS2Exception {
        File file = new File("jssecacerts");
        if (!file.isFile()) {
            char SEP = File.separatorChar;
            File dir = new File(System.getProperty("java.home") + SEP + "lib" + SEP + "security");
            /* Check if this is a JDK home */
            if (!dir.isDirectory()) {
                dir = new File(System.getProperty("java.home") + SEP + "jre" + SEP + "lib" + SEP + "security");
            }
            if (!dir.isDirectory()) {
                throw new OpenAS2Exception("The JSSE folder could not be identified. Please check that JSSE is installed.");
            }
            file = new File(dir, "jssecacerts");
            if (file.isFile() == false) {
                file = new File(dir, "cacerts");
            }
        }
        return file;
    }

    public static String getParamsString(Map<String, String> params) throws UnsupportedEncodingException {
        StringBuilder result = new StringBuilder();

        for (Map.Entry<String, String> entry : params.entrySet()) {
            result.append(URLEncoder.encode(entry.getKey(), "UTF-8"));
            result.append("=");
            result.append(URLEncoder.encode(entry.getValue(), "UTF-8"));
            result.append("&");
        }

        String resultString = result.toString();
        return resultString.length() > 0 ? resultString.substring(0, resultString.length() - 1) : resultString;
    }

    public static boolean isLocalhostBound(InetAddress addr) {
        // Check if the address is a valid special local or loop back
        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress()) {
            return true;
        }

        // Check if the address is defined on any interface
        try {
            return NetworkInterface.getByInetAddress(addr) != null;
        } catch (SocketException e) {
            return false;
        }
    }

    /**
     * @param url
     * @param output
     * @param input
     * @param useCaches
     * @param requestMethod
     * @return
     * @throws OpenAS2Exception
     * @deprecated Use post method to send messages
     */
    public static HttpURLConnection getConnection(String url, boolean output, boolean input, boolean useCaches, String requestMethod) throws OpenAS2Exception {
        if (url == null) {
            throw new OpenAS2Exception("HTTP getConnection method received empty URL string.");
        }
        try {
            initializeProxyAuthenticator();
            HttpURLConnection conn;
            URL urlObj = new URL(url);
            if (urlObj.getProtocol().equalsIgnoreCase("https")) {
                HttpsURLConnection connS = (HttpsURLConnection) urlObj.openConnection(getProxy("https"));
                String selfSignedCN = System.getProperty("org.openas2.cert.TrustSelfSignedCN");
                if (selfSignedCN != null) {
                    File file = new File("jssecacerts");
                    if (file.isFile() == false) {
                        char SEP = File.separatorChar;
                        File dir = new File(System.getProperty("java.home") + SEP + "lib" + SEP + "security");
                        /* Check if this is a JDK home */
                        if (!dir.isDirectory()) {
                            dir = new File(System.getProperty("java.home") + SEP + "jre" + SEP + "lib" + SEP + "security");
                        }
                        if (!dir.isDirectory()) {
                            throw new OpenAS2Exception("The JSSE folder could not be identified. Please check that JSSE is installed.");
                        }
                        file = new File(dir, "jssecacerts");
                        if (file.isFile() == false) {
                            file = new File(dir, "cacerts");
                        }
                    }
                    InputStream in = new FileInputStream(file);
                    try {
                        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
                        ks.load(in, "changeit".toCharArray());
                        in.close();
                        SSLContext context = SSLContext.getInstance("TLS");
                        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
                        tmf.init(ks);
                        X509TrustManager defaultTrustManager = (X509TrustManager) tmf.getTrustManagers()[0];
                        SelfSignedTrustManager tm = new SelfSignedTrustManager(defaultTrustManager);
                        tm.setTrustCN(selfSignedCN);
                        context.init(null, new TrustManager[]{tm}, null);
                        connS.setSSLSocketFactory(context.getSocketFactory());
                    } catch (Exception e) {
                        throw new OpenAS2Exception("Self-signed certificate URL connection failed connecting to : " + url, e);
                    }
                }
                conn = connS;
            } else {
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

    private static void setProxyConfig(HttpClientBuilder builder, RequestConfig.Builder rcBuilder, String protocol) throws OpenAS2Exception {
        String proxyHost = Properties.getProperty(protocol + ".proxyHost", null);
        if (proxyHost == null) {
            proxyHost = System.getProperty(protocol + ".proxyHost");
        }
        if (proxyHost == null) {
            return;
        }
        String proxyPort = Properties.getProperty(protocol + ".proxyPort", null);
        if (proxyPort == null) {
            proxyPort = System.getProperty(protocol + ".proxyPort");
        }
        if (proxyPort == null) {
            throw new OpenAS2Exception("Missing PROXY port since Proxy host is set");
        }
        int port = Integer.parseInt(proxyPort);
        HttpHost proxy = new HttpHost(proxyHost, port);

        rcBuilder.setProxy(proxy);

        String proxyUser1 = Properties.getProperty("http.proxyUser", null);
        final String proxyUser = proxyUser1 == null ? System.getProperty("http.proxyUser") : proxyUser1;
        if (proxyUser == null) {
            return;
        }
        String proxyPwd1 = Properties.getProperty("http.proxyPassword", null);
        final String proxyPassword = proxyPwd1 == null ? System.getProperty("http.proxyPassword") : proxyPwd1;
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(proxyHost, port), new UsernamePasswordCredentials(proxyUser, proxyPassword));
        builder.setDefaultCredentialsProvider(credsProvider);
    }

    /**
     * @param protocol
     * @return
     * @throws OpenAS2Exception
     * @deprecated - use post method to send messages
     */
    private static Proxy getProxy(String protocol) throws OpenAS2Exception {
        String proxyHost = Properties.getProperty(protocol + ".proxyHost", null);
        if (proxyHost == null) {
            proxyHost = System.getProperty(protocol + ".proxyHost");
        }
        if (proxyHost == null) {
            return Proxy.NO_PROXY;
        }
        String proxyPort = Properties.getProperty(protocol + ".proxyPort", null);
        if (proxyPort == null) {
            proxyPort = System.getProperty(protocol + ".proxyPort");
        }
        if (proxyPort == null) {
            throw new OpenAS2Exception("Missing PROXY port since Proxy host is set");
        }
        int port = Integer.parseInt(proxyPort);
        return new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, port));

    }

    /**
     * @deprecated - use post method to send messages
     */
    private static void initializeProxyAuthenticator() {
        String proxyUser1 = Properties.getProperty("http.proxyUser", null);
        final String proxyUser = proxyUser1 == null ? System.getProperty("http.proxyUser") : proxyUser1;
        String proxyPwd1 = Properties.getProperty("http.proxyPassword", null);
        final String proxyPassword = proxyPwd1 == null ? System.getProperty("http.proxyPassword") : proxyPwd1;

        if (proxyUser != null && proxyPassword != null) {
            Authenticator.setDefault(new Authenticator() {
                public PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(proxyUser, proxyPassword.toCharArray());
                }
            });
        }
    }

    // Copy headers from an Http connection to an InternetHeaders object
    public static void copyHttpHeaders(HttpURLConnection conn, InternetHeaders headers) {
        Iterator<Map.Entry<String, List<String>>> connHeadersIt = conn.getHeaderFields().entrySet().iterator();
        Iterator<String> connValuesIt;
        Map.Entry<String, List<String>> connHeader;
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
                    } else {
                        // Avoid duplicates of the same value since headers that exist in the HTTP
                        // headers
                        // may already have been inserted in the Message object
                        boolean exists = false;
                        for (int i = 0; i < existingVals.length; i++) {
                            if (value.equals(existingVals[i])) {
                                exists = true;
                            }
                        }
                        if (!exists) {
                            headers.addHeader(headerName, value);
                        }
                    }
                }
            }
        }
    }

    private static class SelfSignedTrustManager implements X509TrustManager {

        private final X509TrustManager tm;
        private String[] trustCN = null;

        SelfSignedTrustManager(X509TrustManager tm) {
            this.tm = tm;
        }

        public X509Certificate[] getAcceptedIssuers() {
            return tm.getAcceptedIssuers();
        }

        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            throw new UnsupportedOperationException();
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            if (chain.length == 1) {
                // Only ignore the check for self signed certs where CN (Canonical Name) matches
                String dn = chain[0].getIssuerDN().getName();
                for (int i = 0; i < trustCN.length; i++) {
                    if (dn.contains("CN=" + trustCN[i])) {
                        return;
                    }
                }
            }
            tm.checkServerTrusted(chain, authType);
        }

        public void setTrustCN(String trustCN) {
            this.trustCN = trustCN.split(",");
        }

    }
}
