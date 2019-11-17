/**
 *
 */
package org.openas2.remote;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;

/**
 * @author joseph mcverry
 * a test program but usable with the SocketCommandProcessor which in turns passes to
 * command off to the OpenAS2Server.
 *
 * uses TLS_DH_anon_WITH_AES_256_CBC_SHA cipher for the secure socket layer;
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
                port = "14321";
                name = "userID";
                pwd = "pWd";

            } else if (args.length != 4) {
                System.out.println("" +
                    "format: java org.openas2.remote.CommandLine ipaddresss portnumber userid password"
                );
                return;
            } else {
                host = args[0];
                port = args[1];
                name = args[2];
                pwd = args[3];
            }
            int iport = Integer.parseInt(port);
            while (true) {
                System.out.print("Enter command: ");
                String icmd = br.readLine().trim();
                System.out.print("");
                if (icmd.length() < 1) {
                    System.out.println("\r\n");
                    continue;
                } else if ("exit".equals(icmd)) {
                    System.out.println("Terminating remote session.\r\n");
                    return;
                } else if ("shutdown".equals(icmd)) {
                    System.out.println("" +
                        "This will shutdown your OpenAS2 server. Are you sure you wish to do this? [Y/N]"
                    );
                    String confirm = br.readLine().trim();
                    if (!"Y".equalsIgnoreCase(confirm)) {
                        icmd = "";
                        System.out.println("Command cancelled.\r\n");
                        continue;
                    } else {
                        icmd = "exit";
                    }
                }
                s = (SSLSocket) SSLSocketFactory.getDefault()
                                                .createSocket(InetAddress.getByName(host), iport);
                final String cmdProcessorCipherName = "TLS_DH_anon_WITH_AES_256_CBC_SHA";
                String cipherSuites = System.getProperty("CmdProcessorSocketCipher", cmdProcessorCipherName);
                final String[] enabledCipherSuites = {cipherSuites};
                try {
                    s.setEnabledCipherSuites(enabledCipherSuites);
                } catch (IllegalArgumentException e) {
                    e.printStackTrace();
                    System.out.println("" +
                        "Cipher is not supported. Try using the command line switch " +
                        "-DCmdProcessorSocketCipher=<some cipher suite> to use one supported by your " +
                        "version of java security."
                    );
                }
                String cmd = "<command id=\"" + name + "\" password=\"" + pwd + "\">" + icmd + "</command>";
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
