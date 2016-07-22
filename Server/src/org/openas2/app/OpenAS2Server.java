package org.openas2.app;

import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.XMLSession;
import org.openas2.cmd.CommandManager;
import org.openas2.cmd.CommandRegistry;
import org.openas2.cmd.processor.BaseCommandProcessor;


/** 
 * original author unknown
 * 
 * in this release added ability to have multiple command processors
 * @author joseph mcverry
 *
 */
public class OpenAS2Server {
	protected BufferedWriter sysOut;
	BaseCommandProcessor cmd = null;
	XMLSession session = null;

	
	public static void main(String[] args) {
		OpenAS2Server server = new OpenAS2Server();
		server.start(args);
	}

	public void start(String[] args) {
		int exitStatus = 0;

		Runtime.getRuntime().addShutdownHook(new Thread()
        {
            @Override
            public void run()
            {
            	shutdown();
                System.out.println("Shutdown due to process interruption!");
            }
        });
		try {
			Log logger = LogFactory.getLog(OpenAS2Server.class.getSimpleName());
			
			write(Session.TITLE + "\r\nStarting Server...\r\n");

			// create the OpenAS2 Session object
			// this is used by all other objects to access global configs and functionality
			write("Loading configuration...\r\n");

			if (args.length > 0) {
				session = new XMLSession(args[0]);
			} else {
				write("Usage:\r\n");
				write("java org.openas2.app.OpenAS2Server <configuration file>\r\n");
				throw new Exception("Missing configuration file");
			}
			// create a command processor

			// get a registry of Command objects, and add Commands for the Session
			write("Registering Session to Command Processor...\r\n");

			CommandRegistry reg = session.getCommandRegistry();

			// start the active processor modules
			write("Starting Active Modules...\r\n");
			session.getProcessor().startActiveModules();

			// enter the command processing loop
			write("OpenAS2 V" + Session.VERSION + " Started\r\n");

			
			logger.info("- OpenAS2 Started - V" + Session.VERSION);
			
			CommandManager cmdMgr = session.getCommandManager();
			List<BaseCommandProcessor> processors = cmdMgr.getProcessors();
			for (int i = 0; i < processors.size(); i++) {
				write("Loading Command Processor..." + processors.toString()
						+ "\r\n");
				cmd = (BaseCommandProcessor) processors.get(i);
				cmd.init();
				cmd.addCommands(reg);
				cmd.start();
			}
			breakOut : while (true) {
				for (int i = 0; i < processors.size(); i++) {
					cmd = (BaseCommandProcessor) processors.get(i);
					if (cmd.isTerminated())
						break breakOut;
					Thread.sleep(100); 
				}
			}
			logger.info("- OpenAS2 Stopped -");
		} catch (Exception e) {
			exitStatus = -1;
			e.printStackTrace();
		} catch (Error err) {
			exitStatus = -1;
			err.printStackTrace();
		} finally {
			shutdown();
			System.exit(exitStatus);
		}
	}

	public void shutdown()
	{
		if (session != null) {
			try {
				session.getProcessor().stopActiveModules();
			} catch (OpenAS2Exception same) {
				same.terminate();
			}
		}

		if (cmd != null) {
			try {
				cmd.deInit();
			} catch (OpenAS2Exception cdie) {
				cdie.terminate();
			}
		}

		write("OpenAS2 has shut down\r\n");
		

	}
	
	public void write(String msg) {
		if (sysOut == null) {
			sysOut = new BufferedWriter(new OutputStreamWriter(System.out));
		}

		try {
			sysOut.write(msg);
			sysOut.flush();
		} catch (java.io.IOException e) {
			e.printStackTrace();
		}
	}
}
