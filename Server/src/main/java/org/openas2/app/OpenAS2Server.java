package org.openas2.app;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.XMLSession;
import org.openas2.lib.helper.ICryptoHelper;

import javax.annotation.Nonnull;
import javax.crypto.Cipher;
import java.io.File;


/**
 * original author unknown
 * <p>
 * in this release added ability to have multiple command processors
 */
public class OpenAS2Server {
    private static final Log LOGGER = LogFactory.getLog(OpenAS2Server.class.getSimpleName());

    @Nonnull
    private final Session session;

    private boolean isStarted = false;

    public boolean isStarted() {
        return isStarted;
    }


    public void setStarted(boolean isStarted) {
        this.isStarted = isStarted;
    }


    public Session getSession() {
        return session;
    }


    public OpenAS2Server(@Nonnull Session session) {
        this.session = session;
    }


    public static void main(String[] args) throws Exception {
        new OpenAS2Server.Builder().registerShutdownHook().run(args);
    }

    protected void start() throws Exception {
        LOGGER.info("Starting " + session.getAppTitle() + "...");
        session.start();
        LOGGER.info(session.getAppTitle() + " started.");
        setStarted(true);
    }

    public void shutdown() {
        try {
            session.getProcessor().stopActiveModules();
        } catch (OpenAS2Exception same) {
            same.terminate();
        }

        LOGGER.info("OpenAS2 has shut down\r\n");
    }

    public static class Builder {
        private boolean registerShutdownHook;

        @Nonnull
        private static File findConfig(@Nonnull String[] runArgs) throws Exception {
            LOGGER.info("Retrieving config file...");
            // Check for passed in as parameter first then look for system property
            String configFile = (runArgs.length > 0) ? runArgs[0] : System.getProperty("openas2.config.file");
            if (configFile == null || configFile.length() < 1) {
                // Try the default location assuming the app was started in the bin folder
                configFile = System.getProperty("user.dir") + "/../config/config.xml";
            }
            File cfg = new File(configFile);
            if (!cfg.exists()) {
                LOGGER.error("No config file found: " + configFile);
                LOGGER.error("Pass as the first paramter on the command line or set the system property \"openas2.config.file\" to identify the configuration file to start OpenAS2");
                throw new Exception("Missing configuration file");
            }
            return cfg;
        }

        public OpenAS2Server run(String... args) throws Exception {
            // an error if the JCE is limited
            if (Cipher.getMaxAllowedKeyLength(ICryptoHelper.DIGEST_MD5) <= ICryptoHelper.JCE_LIMITED_MAX_LENGTH) {
                LOGGER.fatal(ICryptoHelper.JCE_LIMITATION_ERROR);
                System.exit(1);
            }

            XMLSession session = new XMLSession(findConfig(args).getAbsolutePath());
            final OpenAS2Server server = new OpenAS2Server(session);

            registerShutdownHookIfNeeded(server);

            server.start();
            return server;
        }

        private void registerShutdownHookIfNeeded(final OpenAS2Server server) {
            if (registerShutdownHook) {
                Runtime.getRuntime().addShutdownHook(new Thread() {
                    @Override
                    public void run() {
                        server.shutdown();
                    }
                });
                LOGGER.info("Shutdown hook registered.");
            }
        }

        public Builder registerShutdownHook() {
            this.registerShutdownHook = true;
            return this;
        }
    }
}
