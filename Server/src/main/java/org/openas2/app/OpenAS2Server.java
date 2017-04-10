package org.openas2.app;

import java.io.File;

import javax.annotation.Nonnull;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.XMLSession;


/**
 * original author unknown
 * <p>
 * in this release added ability to have multiple command processors
 *
 * @author joseph mcverry
 */
public class OpenAS2Server {
    private static final Log LOGGER = LogFactory.getLog(OpenAS2Server.class.getSimpleName());

    @Nonnull
    private final Session session;

    public OpenAS2Server(@Nonnull Session session)
    {
        this.session = session;
    }


    public static void main(String[] args) throws Exception
    {
        new OpenAS2Server.Builder()
                .registerShutdownHook()
                .run(args);
    }

    private void start() throws Exception
    {
        LOGGER.info("Starting Server...");
        // create the OpenAS2 Session object
        // this is used by all other objects to access global configs and functionality
        LOGGER.info("Loading configuration...");

        LOGGER.info(session.getAppTitle() + ": Session instantiated.");
        // create a command processor

        // get a registry of Command objects, and add Commands for the Session
        LOGGER.info("Registering Session to Command Processor...");

        // start the active processor modules
        LOGGER.info("Starting Active Modules...");
        session.getProcessor().startActiveModules();

        // enter the command processing loop
        LOGGER.info(session.getAppTitle() + " Started");

        LOGGER.info("- OpenAS2 Started - V" + session.getAppVersion());

    }

    public void shutdown()
    {
        try
        {
            session.getProcessor().stopActiveModules();
        } catch (OpenAS2Exception same)
        {
            same.terminate();
        }

        LOGGER.info("OpenAS2 has shut down\r\n");
    }


    public static class Builder {
        private boolean registerShutdownHook;

        @Nonnull
        private static File findConfig(@Nonnull String[] runArgs) throws Exception
        {
            LOGGER.info("Retrieving config file...");
            // Check for passed in as parameter first then look for system property
            String configFile = (runArgs.length > 0) ? runArgs[0] : System.getProperty("openas2.config.file");
            if (configFile == null || configFile.length() < 1)
            {
                // Try the default location assuming the app was started in the bin folder
                configFile = System.getProperty("user.dir") + "/../config/config.xml";
            }
            File cfg = new File(configFile);
            if (!cfg.exists())
            {
                LOGGER.error("No config file found: " + configFile);
                LOGGER.error("Pass as the first paramter on the command line or set the system property \"openas2.config.file\" to identify the configuration file to start OpenAS2");
                throw new Exception("Missing configuration file");
            }
            return cfg;
        }

        public OpenAS2Server run(String... args) throws Exception
        {

            XMLSession session = new XMLSession(findConfig(args).getAbsolutePath());
            final OpenAS2Server server = new OpenAS2Server(session);

            registerShutdownHookIfNeeded(server);

            server.start();
            return server;
        }

        private void registerShutdownHookIfNeeded(final OpenAS2Server server)
        {
            if (registerShutdownHook)
            {
                Runtime.getRuntime().addShutdownHook(new Thread() {
                    @Override
                    public void run()
                    {
                        server.shutdown();
                    }
                });
            }
        }

        public Builder registerShutdownHook()
        {
            this.registerShutdownHook = true;
            return this;
        }
    }
}
