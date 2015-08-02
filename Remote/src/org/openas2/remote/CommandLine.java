/**
 * 
 */
package org.openas2.remote;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * @author joseph mcverry
 * a test program but usable with the SocketCommandProcessor which in turns passes to 
 * command off to the OpenAS2Server.
 * 
 * uses SSL_DH_anon_WITH_RC4_128_MD5 cipher for the secure socket layer; 
 * 
 */
public class CommandLine {
	public static void main(String args[]) {
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
			SSLSocket s = null;
			String host, port, name, pwd;
			if (args.length == 0) {
				host = "localhost";
				port = "4321";
				name = "userID";
				pwd = "pWd"; 
				
			} else 
			if (args.length != 4) {
				System.out.println("format: java org.openas2.remote.CommandLine ipaddresss portnumber userid password command");
				return;
			} else {
				host = args[0];
				port = args[1];
				name = args[2];
				pwd = args[3];
			}
			int iport = Integer.parseInt(port);
			while (true) {
				String icmd = br.readLine().trim();
				if (icmd.length() < 1) {
					System.out.println("adios");
					return;
				}
				s = (SSLSocket) SSLSocketFactory.getDefault().createSocket(InetAddress.getByName(host), iport);
				final String[] enabledCipherSuites = { "SSL_DH_anon_WITH_RC4_128_MD5" };
				s.setEnabledCipherSuites(enabledCipherSuites);
				String cmd = "<command id=\"" + name + 
					"\" password=\"" + pwd + "\">" + 
					icmd + "</command>";
				PrintStream ps = new PrintStream(s.getOutputStream(), true);
				ps.println(cmd);
				BufferedReader rdr = new BufferedReader(new InputStreamReader(s.getInputStream()));
				String r;
				while ((r = rdr.readLine()) != null) {
					System.out.println(r);
				}
				s.close();
			}


		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
