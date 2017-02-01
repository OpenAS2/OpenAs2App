
import javax.net.ssl.*;

import java.io.*;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.PKIXParameters;
import java.security.cert.TrustAnchor;
import java.security.cert.X509Certificate;
import java.util.Iterator;

/**
 * Class used to add the server's certificate to the KeyStore with your trusted
 * certificates.
 */
public class CheckCertificate
{

	public static int CheckCertStore(String host, int port, String targetKeyStore, String keyStorePwd) throws Exception
	{
		if (keyStorePwd == null || keyStorePwd.length() < 1)
			keyStorePwd = "changeit";
		char[] passphrase = keyStorePwd.toCharArray();

		File file = new File(targetKeyStore);
		if (file.isFile() == false)
		{
			char SEP = File.separatorChar;
			File dir = new File(System.getProperty("java.home") + SEP + "lib" + SEP + "security");
			/* Check if this is a JDK home */
			if (!dir.isDirectory())
			{
				dir = new File(System.getProperty("java.home") + SEP + "jre" + SEP + "lib" + SEP + "security");
			}
			if (!dir.isDirectory()) { throw new Exception(
						"The JSSE folder could not be identified. Please check that JSSE is installed."); }
			file = new File(dir, "jssecacerts");
			if (file.isFile() == false)
			{
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
		}
		catch (Exception e) {
			throw new Exception("\nSOCKET FAIL ::: Reason: " + e + "\n");

		}
		SSLParameters sslParms = socket.getSSLParameters();
		String [] protocols = sslParms.getProtocols();
		//String [] protocols = socket.getEnabledProtocols();
		//for (int i = 0; i < protocols.length; i++) System.out.println("\nProtocol : " + protocols[i]);
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
		for (int i = 0; i < protocols.length; i++)
		{
			SSLContext context;
			try
			{
				context = SSLContext.getInstance(protocols[i]);
			}
			catch (NoSuchAlgorithmException e1)
			{
				lastExceptionMsg = e1.getMessage();
				continue;
			}
			context.init(null, new TrustManager[]{ tm }, null);
			SSLSocketFactory factory = context.getSocketFactory();
			try
			{
				socket = (SSLSocket) factory.createSocket(host, port);
			}
			catch (IOException io)
            {
				/*possibly an unsupported protocol so keep going */
				lastExceptionMsg = io.getMessage();
				continue;
			}
			catch (Exception e)
			{
				throw new Exception(e);
			}
			break; // Must have successfully connected now
		}
		if (socket == null)
			throw new Exception("Failed to connect to remote system:  " + lastExceptionMsg);
		try
		{
			socket.setSoTimeout(10000);
			// System.out.println("Starting SSL handshake...");
			socket.startHandshake();
			socket.close();
			//System.out.println();
			//System.out.println("No errors, certificate is already trusted");
			// Trusted cert so no need to do anything
			System.out.println("No errors, certificate is already trusted");
			return 0;
		}
		catch (SSLHandshakeException e)
		{
			//e.printStackTrace(System.out);
			System.out.println("\nException caught starting SSL handshake so set up a local certificate store with trust chain....\n\n");
		}

		X509Certificate[] chain = tm.chain;
		if (chain == null)
		{
			throw new Exception("Could not obtain server certificate chain");
		}
        System.out.println("Number of certificates in chain: " + chain.length);
        // If the chain contains intermediates then Only install the certificate chain not the host cert
       // Otherwise if the chain length is 1 then probably the root certificate is not trusted so store that
        int startNdx = (chain.length == 1)?0:1;
        if (startNdx == 0)
        {
            findClosestMatchTrustedCert(ks, chain[0]);
            System.out.println("\n\nThe root certificate is not trusted so storing it locally... ");
        }
		for (int k = 0; k < chain.length; k++)
		{
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
	
	private static void findClosestMatchTrustedCert(KeyStore ks, X509Certificate rootCert)
	{
        // This class retrieves the most-trusted CAs from the keystore
        PKIXParameters params;
		try
		{
			params = new PKIXParameters(ks);
		} catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
			return;
		}

        String rootCertDN = rootCert.getIssuerDN().getName();
        String org = getDNField("O", rootCertDN).toLowerCase();
        String org1StWord = org.replaceAll("(\\S*)[^$]*", "$1").toLowerCase();
        System.out.println("Looking for matches to root certificate DN:\n\t" + rootCertDN
      		  + "\n\t\tReference certificate signing algorthim: " + rootCert.getSigAlgName()
      		  + "\n\n\tTrusted certificate(s) most closely matching \"O\" field of root certificate DN:");
        // Get the set of trust anchors, which contain the most-trusted CA certificates
        Iterator<TrustAnchor> it = params.getTrustAnchors().iterator();
        boolean found = false;
        while( it.hasNext() ) {
            TrustAnchor ta = (TrustAnchor)it.next();
            // Get certificate
            X509Certificate cert = ta.getTrustedCert();
            String dn = cert.getIssuerDN().getName();
            String lcDN = dn.toLowerCase();
            if (lcDN.contains(org) || lcDN.contains(org1StWord))
            {
              found = true;
              System.out.println(
            		  "\t\tTrusted certificate DN:\n\t\t" + dn
            		+  "\n\t\tTrusted certificate signing algorthim: " + cert.getSigAlgName());
            }
        }
        if (!found) System.out.println("\n\t\t\tNo matching certificates found");
	}
	
	private static String getDNField(String dnFld, String dn)
	{
		return dn.contains(" "+dnFld+"=\"")?dn.replaceAll(".* "+dnFld+"=\"([^\"]*)\",[^$]*", "$1"):dn.replaceAll(".* "+dnFld+"=([^,]*),[^$]*", "$1");
	}

	private static class SavingTrustManager implements X509TrustManager
	{

		private final X509TrustManager tm;
		private X509Certificate[] chain;

		SavingTrustManager(X509TrustManager tm)
		{
			this.tm = tm;
		}

		public X509Certificate[] getAcceptedIssuers()
		{
			return tm.getAcceptedIssuers();
		}

		public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException
		{
			tm.checkClientTrusted(chain, authType);
		}

		public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException
		{
			this.chain = chain;
			tm.checkServerTrusted(chain, authType);
		}
	}

	public static void main(String[] args) throws Exception
	{
		String host;
		int port;
		String keyStoreFile;
		String passphrase;
		if ((args.length == 2) || (args.length == 3))
		{
			String[] c = args[0].split(":");
			host = c[0];
			port = (c.length == 1) ? 443 : Integer.parseInt(c[1]);
			keyStoreFile = args[1];
			passphrase = (args.length == 2) ? "changeit" : args[2];
		}
		else
		{
			System.out.println("Usage: java CheckCertChain <host name>[:port] <localKeystoreFile> [passphrase]");
			return;
		}
		CheckCertStore(host, port, keyStoreFile, passphrase);
	}
}
