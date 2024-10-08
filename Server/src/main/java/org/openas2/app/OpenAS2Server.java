package org.openas2.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.XMLSession;
import org.openas2.lib.helper.ICryptoHelper;

import jakarta.annotation.Nonnull;
import javax.crypto.Cipher;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;


/**
 * original author unknown
 * <p>
 * in this release added ability to have multiple command processors
 */
public class OpenAS2Server {
    public static final String MANIFEST_VENDOR_ID_ATTRIB = "Implementation-Vendor-Id";
    public static final String MANIFEST_VERSION_ATTRIB = "Implementation-Version";
    public static final String MANIFEST_TITLE_ATTRIB = "Implementation-Title";
    public static final String VENDOR_ID = "net.sf.openas2";
    public static final String PROJECT_NAME = "OpenAS2 Server";

    private static final Logger LOGGER = LoggerFactory.getLogger(OpenAS2Server.class);

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
        if (args != null && args.length > 0 && "--version".equals(args[0])) {
            Attributes manifestAttribs = getManifestAttributes();
            if (manifestAttribs == null) {
                System.out.println("No manifest found  and cannot determine the app version.");
                System.exit(1);
            } else {
                System.out.println("OpenAS2 Version: " + getAppVersion(manifestAttribs));
                System.exit(0);
            }
        }
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
            same.log();
        }

        LOGGER.info("OpenAS2 has shut down\r\n");
    }

    public static Attributes getManifestAttributes() {
        Enumeration<?> resEnum;
        URL openAS2Manifest = null;
        Attributes manifestAttributes = null;
        try {
            resEnum = Thread.currentThread().getContextClassLoader().getResources(JarFile.MANIFEST_NAME);
            while (resEnum.hasMoreElements()) {
                try {
                    URL url = (URL) resEnum.nextElement();
                    InputStream is = url.openStream();
                    if (is != null) {
                        Manifest manifest = new Manifest(is);
                        Attributes attribs = manifest.getMainAttributes();
                        is.close();
                        String vendor = attribs.getValue(MANIFEST_VENDOR_ID_ATTRIB);
                        if (vendor != null && VENDOR_ID.equals(vendor)) {
                            // We have an OpenAS2 jar at least - check the project name
                            String project = attribs.getValue(MANIFEST_TITLE_ATTRIB);
                            if (project != null && PROJECT_NAME.equals(project)) {
                                if (openAS2Manifest != null) {
                                    // A duplicate detected
                                    throw new OpenAS2Exception("Duplicate manifests detected: " + openAS2Manifest.getPath() + " ::: " + url.getPath());
                                }
                                openAS2Manifest = url;
                                manifestAttributes = attribs;
                            }
                        }
                    } else {
                        LOGGER.warn("MANIFEST input stream not opened for: " + url.toString());
                    }
                    
                } catch (Exception e) {
                    LOGGER.info("MANIFEST not accessed: " + e.getMessage(), e);
                }
            }
        } catch (IOException e1) {
            // Silently ignore wrong manifests on classpath?
        }
        if (openAS2Manifest == null) {
            LOGGER.warn("Failed to find a MANIFEST.MF with the desired vendor and project name.");
        } else {
            LOGGER.info("Using MANIFEST " + openAS2Manifest.getPath());
        }
        return manifestAttributes;
    }

    public static String getManifestAttribValue(@Nonnull String attrib, Attributes manifestAttributes) {
        if (manifestAttributes != null) {
            return manifestAttributes.getValue(attrib);
        }
        return "NO MANIFEST";
    }

    public static String getAppVersion(Attributes manifestAttributes) {
        String version =  getManifestAttribValue(OpenAS2Server.MANIFEST_VERSION_ATTRIB, manifestAttributes);
        return version;
    }

    public static String getAppTitle(Attributes manifestAttributes) {
            return getManifestAttribValue(OpenAS2Server.MANIFEST_TITLE_ATTRIB, manifestAttributes) + " v" + getAppVersion(manifestAttributes);
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
                LOGGER.error("No config file found: " + configFile + " :: Path: " + cfg.getAbsolutePath());
                LOGGER.error("Pass as the first paramter on the command line or set the system property \"openas2.config.file\" to identify the configuration file to start OpenAS2");
                throw new Exception("Missing configuration file");
            }
            return cfg;
        }

        public OpenAS2Server run(String... args) throws Exception {
            // an error if the JCE is limited
            if (Cipher.getMaxAllowedKeyLength(ICryptoHelper.DIGEST_MD5) <= ICryptoHelper.JCE_LIMITED_MAX_LENGTH) {
                LOGGER.error(ICryptoHelper.JCE_LIMITATION_ERROR);
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
