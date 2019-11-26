import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.openas2.util.HTTPUtil;
import org.openas2.util.ResponseWrapper;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Class used to add the server's certificate to the KeyStore with your trusted
 * certificates.
 */
public class CheckCertificate {

    public static final String HOST = "s";
    public static final String PORT = "p";
    public static final String URI = "u";
    public static final String CACERT = "c";
    public static final String PASSWORD = "P";
    public static final String DEBUG = "d";
    public static final String AUTH_USER = "a";
    public static final String AUTH_PWD = "A";
    public static final String HELP_OPT = "h";

    private String auth_user = null;
    private String auth_pwd = null;
    /*
     * Options in this format: short-opt, long-opt, has-argument, required,
     * description
     */
    public String[][] opts = {{HOST, "server", "true", "true", "the target host name"}, {URI, "uri", "true", "false", "URI part of the connection"}, {PORT, "port", "true", "false", "target server port"}, {CACERT, "cacert", "true", "false", "Java keystore file to create if cert chain not present in Java keystore"}, {PASSWORD, "password", "true", "false", "password for Keystore if not 'changeit'"}, {DEBUG, "debug", "true", "false", "Enabling debug logging"}, {AUTH_USER, "authuser", "true", "false", "Basic auth user"}, {AUTH_PWD, "authpwd", "true", "false", "Basic auth password"}, {HELP_OPT, "help", "false", "false", "print this help"}

    };

    private void usage(Options options) {
        String header = "Checks SSL connectivity." + "\nTries to connect to the remote server and establish a connection.";
        String footer = "Good luck!";

        HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp(this.getClass().getName(), header, options, footer, true);
    }

    private CommandLine parseCommandLine(String[] args) {
        // create the command line parser
        CommandLineParser parser = new DefaultParser();

        // create the Options
        Options options = new Options();
        for (String[] opt : opts) {
            Option option = Option.builder(opt[0]).longOpt(opt[1]).hasArg("true".equalsIgnoreCase(opt[2])).desc(opt[4]).build();
            option.setRequired("true".equalsIgnoreCase(opt[3]));
            options.addOption(option);
        }

        // parse the command line arguments
        CommandLine line = null;
        try {
            line = parser.parse(options, args);
        } catch (ParseException e) {
            System.out.println("Unexpected exception:" + e.getMessage());
            usage(options);
        }
        return line;
    }

    public int CheckCertStore(String host, int port, String uri, String targetKeyStore, String keyStorePwd) throws Exception {
        if (keyStorePwd == null || keyStorePwd.length() < 1) {
            keyStorePwd = "changeit";
        }
        char[] passphrase = keyStorePwd.toCharArray();

        File file = new File(targetKeyStore);
        if (file.isFile() == false) {
            char SEP = File.separatorChar;
            File dir = new File(System.getProperty("java.home") + SEP + "lib" + SEP + "security");
            /* Check if this is a JDK home */
            if (!dir.isDirectory()) {
                dir = new File(System.getProperty("java.home") + SEP + "jre" + SEP + "lib" + SEP + "security");
            }
            if (!dir.isDirectory()) {
                throw new Exception("The JSSE folder could not be identified. Please check that JSSE is installed.");
            }
            file = new File(dir, "jssecacerts");
            if (file.isFile() == false) {
                file = new File(dir, "cacerts");
            }
        }
        // System.out.println("Loading KeyStore " + file + "...");
        InputStream in = new FileInputStream(file);
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        ks.load(in, passphrase);
        in.close();
        SSLSocket socket = null;
        try {
            socket = (SSLSocket) SSLSocketFactory.getDefault().createSocket(host, port);
        } catch (Exception e) {
            throw new Exception("\nSOCKET FAIL ::: Reason: " + e + "\n");

        }
        SSLParameters sslParms = socket.getSSLParameters();
        String[] protocols = sslParms.getProtocols();
        // String [] protocols = socket.getEnabledProtocols();
        // for (int i = 0; i < protocols.length; i++) System.out.println("\nProtocol : "
        // + protocols[i]);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        X509TrustManager defaultX509TM = null;

        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                defaultX509TM = (X509TrustManager) tm;
                break;
            }
        }

        SavingTrustManager tm = new SavingTrustManager(defaultX509TM);

        String lastExceptionMsg = "";
        for (int i = 0; i < protocols.length; i++) {
            SSLContext context;
            try {
                context = SSLContext.getInstance(protocols[i]);
            } catch (NoSuchAlgorithmException e1) {
                lastExceptionMsg = e1.getMessage();
                continue;
            }
            System.out.println("Adding KeyManager for possible HTTP AUTH...");
            KeyManagerFactory kmf;
            kmf = KeyManagerFactory.getInstance("SunX509");
            kmf.init(ks, passphrase);
            //BasicScheme basicAuth = new BasicScheme();
            //localcontext.setAttribute("preemptive-auth", basicAuth);
            context.init(kmf.getKeyManagers(), new TrustManager[]{tm}, null);
            SSLSocketFactory factory = context.getSocketFactory();
            try {
                socket = (SSLSocket) factory.createSocket(host, port);
            } catch (IOException io) {
                /* possibly an unsupported protocol so keep going */
                lastExceptionMsg = io.getMessage();
                continue;
            } catch (Exception e) {
                throw new Exception(e);
            }
            System.out.println("Set SSLContext using protocol: " + protocols[i]);
            break; // Must have successfully connected now
        }
        if (socket == null) {
            throw new Exception("Failed to connect to remote system:  " + lastExceptionMsg);
        }
        try {
            socket.setSoTimeout(10000);
            System.out.println("\n\t\t**** Starting SSL handshake...");
            socket.startHandshake();
            if (!socket.isClosed()) {
                socket.close();
            }
            checkUsingApacheHttp(host, port, uri, targetKeyStore, keyStorePwd);
            // Trusted cert so no need to do anything
            System.out.println("No errors, certificate is already trusted");
            return 0;
        } catch (SSLHandshakeException e) {
            e.printStackTrace(System.out);
            System.out.println("\nException caught starting SSL handshake so trying to set up a local certificate store with trust chain....\n\n");
            checkUsingApacheHttp(host, port, uri, targetKeyStore, keyStorePwd);
        }

        X509Certificate[] chain = tm.chain;
        if (chain == null) {
            throw new Exception("Could not obtain server certificate chain");
        }
        System.out.println("Number of certificates in chain: " + chain.length);
        // If the chain contains intermediates then Only install the certificate chain
        // not the host cert
        // Otherwise if the chain length is 1 then probably the root certificate is not
        // trusted so store that
        int startNdx = (chain.length == 1) ? 0 : 1;
        if (startNdx == 0) {
            findClosestMatchTrustedCert(ks, chain[0]);
            System.out.println("\n\nThe root certificate is not trusted so storing it locally... ");
        }
        for (int k = 0; k < chain.length; k++) {
            X509Certificate cert = chain[k];
            String alias = host + "-" + (k + 1);
            ks.setCertificateEntry(alias, cert);

            OutputStream out = new FileOutputStream(targetKeyStore);
            ks.store(out, passphrase);
            out.close();
            System.out.println("Installed certificate as trusted: " + cert.getIssuerDN() + "::" + cert.getSigAlgName());
        }
        return 0;
    }

    private void checkUsingApacheHttp(String host, int port, String uri, String targetKeyStore, String keyStorePwd) throws Exception {
        System.out.println("Trying using Apache HTTP Client...");
        Map<String, String> httpOptions = new HashMap<String, String>();
        if (auth_user != null) {
            httpOptions.put(HTTPUtil.PARAM_HTTP_USER, auth_user);
            httpOptions.put(HTTPUtil.PARAM_HTTP_PWD, auth_pwd);
        }
        ResponseWrapper resp = HTTPUtil.execRequest(HTTPUtil.Method.POST, "https://" + host + ":" + port + "/" + uri, null, null, new ByteArrayInputStream("Testing".getBytes()), httpOptions, 1000000000);
        System.out.println("Got a response using Apache HTTP Client: " + resp.getStatusCode());
        System.out.println("\t\tHEADERS: " + resp.getHeaders());
        System.out.println("\t\tBODY: " + resp.getBody());

    }

    private static void findClosestMatchTrustedCert(KeyStore ks, X509Certificate rootCert) {
        // This class retrieves the most-trusted CAs from the keystore
        PKIXParameters params;
        try {
            params = new PKIXParameters(ks);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            return;
        }

        String rootCertDN = rootCert.getIssuerDN().getName();
        String org = getDNField("O", rootCertDN).toLowerCase();
        String org1StWord = org.replaceAll("(\\S*)[^$]*", "$1").toLowerCase();
        System.out.println("Looking for matches to root certificate DN:\n\t" + rootCertDN + "\n\t\tReference certificate signing algorthim: " + rootCert.getSigAlgName() + "\n\n\tTrusted certificate(s) most closely matching \"O\" field of root certificate DN:");
        // Get the set of trust anchors, which contain the most-trusted CA certificates
        Iterator<TrustAnchor> it = params.getTrustAnchors().iterator();
        boolean found = false;
        while (it.hasNext()) {
            TrustAnchor ta = (TrustAnchor) it.next();
            // Get certificate
            X509Certificate cert = ta.getTrustedCert();
            String dn = cert.getIssuerDN().getName();
            String lcDN = dn.toLowerCase();
            if (lcDN.contains(org) || lcDN.contains(org1StWord)) {
                found = true;
                System.out.println("\t\tTrusted certificate DN:\n\t\t" + dn + "\n\t\tTrusted certificate signing algorthim: " + cert.getSigAlgName());
            }
        }
        if (!found) {
            System.out.println("\n\t\t\tNo matching certificates found");
        }
    }

    private static String getDNField(String dnFld, String dn) {
        return dn.contains(" " + dnFld + "=\"") ? dn.replaceAll(".* " + dnFld + "=\"([^\"]*)\",[^$]*", "$1") : dn.replaceAll(".* " + dnFld + "=([^,]*),[^$]*", "$1");
    }

    private static class SavingTrustManager implements X509TrustManager {

        private final X509TrustManager tm;
        private X509Certificate[] chain;

        SavingTrustManager(X509TrustManager tm) {
            this.tm = tm;
        }

        public X509Certificate[] getAcceptedIssuers() {
            return tm.getAcceptedIssuers();
        }

        public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            tm.checkClientTrusted(chain, authType);
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
            this.chain = chain;
            tm.checkServerTrusted(chain, authType);
        }
    }

    private void process(String[] args) {
        CommandLine line = parseCommandLine(args);
        String host = line.getOptionValue(HOST);
        int port = (line.hasOption(PORT)) ? Integer.parseInt(line.getOptionValue(PORT)) : 443;
        String uri = (line.hasOption(URI)) ? line.getOptionValue(URI) : "";
        String keyStoreFile = (line.hasOption(CACERT)) ? line.getOptionValue(CACERT) : "";
        String passphrase = (line.hasOption(PASSWORD)) ? line.getOptionValue(PASSWORD) : "changeit";
        auth_user = (line.hasOption(AUTH_USER)) ? line.getOptionValue(AUTH_USER) : null;
        auth_pwd = (line.hasOption(AUTH_PWD)) ? line.getOptionValue(AUTH_PWD) : null;

        if (line.hasOption(DEBUG) && "true".equalsIgnoreCase(line.getOptionValue(DEBUG))) {
            System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
            System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
            System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http", "DEBUG");
            System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.wire", "ERROR");
        }
        try {
            CheckCertStore(host, port, uri, keyStoreFile, passphrase);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        try {
            CheckCertificate mgr = new CheckCertificate();
            mgr.process(args);
        } catch (Exception e) {
            System.out.println("Unexpected exception:" + e.getMessage());
        }
    }
}
