package org.openas2.cmd.processor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.WrappedException;
import org.openas2.cmd.Command;
import org.openas2.cmd.CommandResult;
import org.openas2.util.CommandTokenizer;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * actual socket command processor takes commands from socket/port and passes
 * them to the OpenAS2Server message format &lt;command userid="abc"
 * pasword="xyz"&gt; the actual command &lt;/command&gt;
 * <p>
 * when inited the valid userid and password is passed, then as each command is
 * processed the processCommand method verifies the two fields correctness
 *
 * @author joseph mcverry
 */
public class SocketCommandProcessor extends BaseCommandProcessor {

    SocketCommandParser parser;
    private BufferedReader rdr = null;
    private BufferedWriter wrtr = null;
    private SSLServerSocket sslserversocket = null;
    private String userid, password;
    private String responseFormat = "xml";

    private Log logger = LogFactory.getLog(SocketCommandProcessor.class.getSimpleName());

    public void init(Session session, Map<String, String> parameters) throws OpenAS2Exception {
        super.init(session, parameters);
        String p = parameters.get("portid");
        try {
            int port = Integer.parseInt(p);

            SSLServerSocketFactory sslserversocketfactory = (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
            sslserversocket = (SSLServerSocket) sslserversocketfactory.createServerSocket(port);
            String cipherSuites = System.getProperty("CmdProcessorSocketCipher", "TLS_DH_anon_WITH_AES_256_CBC_SHA");
            final String[] enabledCipherSuites = {cipherSuites};
            try {
                sslserversocket.setEnabledCipherSuites(enabledCipherSuites);
            } catch (IllegalArgumentException e) {
                throw new OpenAS2Exception("Cipher is not supported. Use command line switch -DCmdProcessorSocketCipher=<some cipher suite> to use one supported by your version of java security.", e);
            }

        } catch (IOException e) {
            e.printStackTrace();
            throw new OpenAS2Exception(e);
        } catch (NumberFormatException e) {
            e.printStackTrace();
            throw new OpenAS2Exception("error converting portid parameter " + e);
        }
        userid = parameters.get("userid");
        if (userid == null || userid.length() < 1) {
            throw new OpenAS2Exception("missing userid parameter");
        }

        password = parameters.get("password");
        if (password == null || password.length() < 1) {
            throw new OpenAS2Exception("missing password parameter");
        }

        String rf = parameters.get("response_format");
        if (rf != null && rf.length() > 0) {
            switch (rf) {
                case "xml":
                    responseFormat = rf;
                    break;
                case "txt":
                    responseFormat = rf;
                    break;
                default:
                    logger.info("Socket command processor response format not recognised: \"" + rf + "\"   ::: Will use the default.");
                    break;
            }

        }
        logger.info("Socket command processor response format using \"" + responseFormat + "\".");
        try {
            parser = new SocketCommandParser();
        } catch (Exception e) {
            // TODO Auto-generated catch block
            new OpenAS2Exception(e);
        }
    }

    public void processCommand() throws OpenAS2Exception {

        try (SSLSocket socket = (SSLSocket) sslserversocket.accept()) {
            socket.setSoTimeout(2000);
            rdr = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            wrtr = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

            String line;
            line = rdr.readLine();
            if (logger.isTraceEnabled()) {
                logger.trace("Socket command processor received command: " + line);
            }

            parser.parse(line);

            if (!parser.getUserid().equals(userid)) {
                wrtr.write("Bad userid/password");
                wrtr.flush();
                logger.error("Remote socket command processor accessed with invalid userid ");
                return;
            }

            if (parser.getPassword().equals(password) == false) {
                wrtr.write("Bad userid/password");
                wrtr.flush();
                logger.error("Remote socket command processor accessed with invalid password ");
                return;
            }

            String str = parser.getCommandText();
            if (str != null && str.length() > 0) {
                CommandTokenizer cmdTkn = new CommandTokenizer(str);

                if (cmdTkn.hasMoreTokens()) {
                    String commandName = cmdTkn.nextToken().toLowerCase();

                    if (commandName.equals(StreamCommandProcessor.SERVER_EXIT_COMMAND)) {
                        wrtr.write("Server terminating...");
                        wrtr.flush();
                        rdr.close();
                        wrtr.close();
                        terminate();
                    } else {
                        List<String> params = new ArrayList<String>();

                        while (cmdTkn.hasMoreTokens()) {
                            params.add(cmdTkn.nextToken());
                        }

                        Command cmd = getCommand(commandName);

                        if (cmd != null) {
                            CommandResult result = cmd.execute(params.toArray());

                            if (result.getType() == CommandResult.TYPE_OK) {
                                switch (responseFormat) {
                                    case "txt":
                                        wrtr.write(result.toString());
                                        break;
                                    case "xml":
                                        wrtr.write(result.toXML());
                                        break;
                                    default:
                                        wrtr.write(result.toString());
                                }
                            } else {
                                wrtr.write("\r\n" + StreamCommandProcessor.COMMAND_ERROR + "\r\n");
                                wrtr.write(result.getResult());
                            }
                        } else {
                            wrtr.write(StreamCommandProcessor.COMMAND_NOT_FOUND + "> " + commandName + "\r\n");
                            List<Command> l = getCommands();
                            wrtr.write("List of commands:" + "\r\n");
                            for (int i = 0; i < l.size(); i++) {
                                cmd = l.get(i);
                                wrtr.write(cmd.getName() + "\r\n");
                            }
                        }
                    }
                }

            }
            wrtr.flush();
        } catch (SocketException socketError) {
            // shutdown case
            if (!sslserversocket.isClosed()) {
                throw new WrappedException(socketError);
            }
        } catch (IOException e) {
            // nothing
        } catch (Exception e) {
            // nothing
        }

    }

    @Override
    public void destroy() throws Exception {
        if (logger.isTraceEnabled()) {
            logger.trace("SocketCommandProcessor.destroy called...");
        }
        sslserversocket.close(); // closes remote session
        super.destroy();

    }
}
