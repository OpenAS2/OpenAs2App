package org.openas2.processor.receiver;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.WrappedException;
import org.openas2.message.InvalidMessageException;
import org.openas2.message.Message;
import org.openas2.params.CompositeParameters;
import org.openas2.params.DateParameters;
import org.openas2.params.InvalidParameterException;
import org.openas2.params.MessageParameters;
import org.openas2.util.HTTPUtil;
import org.openas2.util.IOUtil;
import org.openas2.util.Properties;
import org.openas2.util.ResponseWrapper;

import javax.annotation.Nullable;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.apache.commons.lang3.StringUtils.defaultIfBlank;


public abstract class NetModule extends BaseReceiverModule {
    public static final String PARAM_ADDRESS = "address";
    public static final String PARAM_PORT = "port";
    public static final String PARAM_PROTOCOL = "protocol";
    public static final String PARAM_SSL_KEYSTORE = "ssl_keystore";
    public static final String PARAM_SSL_KEYSTORE_PASSWORD = "ssl_keystore_password";
    public static final String PARAM_SSL_PROTOCOL = "ssl_protocol";
    public static final String PARAM_ERROR_DIRECTORY = "errordir";
    public static final String PARAM_ERRORS = "errors";
    public static final String DEFAULT_ERRORS = "$date.yyyyMMddhhmmss$";

    private HTTPServerThread mainThread;
    private Log logger = LogFactory.getLog(NetModule.class.getSimpleName());

    public void doStart() throws OpenAS2Exception {
        try {
            mainThread = new HTTPServerThread(this, getParameter(PARAM_ADDRESS, false), getParameterInt(PARAM_PORT, true));
            mainThread.start();
        } catch (IOException ioe) {
            String host = getParameter(PARAM_ADDRESS, false);
            if (host == null || host.length() < 1) {
                host = "localhost";
            }
            logger.error("Error in HTTP connection starting server thread on host::port: " + host + "::" + getParameterInt(PARAM_PORT, true), ioe);
            throw new WrappedException(ioe);
        }
    }

    public void doStop() throws OpenAS2Exception {
        if (mainThread != null) {
            mainThread.terminate();
            mainThread = null;
        }
    }

    public void init(Session session, Map<String, String> options) throws OpenAS2Exception {
        super.init(session, options);
        getParameter(PARAM_PORT, true);
        // Override the password if it was passed as a system property
        String pwd = System.getProperty("org.openas2.ssl.Password");
        if (pwd != null) {
            setParameter(PARAM_SSL_KEYSTORE_PASSWORD, pwd);
        }

    }

    @Override
    public boolean healthcheck(List<String> failures) {
        try {
            String hcHost = getParameter(PARAM_ADDRESS, Properties.getProperty("ssl_host_name", "localhost"));
            String hcPort = getParameter(PARAM_PORT, true);
            String hcProtocol = getParameter(PARAM_PROTOCOL, "http");
            String urlString = hcProtocol + "://" + hcHost + ":" + hcPort + "/" + Properties.getProperty("health_check_uri", "healthcheck");

            if (logger.isTraceEnabled()) {
                logger.trace("Helthcheck about to try URL: " + urlString);
            }
            Map<String, String> options = new HashMap<String, String>();
            options.put(HTTPUtil.HTTP_PROP_OVERRIDE_SSL_CHECKS, "true");
            ResponseWrapper rw = HTTPUtil.execRequest(HTTPUtil.Method.GET, urlString, null, null, null, options, 0L);
            if (200 != rw.getStatusCode()) {
                failures.add(this.getClass().getSimpleName() + " - Error making HTTP connection. Response code: " + rw.getStatusCode() + " " + rw.getStatusPhrase());
                return false;
            }
        } catch (Exception e) {
            logger.error("Failed to execute healthcheck.", e);
            failures.add(this.getClass().getSimpleName() + " - Failed to execute HTTP connection to listener: " + e.getMessage());
            return false;
        }
        return true;
    }

    protected abstract NetModuleHandler getHandler();

    protected void handleError(Message msg, OpenAS2Exception oae) {
        oae.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
        oae.terminate();

        try {
            CompositeParameters params = new CompositeParameters(false).
                add("date", new DateParameters()).
                add("msg", new MessageParameters(msg));

            String name = params.format(getParameter(PARAM_ERRORS, DEFAULT_ERRORS));
            String directory = getParameter(PARAM_ERROR_DIRECTORY, true);

            File msgFile = IOUtil.getUnique(IOUtil.getDirectoryFile(directory), IOUtil.cleanFilename(name));
            String msgText = msg.toString();
            FileOutputStream fOut = new FileOutputStream(msgFile);

            fOut.write(msgText.getBytes());
            fOut.close();

            // make sure an error of this event is logged
            InvalidMessageException im = new InvalidMessageException("Stored invalid message to " + msgFile.getAbsolutePath());
            im.terminate();
        } catch (OpenAS2Exception oae2) {
            oae2.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
            oae2.terminate();
        } catch (IOException ioe) {
            WrappedException we = new WrappedException(ioe);
            we.addSource(OpenAS2Exception.SOURCE_MESSAGE, msg);
            we.terminate();
        }
    }

    protected class ConnectionHandler implements Runnable {
        private final NetModule owner;
        private final Socket socket;

        public ConnectionHandler(NetModule owner, Socket socket) {
            this.owner = owner;
            this.socket = socket;
        }

        public NetModule getOwner() {
            return owner;
        }

        public Socket getSocket() {
            return socket;
        }

        @Override
        public void run() {
            Socket s = getSocket();

            try {
                getOwner().getHandler().handle(getOwner(), s);
            } finally {
                try {
                    s.close();
                } catch (IOException sce) {
                    new WrappedException(sce).terminate();
                }
            }
        }
    }

    protected class HTTPServerThread extends Thread {
        private final NetModule owner;
        private final ServerSocket socket;
        private final ExecutorService connectionThreads;
        private final AtomicBoolean terminated = new AtomicBoolean();

        HTTPServerThread(NetModule owner, @Nullable String address, int port) throws IOException {
            super(ClassUtils.getSimpleName(HTTPServerThread.class) + " (" + defaultIfBlank(address, "0.0.0.0") + ":" + port + ")");
            this.owner = owner;
            String protocol = "http";
            String sslProtocol = "TLS";
            try {
                protocol = owner.getParameter(PARAM_PROTOCOL, "http");
                sslProtocol = owner.getParameter(PARAM_SSL_PROTOCOL, "TLS");
            } catch (InvalidParameterException e) {
                // Do nothing
            }
            if ("https".equalsIgnoreCase(protocol)) {
                String ksName;
                char[] ksPass;
                try {
                    ksName = owner.getParameter(PARAM_SSL_KEYSTORE, true);
                    ksPass = owner.getParameter(PARAM_SSL_KEYSTORE_PASSWORD, true).toCharArray();
                } catch (InvalidParameterException e) {
                    logger.error("Required SSL parameter missing.", e);
                    throw new IOException("Failed to retireve require SSL parameters. Check config XML");
                }
                KeyStore ks;
                try {
                    ks = KeyStore.getInstance("JKS");
                } catch (KeyStoreException e) {
                    logger.error("Failed to initialise SSL keystore.", e);
                    throw new IOException("Error initialising SSL keystore");
                }
                try {
                    ks.load(new FileInputStream(ksName), ksPass);
                } catch (NoSuchAlgorithmException e) {
                    logger.error("Failed to load keystore: " + ksName, e);
                    throw new IOException("Error loading SSL keystore");
                } catch (CertificateException e) {
                    logger.error("Failed to load SSL certificate: " + ksName, e);
                    throw new IOException("Error loading SSL certificate");
                }
                KeyManagerFactory kmf;
                try {
                    kmf = KeyManagerFactory.getInstance("SunX509");
                } catch (NoSuchAlgorithmException e) {
                    logger.error("Failed to create key manager instance", e);
                    throw new IOException("Error creating SSL key manager instance");
                }
                try {
                    kmf.init(ks, ksPass);
                } catch (Exception e) {
                    logger.error("Failed to initialise key manager instance", e);
                    throw new IOException("Error initialising SSL key manager instance");
                }
                // setup the trust manager factory
                TrustManagerFactory tmf;
                try {
                    tmf = TrustManagerFactory.getInstance("SunX509");
                    tmf.init(ks);
                } catch (Exception e1) {
                    logger.error("Failed to create trust manager instance", e1);
                    throw new IOException("Error creating SSL trust manager instance");
                }
                SSLContext sc;
                try {
                    sc = SSLContext.getInstance(sslProtocol);
                } catch (NoSuchAlgorithmException e) {
                    logger.error("Failed to create SSL context instance", e);
                    throw new IOException("Error creating SSL context instance");
                }
                try {
                    sc.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
                } catch (KeyManagementException e) {
                    logger.error("Failed to initialise SSL context instance", e);
                    throw new IOException("Error initialising SSL context instance");
                }
                SSLServerSocketFactory ssf = sc.getServerSocketFactory();
                if (address != null) {
                    socket = ssf.createServerSocket(port, 0, InetAddress.getByName(address));
                } else {
                    socket = ssf.createServerSocket(port);
                }
            } else {
                socket = new ServerSocket();
                if (address != null) {
                    socket.bind(new InetSocketAddress(address, port));
                } else {
                    socket.bind(new InetSocketAddress(port));
                }
            }
            connectionThreads = Executors.newCachedThreadPool();
        }

        NetModule getOwner() {
            return owner;
        }

        public ServerSocket getSocket() {
            return socket;
        }

        public boolean isTerminated() {
            return terminated.get();
        }

        public void terminate() {
            if (!terminated.compareAndSet(false, true) || socket == null) {
                return;
            }
            try {
                socket.close();
            } catch (IOException e) {
                owner.forceStop(e);
            }
            connectionThreads.shutdown();
        }

        @Override
        public void run() {
            while (!isTerminated()) {
                try {
                    Socket conn = socket.accept();
                    conn.setSoLinger(true, 60);
                    connectionThreads.execute(new ConnectionHandler(getOwner(), conn));
                } catch (IOException e) {
                    if (!isTerminated()) {
                        owner.forceStop(e);
                    }
                }
            }
        }


    }
}
