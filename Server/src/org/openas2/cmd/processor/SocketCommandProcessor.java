package org.openas2.cmd.processor;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLSocket;

import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.cmd.Command;
import org.openas2.cmd.CommandResult;
import org.openas2.util.CommandTokenizer;
import org.xml.sax.SAXException;

/** actual socket command processor
 *  takes commands from socket/port and passes them to the OpenAS2Server
 * message format 
 *   <command userid="abc" pasword="xyz"> the actual command </command>
 * 
 * when inited the valid userid and password is passed, then as each
 * command is processed the processCommand method verifies the two fields correctness
 *  
 * @author joseph mcverry
 *
 */
public class SocketCommandProcessor extends BaseCommandProcessor
		implements
			Runnable {

	private BufferedReader rdr = null;
	private BufferedWriter wrtr = null;
	private SSLServerSocket sslserversocket = null;

	private String userid, password;

	public SocketCommandProcessor() {
	}

	public void deInit() throws OpenAS2Exception {
	}

	SocketCommandParser parser;

	public void init() throws OpenAS2Exception {
	}

	public void init(Session session, Map<String,String> parameters) throws OpenAS2Exception {
		String p = (String) parameters.get("portid");
		try {
			int port = Integer.parseInt(p);
			
			SSLServerSocketFactory sslserversocketfactory =
                (SSLServerSocketFactory) SSLServerSocketFactory.getDefault();
			 sslserversocket =
                (SSLServerSocket) sslserversocketfactory.createServerSocket(port);
			final String[] enabledCipherSuites = { "SSL_DH_anon_WITH_RC4_128_MD5" };
			sslserversocket.setEnabledCipherSuites(enabledCipherSuites);

			
		} catch (IOException e) {
			e.printStackTrace();
			throw new OpenAS2Exception(e);
		} catch (NumberFormatException e) {
			e.printStackTrace();
			throw new OpenAS2Exception("error converting portid parameter " + e);
		}
		userid = (String) parameters.get("userid");
		if (userid == null || userid.length() < 1)
			throw new OpenAS2Exception("missing userid parameter");

		password = (String) parameters.get("password");
		if (password == null || password.length() < 1)
			throw new OpenAS2Exception("missing password parameter");

		try {
			parser = new SocketCommandParser();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			new OpenAS2Exception(e);
		}
	}

	public void processCommand() throws OpenAS2Exception {

		SSLSocket socket = null;
		try {
			socket = (SSLSocket) sslserversocket.accept();
			socket.setSoTimeout(2000);
			rdr = new BufferedReader(new InputStreamReader(socket.getInputStream()));
			wrtr = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));

			String line;
			line = rdr.readLine();

			parser.parse(line);

			if (parser.getUserid().equals(userid) == false) {
				wrtr.write("Bad userid/password");
				throw new OpenAS2Exception("Bad userid");
			}

			if (parser.getPassword().equals(password) == false) {
				wrtr.write("Bad userid/password");
				throw new OpenAS2Exception("Bad password");
			}

			String str = parser.getCommandText();
			if (str != null && str.length() > 0) {
				CommandTokenizer cmdTkn = new CommandTokenizer(str);
				
				if (cmdTkn.hasMoreTokens()) {
					String commandName = cmdTkn.nextToken().toLowerCase();

					if (commandName.equals(StreamCommandProcessor.EXIT_COMMAND)) {
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
								wrtr.write(result.toXML());
							} else {
								wrtr.write("\r\n" +StreamCommandProcessor.COMMAND_ERROR + "\r\n");
								wrtr.write(result.getResult());
							}
						} else {
							wrtr.write(StreamCommandProcessor.COMMAND_NOT_FOUND
									+ "> " + commandName + "\r\n");
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
		} catch (IOException ioe) {
			ioe.printStackTrace();
		} catch (SAXException e) {
			try {
				if (socket != null)
					socket.close();
			} catch (IOException e1) {
			}
			new OpenAS2Exception(e);
		} catch (Exception e) {
		  new OpenAS2Exception(e);	
		}
		finally {
			if (socket != null)
				try {
					socket.close();
				} catch (IOException e) {
					;
				}

		}

	}

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		try {
			while (true) {
				processCommand();
			}
		} catch (OpenAS2Exception e) {
			e.printStackTrace();
		}

	}

}
