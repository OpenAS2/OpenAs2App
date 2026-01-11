package org.openas2.util;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.apache.http.entity.AbstractHttpEntity;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.BasicHttpClientConnectionManager;
import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HTTP;
import org.apache.http.ssl.SSLContexts;
import org.openas2.OpenAS2Exception;
import org.openas2.message.Message;
import org.openas2.processor.sender.HttpSenderModule;

import jakarta.mail.Header;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetHeaders;
import javax.net.ssl.*;
import java.io.*;
import java.net.*;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


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

    public static final String SSL_KEYSTORE_PATH_ENV = "SSL_KEYSTORE_PATH";
    public static final String SSL_KEYSTORE_PASSWORD_ENV = "SSL_KEYSTORE_PASSWORD";
    private static Set<String> cachedFingerprints = ConcurrentHashMap.newKeySet();
    private static KeyStore cachedJavaKeyStore = null;

    private static final Logger LOG = LoggerFactory.getLogger(HTTPUtil.class);

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
        Logger logger = LoggerFactory.getLogger(HTTPUtil.class);

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
        if (headerCache.getHeader(HTTP.CONTENT_LEN) == null) {
            String transfer_encoding = headerCache.getHeader(HTTP.TRANSFER_ENCODING, ",");

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
            if (logger.isTraceEnabled()) {
                logger.trace("Reading fixed byte count from HTTP stream based on Content-Length: " + contentSize + " - Receiver will wait until full byte count is received unless an IO exception is triggered....");
            }
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
                if ("true".equals(Properties.getProperty(Properties.LOG_INVALID_HTTP_REQUEST, "true"))) {
                  Logger logger = LoggerFactory.getLogger(HTTPUtil.class);
                  logger.warn("The request either contained no data or has issues with the Transfer-Encoding or Content-Length: : " + request.get(0) + " " + request.get(1) + "\n\tHeaders: " + printHeaders(msg.getHeaders().getAllHeaders(), "==", ";;"));
                }
                return null;
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
    public static ResponseWrapper execRequest(
            String method,
            String url,
            InternetHeaders headers,
            NameValuePair[] params,
            InputStream inputStream,
            Map<String, Object> options,
            long noChunkMaxSize,
            boolean preventChunking) throws Exception {

        HttpClientBuilder httpBuilder = HttpClientBuilder.create();
        //org.apache.http.protocol.RequestContent
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
            httpBuilder.setConnectionManager(
                    new BasicHttpClientConnectionManager(
                            RegistryBuilder.<ConnectionSocketFactory>create()
                            .register("http", PlainConnectionSocketFactory.getSocketFactory())
                            .register("https", sslCsf).build()
                    )
            );
        } else {
            httpBuilder.setConnectionManager(new BasicHttpClientConnectionManager());
        }

        // Check if Content-Length was added and remove it so it as it is managed by the HttpRequest when processing the entity
        long contentLength = -1; // Initialise as unknown
        String[] contentLengthValues = headers==null?null:headers.getHeader(HTTP.CONTENT_LEN);
        if (contentLengthValues != null && contentLengthValues.length > 0) {
            contentLength = Long.parseLong(contentLengthValues[0]);
            headers.removeHeader(HTTP.CONTENT_LEN);
        }
        RequestBuilder rb = getRequestBuilder(method, urlObj, params, headers);
        RequestConfig.Builder rcBuilder = buildRequestConfig(options);
        setProxyConfig(httpBuilder, urlObj.getProtocol());
        rb.setConfig(rcBuilder.build());

        String httpUser = (String) options.get(HTTPUtil.PARAM_HTTP_USER);
        if (httpUser != null) {
            String httpPwd = (String) options.get(HTTPUtil.PARAM_HTTP_PWD);
            CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
            credentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(httpUser, httpPwd));
            httpBuilder.setDefaultCredentialsProvider(credentialsProvider);
        }
        if (inputStream != null) {
            AbstractHttpEntity httpEntity = new InputStreamEntity(inputStream, contentLength);
            // the default is to use chunking for transfer encoding - allow override
            if (preventChunking) {
                if (noChunkMaxSize > 0L) {
                    // There is a maximum size of the content that a partner receiver can accept
                    if (contentLength == -1) {
                        // Not set as a header so do it the compute expensive way
                        ByteArrayOutputStream bout = new ByteArrayOutputStream();
                        contentLength = IOUtils.copyLarge(inputStream, bout, 0L, noChunkMaxSize + 1, new byte[8192]);
                        if (contentLength > noChunkMaxSize) {
                            throw new IOException("Data inputstream too big to put in memory (more than " + noChunkMaxSize + " bytes).");
                        }
                        httpEntity = new ByteArrayEntity(bout.toByteArray(), null);
                    }
                }
                // Tell the HTTP client to try to send unchunked - the Content-Length will be extracted from the entity
                httpEntity.setChunked(false);
            }
            // Use a BufferedEntity for BasicAuth connections to avoid the NonRepeatableRequestException
            if (httpUser != null) {
                rb.setEntity(new BufferedHttpEntity(httpEntity));
            } else {
                rb.setEntity(httpEntity);
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

    private static SSLConnectionSocketFactory buildSslFactory(URL urlObj, Map<String, Object> options) throws Exception {
        // Support various ways of doing the connection trusting
        // There is a custom keystore to verify self signed certs against
        boolean isExtendedSelfsignedTrustCheck = options.containsKey(HttpSenderModule.PARAM_CUSTOM_SSL_TRUST_STORE);
        // this is only currently used by the Health check module
        boolean overrideSslChecks = "true".equalsIgnoreCase((String) options.get(HTTPUtil.HTTP_PROP_OVERRIDE_SSL_CHECKS));
        // The original method where hostnames to be trusted can be passed in a system property
        String selfSignedCN = System.getProperty("org.openas2.cert.TrustSelfSignedCN");
        boolean isTrustSelfSignedCNHandling = (selfSignedCN != null && selfSignedCN.contains(urlObj.getHost()))?true:false;

        if (LOG.isTraceEnabled()) {
            LOG.trace("SSL factory building values: isExtendedSelfsignedTrustCheck - {}  ::: overrideSslChecks - {} ::: isTrustSelfSignedCNHandling - {}", isExtendedSelfsignedTrustCheck, overrideSslChecks, isTrustSelfSignedCNHandling);
        }
        // Find a keystore to verify the self signed certs against if required
        // Even if TrustSelfSignedCN is passed, use the custom keystore if it is defined
        KeyStore selfsignedCertsKeystore = null;
        if (isExtendedSelfsignedTrustCheck) {
            selfsignedCertsKeystore = (KeyStore) options.get(HttpSenderModule.PARAM_CUSTOM_SSL_TRUST_STORE);
        } else if (isTrustSelfSignedCNHandling) {
            selfsignedCertsKeystore = getTrustedCertsKeystore();
        }

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        SSLContext sslcontext;

        if (selfsignedCertsKeystore != null) {
            try {
                // Trust own CA and all self-signed certs
                sslcontext = SSLContexts.custom().loadTrustMaterial(selfsignedCertsKeystore, new TrustSelfSignedStrategy()).build();
                if (LOG.isTraceEnabled()) {
                    LOG.trace("SSL context built using self signed trust store...");
                }
            } catch (Exception e) {
                throw new OpenAS2Exception("Attempted connection using self-signed manager failed connecting to : " + urlObj.toString(), e);
            }
        } else {
            sslcontext = SSLContexts.createSystemDefault();
        }
        // For normal SSL operation a null KeyStore passed in defaults to the Java trust store
        tmf.init(selfsignedCertsKeystore);
        HostnameVerifier hnv = null;

        if (overrideSslChecks) {
            hnv = new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            };
        } else if(selfsignedCertsKeystore != null) {
            SelfSignedTrustManager tm = new SelfSignedTrustManager((X509TrustManager) tmf.getTrustManagers()[0]);
            if (isTrustSelfSignedCNHandling) {
                tm.setTrustCN(selfSignedCN);
            }
            tm.setCustomSelfSignedHandling(isExtendedSelfsignedTrustCheck);
            tm.setCustomTrustKeyStore(selfsignedCertsKeystore);
            if(isExtendedSelfsignedTrustCheck) {
                hnv = new HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        try {
                            // Check if the certificate's fingerprint is cached or if it exists in the custom keystore
                            X509Certificate[] certs = (X509Certificate[]) session.getPeerCertificates();
                            String fingerprint = tm.getCertificateFingerprint(certs[0]);
            
                            if (cachedFingerprints.contains(fingerprint) || tm.isCertificateInCustomKeystore(certs[0], fingerprint)) {
                                LOG.info("Hostname verification skipped for trusted certificate: " + certs[0].getSubjectX500Principal().getName());
                                return true;
                            }
                        } catch (Exception e) {
                            LOG.error("Hostname verification failed: " + e.getMessage(), e);
                        }
    
                        // fallback to default hostname verifier
                        return SSLConnectionSocketFactory.getDefaultHostnameVerifier().verify(hostname, session);
                    }
                };
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory
                    .getDefaultAlgorithm());
            kmf.init(selfsignedCertsKeystore, null);
            // Now add the custom trust manager to the SSL context
            sslcontext.init(kmf.getKeyManagers(), new TrustManager[]{tm}, null);
        }
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslcontext, null, null, hnv);
        return sslsf;
    }

    private static RequestBuilder getRequestBuilder(String method, URL urlObj, NameValuePair[] params, InternetHeaders headers) throws URISyntaxException {

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
            Enumeration<Header> headerEnum = headers.getAllHeaders();
            while (headerEnum.hasMoreElements()) {
                Header header = headerEnum.nextElement();
                String headerValue = header.getValue();
                if (removeHeaderFolding) {
                    headerValue = headerValue.replaceAll("\r\n[ \t]*", " ");
                }
                req.setHeader(header.getName(), headerValue);
            }
        }
        return req;
    }

    private static RequestConfig.Builder buildRequestConfig(Map<String, Object> options) {

        String connectTimeOutStr = (String) options.get(PARAM_CONNECT_TIMEOUT);
        String socketTimeOutStr = (String) options.get(PARAM_SOCKET_TIMEOUT);
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

    public static KeyStore getTrustedCertsKeystore() throws OpenAS2Exception {
        if (cachedJavaKeyStore != null) {
            return cachedJavaKeyStore;
        }

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
        try (InputStream in = new FileInputStream(file)) {
            cachedJavaKeyStore = KeyStore.getInstance(KeyStore.getDefaultType());
            cachedJavaKeyStore.load(in, "changeit".toCharArray());
        } catch (Exception e) {
            throw new OpenAS2Exception("failed to load Java keystore file: " + file.getAbsolutePath(), e);
        }
        return cachedJavaKeyStore;
    }


    private static void setProxyConfig(HttpClientBuilder builder, String protocol) throws OpenAS2Exception {

        String proxyHostKey = protocol + ".proxyHost";
        String proxyPortKey = protocol + ".proxyPort";
        String proxyUserKey = protocol + ".proxyUser";
        String proxyPasswordKey = protocol + ".proxyPassword";
        String proxyNonProxyHostsKey = "http.nonProxyHosts";

        String proxyHost = getPropertyFromOpenAS2PropertiesOrSystem(proxyHostKey, null);
        String proxyPort = getPropertyFromOpenAS2PropertiesOrSystem(proxyPortKey, null);
        String proxyUser = getPropertyFromOpenAS2PropertiesOrSystem(proxyUserKey, null);
        String proxyPassword = getPropertyFromOpenAS2PropertiesOrSystem(proxyPasswordKey, null);
        String proxyNonProxyHosts = getPropertyFromOpenAS2PropertiesOrSystem(proxyNonProxyHostsKey, null);


        if (proxyHost == null) {
            // fast fail
            return;
        }
        if (proxyPort == null) {
            throw new OpenAS2Exception("Missing PROXY port since Proxy host is set");
        }

        // Set the system properties to ensure that the DefaultProxySelector uses this configuration
        // Note: That overwrites the system properties for the current JVM and the previously set values are lost
        System.setProperty(proxyHostKey, proxyHost);
        System.setProperty(proxyPortKey, proxyPort);
        System.setProperty(proxyNonProxyHostsKey, proxyNonProxyHosts);

        // Don't set an explicit proxy here or else "http.nonProxyHosts" won't be respected. 
        // Other proxy configurations like https.proxyHost will be read by the DefaultProxySelector.
        // See https://issues.apache.org/jira/browse/HTTPCLIENT-1617
        builder.setProxy(null);

        // Use the system default proxy selector which uses the System properties for proxy settings including http.nonProxyHosts
        SystemDefaultRoutePlanner routePlanner = new SystemDefaultRoutePlanner(ProxySelector.getDefault());
        builder.setRoutePlanner(routePlanner);

        if (proxyUser != null) {
            int port = Integer.parseInt(proxyPort);
            CredentialsProvider credsProvider = new BasicCredentialsProvider();
            credsProvider.setCredentials(new AuthScope(proxyHost, port), new UsernamePasswordCredentials(proxyUser, proxyPassword));
            builder.setDefaultCredentialsProvider(credsProvider);
        }
    }
    
    /**
     * @param key key like "http.proxyHost"
     * @param fallback default value like null
     * @return value from the properties file or as first fallback the value from the system properties or as second fallback the fallback value
     */
    private static String getPropertyFromOpenAS2PropertiesOrSystem(String key, String fallback) {
        String valueFromProperties = Properties.getProperty(key, fallback);
        String valueFromSystem = System.getProperty(key, fallback);
        // Prefer the value from the properties file then the system properties
        return valueFromProperties != null ? valueFromProperties : valueFromSystem;
    }

    private static class SelfSignedTrustManager implements X509TrustManager {

        private final X509TrustManager tm;
        private String[] trustCN = null;
        private KeyStore customTrustKeyStore = null;
        private boolean isExtendedSelfsignedTrustCheck = false;

        public void setCustomSelfSignedHandling(boolean isExtendedSelfsignedTrustCheck) {
            this.isExtendedSelfsignedTrustCheck = isExtendedSelfsignedTrustCheck;
        }

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
                // check if certificate is in the truststore or is cached - IF SSL_KEYSTORE_PATH && SSL_KEYSTORE PASSWORD ARE SET
                if(this.isExtendedSelfsignedTrustCheck) {

                    String fingerprint = getCertificateFingerprint(chain[0]);

                    // Check if fingerprint is already cached to avoid re-opening keystore
                    if (cachedFingerprints.contains(fingerprint)) {
                        LOG.info("Certificate validation passed (cached) for " + chain[0].getSubjectX500Principal().getName());
                        return;
                    }
            
                    // Proceed with custom keystore handling if not cached
                    if (isCertificateInCustomKeystore(chain[0], fingerprint)) {
                        LOG.info("Custom self-signed certificate validation passed for " + chain[0].getSubjectX500Principal().getName());
                        return;
                    }
                } else {
                    // Only ignore the check for self signed certs where CN (Canonical Name) matches - DEFAULT BEHAVIOUR
                    String dn = chain[0].getIssuerX500Principal().getName();
                    for (int i = 0; i < trustCN.length; i++) {
                        if (dn.contains("CN=" + trustCN[i])) {
                            return;
                        }
                    }
                }
            }
            tm.checkServerTrusted(chain, authType);
        }

        private String getCertificateFingerprint(X509Certificate cert) throws CertificateException {
            try {
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] fingerprintBytes = md.digest(cert.getEncoded());
                StringBuilder sb = new StringBuilder();
                for (byte b : fingerprintBytes) {
                    sb.append(String.format("%02X", b));
                }
                return sb.toString();
            } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
                throw new CertificateException("Failed to generate fingerprint for certificate", e);
            }
        }

        private boolean isCertificateInCustomKeystore(X509Certificate cert, String fingerprint) {
            try {
                String alias = this.customTrustKeyStore.getCertificateAlias(cert);
                if (alias != null) {
                    cachedFingerprints.add(fingerprint); // Cache the fingerprint
                    return true;
                }
            } catch (Exception e) {
                LOG.error("Failed to verify certificate in custom keystore: " + e.getMessage(), e);
            }
            return false;
        }

        public void setTrustCN(String trustCN) {
            this.trustCN = trustCN.split(",");
        }

        public void setCustomTrustKeyStore(KeyStore trustKeyStore) {
            this.customTrustKeyStore = trustKeyStore;
        }
    }
}
