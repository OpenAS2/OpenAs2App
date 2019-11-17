 /* Copyright (C) 2007 by Joseph McVerry - American Coders, Ltd.
  *
  * This library is free software; you can redistribute it and/or
  * modify it under the terms of the GNU Library General Public
  * License as published by the Free Software Foundation; either
  * version 2 of the License, or (at your option) any later version.
  *
  * This library is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
  * Library General Public License for more details.
  *
  * You should have received a copy of the GNU Library General Public
  * License along with this library; if not, write to the Free
  * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
  *
  */

 package org.openas2.remote;

 import javax.net.ssl.SSLSocket;
 import javax.net.ssl.SSLSocketFactory;
 import javax.servlet.ServletException;
 import javax.servlet.http.HttpServlet;
 import javax.servlet.http.HttpServletRequest;
 import javax.servlet.http.HttpServletResponse;
 import java.io.BufferedReader;
 import java.io.ByteArrayOutputStream;
 import java.io.CharArrayWriter;
 import java.io.IOException;
 import java.io.InputStreamReader;
 import java.io.PrintStream;
 import java.io.PrintWriter;
 import java.net.InetAddress;
 import java.net.UnknownHostException;


 /**
  * @author joseph mcverry
  */
 public class OpenAS2Servlet extends HttpServlet {

     /**
      *
      */
     private static final long serialVersionUID = -625641001873163537L;
     SocketLogging sl = null;
     LogGetter lg = null;
     ByteArrayOutputStream baos = null;
     int logPort;

     String commandHostID = "";
     int commandPort;
     String commandUserID = "";
     String commandPWD = "";


     public void init() throws ServletException {
         super.init();
         String port = getServletConfig().getInitParameter("loggingPort");
         if (port == null) {
             throw new ServletException("loggingPort not defined in servlet config file");
         }

         try {
             logPort = Integer.parseInt(port);
         } catch (NumberFormatException e) {
             throw new ServletException("loggingPort value not an int", e);
         }

         commandHostID = getServletConfig().getInitParameter("commandHostID");
         if (commandHostID == null) {
             throw new ServletException("commandHostID not defined in servlet config file");
         }


         port = getServletConfig().getInitParameter("commandPort");
         if (port == null) {
             throw new ServletException("commandPort not defined in servlet config file");
         }

         try {
             commandPort = Integer.parseInt(port);
         } catch (NumberFormatException e) {
             throw new ServletException("commandPort value not an int", e);
         }

         commandUserID = getServletConfig().getInitParameter("commandUserID");
         if (commandUserID == null) {
             throw new ServletException("commandUserID not defined in servlet config file");
         }

         commandPWD = getServletConfig().getInitParameter("commandPWD");
         if (commandPWD == null) {
             throw new ServletException("commandPWD not defined in servlet config file");
         }


     }

     PrintWriter writer;

     protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
         response.setContentType("text/xml");
         response.setHeader("Cache-Control", "no-cache");
         writer = response.getWriter();

         writer.println("<h1>Not Ready</h1>");

         writer.flush();
         return;

     }

     protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException {

         response.setContentType("text/xml");
         response.setHeader("Cache-Control", "no-cache");
         try {
             writer = response.getWriter();

             String userid = request.getParameter("userid");
             if (userid == null || userid.equals("")) {
                 response.sendError(401);
                 return;
             }

             String pwd = request.getParameter("pwd");
             if (pwd == null || pwd.equals("")) {
                 response.sendError(401);
                 return;
             }

             String action = request.getParameter("action");

             if (action.startsWith("log")) {
                 doLog(request, response);
             } else if (action.startsWith("cert ")) {
                 doCertificate(request, response);
             } else if (action.startsWith("partner ")) {
                 doPartner(request, response);
             } else if (action.startsWith("partnership ")) {
                 doPartnership(request, response);
             } else if (action.equals("useridpwd")) {
                 doUserIDPWD(userid, pwd, response);
             } else {
                 writer.println("<h1>Not Ready</h1>");
             }

             writer.flush();
         } catch (IOException ioe) {
             throw new ServletException("IOException " + ioe.getMessage());
         }
         return;

     }

     /**
      * @param userid
      * @param pwd
      * @param response
      */
     private void doUserIDPWD(String inuser, String inpwd, HttpServletResponse response) {
         if (commandUserID.equals(inuser) && commandPWD.equals(inpwd)) {
             writer.print("<useridpwd resp=\"okay\"/>");
         } else {
             writer.print("<useridpwd resp=\"nope\"/>");
         }
     }

     /**
      * @param request
      * @param response
      * @throws IOException
      * @throws UnknownHostException
      */
     private void doPartnership(final HttpServletRequest request,
                                final HttpServletResponse response) throws UnknownHostException, IOException {
         String action = request.getParameter("action");
         String reply = "not defined";
         if (action.equals("partnership list")) { // get the cert list
             reply = remoteCommandCall(action);
         }
         if (action.startsWith("partnership view")) { // get the cert list
             reply = remoteCommandCall(action);
         }

         writer.print("<partnership>" + normalize(reply) + "</partnership>");

     }

     /**
      * @param request
      * @param response
      * @throws IOException
      * @throws UnknownHostException
      */
     private void doPartner(final HttpServletRequest request,
                            final HttpServletResponse response) throws UnknownHostException, IOException {
         String action = request.getParameter("action");
         String reply = "not defined";
         if (action.equals("partner list")) { // get the cert list
             reply = remoteCommandCall(action);
         }
         if (action.startsWith("partner view")) { // get the cert list
             reply = remoteCommandCall(action);
         }


         writer.print("<partner>" + normalize(reply) + "</partner>");

     }

     /**
      * @param request
      * @param response
      * @throws IOException
      * @throws UnknownHostException
      */
     private void doCertificate(final HttpServletRequest request,
                                final HttpServletResponse response) throws UnknownHostException, IOException {

         String action = request.getParameter("action");
         String reply = "not defined";
         if (action.equals("cert list")) { // get the cert list
             reply = remoteCommandCall(action);
         }

         if (action.startsWith("cert view")) { // view the cert
             reply = remoteCommandCall(action);
         }

         if (action.startsWith("cert delete")) { // delete the cert
             reply = remoteCommandCall(action);
         }

         if (action.startsWith("cert import")) { // import
             reply = remoteCommandCall(action);
             action = reply;
         }

         writer.print("<cert>" + normalize(reply) + "</cert>");

     }

     /**
      * @param request
      * @param response
      * @throws IOException
      */
     private void doLog(final HttpServletRequest request,
                        final HttpServletResponse response) throws IOException {

         String logResponse = "";

         String action = request.getParameter("action");
         if (action.endsWith("0")) {
             baos.reset();
             logResponse = "<p/>log stopped<p/>";
             lg.threadSuspended = true;
             lg.interrupt();
             try {
                 lg.join();
             } catch (InterruptedException e) {
                 // TODO Auto-generated catch block

             }
             lg = null;
             sl = null;
             baos = null;
         } else {
             if (sl == null) {
                 sl = new SocketLogging(logPort);
                 baos = new ByteArrayOutputStream();
                 lg = new LogGetter(sl, new PrintStream(baos));
                 lg.start();
             }
             if (baos.size() > 0) {
                 logResponse = baos.toString();
                 baos.reset();
             }
         }


         writer.print("<log resp=\"" + xmlNormalize(logResponse) + "\"/>");

     }

     class LogGetter extends Thread {
         PrintStream ps;
         SocketLogging sl;
         boolean threadSuspended = false;

         LogGetter(SocketLogging inSL, PrintStream inPS) throws IOException {
             sl = inSL;
             ps = inPS;
         }

         public void interrupt() {
             super.interrupt();
             if (threadSuspended == true) {
                 sl.close();
             }


         }

         public void run() {
             while (true) {
                 while (threadSuspended) {
                     try {
                         wait();
                     } catch (InterruptedException e1) {
                     }
                 }
                 try {
                     sl.logToPrintStream(ps, "<br/>");
                 } catch (Exception e) {
                     e.printStackTrace();
                     return;
                 }
             }

         }

     }

     public static String normalize(String in) {
         int len = in.length();

         StringBuffer sb = new StringBuffer(len);
         for (int i = 0; i < len; i++) {

             if (in.charAt(i) == '\'') {
                 sb.append('\\');
             }

             if (in.charAt(i) == '\"') {
                 sb.append('\\');
             }

             if (in.charAt(i) == '\n') {
                 sb.append("%");
                 continue;
             }

             if (in.charAt(i) == '\r') {
                 sb.append("$");
                 continue;
             }

             sb.append(in.charAt(i));
         }
         return new String(sb);
     }

     public static String xmlNormalize(String in) {
         int len = in.length();

         StringBuffer sb = new StringBuffer(len);
         for (int i = 0; i < len; i++) {

             if (in.charAt(i) == '<') {
                 sb.append("&lt;");
                 continue;
             }
             if (in.charAt(i) == '>') {
                 sb.append("&gt;");
                 continue;
             }
             if (in.charAt(i) == '&') {
                 sb.append("&amp;");
                 continue;
             }

             sb.append(in.charAt(i));
         }
         return new String(sb);
     }

     public String remoteCommandCall(String command) throws UnknownHostException, IOException {
         final InetAddress hostAddress = InetAddress.getByName(commandHostID);
         SSLSocket s = (SSLSocket) SSLSocketFactory.getDefault()
                                                   .createSocket(hostAddress, commandPort);
         final String cmdCipher = "TLS_DH_anon_WITH_AES_256_CBC_SHA";
         String cipherSuites = System.getProperty("CmdProcessorSocketCipher", cmdCipher);
         final String[] enabledCipherSuites = {cipherSuites};
         try {
             s.setEnabledCipherSuites(enabledCipherSuites);
         } catch (IllegalArgumentException e) {
             e.printStackTrace();
             System.out.println("Cipher is not supported. " +
                 "Try using the command line switch -DCmdProcessorSocketCipher=<some cipher suite> " +
                 "to use one supported by your version of java security."
             );
         }
         String cmd = new StringBuilder().append("<command id=\"")
                                         .append(commandUserID)
                                         .append("\" password=\"")
                                         .append(commandPWD)
                                         .append("\">")
                                         .append(command)
                                         .append("</command>\n")
                                         .toString();
         s.getOutputStream().write(cmd.getBytes());
         s.getOutputStream().flush();
         CharArrayWriter caw = new CharArrayWriter();
         BufferedReader rdr = new BufferedReader(new InputStreamReader(s.getInputStream()));
         String r;
         while ((r = rdr.readLine()) != null) {
             caw.write(r.toCharArray());
             caw.write("\n");
         }
         s.close();
         return caw.toString();
     }

 }
