package org.openas2;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.cert.CertificateFactory;
import org.openas2.cmd.CommandManager;
import org.openas2.cmd.CommandRegistry;
import org.openas2.cmd.processor.BaseCommandProcessor;
import org.openas2.lib.xml.PropertyReplacementFilter;
import org.openas2.logging.LogManager;
import org.openas2.logging.Logger;
import org.openas2.partner.PartnershipFactory;
import org.openas2.processor.Processor;
import org.openas2.processor.ProcessorModule;
import org.openas2.schedule.SchedulerComponent;
import org.openas2.util.Properties;
import org.openas2.util.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

/**
 * original author unknown
 * <p>
 * in this release added command registry methods
 *
 * @author joseph mcverry
 */
public class XMLSession extends BaseSession {
    private static final String EL_PROPERTIES = "properties";
    private static final String EL_CERTIFICATES = "certificates";
    private static final String EL_CMDPROCESSOR = "commandProcessors";
    private static final String EL_PROCESSOR = "processor";
    private static final String EL_PARTNERSHIPS = "partnerships";
    private static final String EL_COMMANDS = "commands";
    private static final String EL_LOGGERS = "loggers";
    // private static final String PARAM_BASE_DIRECTORY = "basedir";

    private CommandRegistry commandRegistry;
    private CommandManager cmdManager = new CommandManager();

    private static final String MANIFEST_VENDOR_ID_ATTRIB = "Implementation-Vendor-Id";
    private static final String MANIFEST_VERSION_ATTRIB = "Implementation-Version";
    private static final String MANIFEST_TITLE_ATTRIB = "Implementation-Title";
    private static final String VENDOR_ID = "net.sf.openas2";
    private static final String PROJECT_NAME = "OpenAS2 Server";
    private Attributes manifestAttributes = null;
    private String VERSION;
    private String TITLE;

    private static final Log LOGGER = LogFactory.getLog(XMLSession.class.getSimpleName());

    public XMLSession(String configAbsPath) throws Exception {
        File configXml = new File(configAbsPath);
        File configDir = configXml.getParentFile();
        getManifestAttributes();

        FileInputStream configAsStream = new FileInputStream(configXml);
        setBaseDirectory(configDir.getAbsolutePath());

        load(configAsStream);

        // scheduler should be initializer after all modules
        addSchedulerComponent();
    }

    private void addSchedulerComponent() throws OpenAS2Exception {
        SchedulerComponent comp = new SchedulerComponent();
        setComponent("scheduler", comp);
        comp.init(this, Collections.emptyMap());
    }

    protected void load(InputStream in) throws Exception {
        Document document = XMLUtil.parseXML(in, new PropertyReplacementFilter());

        Element root = document.getDocumentElement();

        NodeList rootNodes = root.getChildNodes();
        Node rootNode;
        String nodeName;

        // this is used by all other objects to access global configs and functionality
        LOGGER.info("Loading configuration...");
        for (int i = 0; i < rootNodes.getLength(); i++) {
            rootNode = rootNodes.item(i);

            nodeName = rootNode.getNodeName();

            // enter the command processing loop
            if (nodeName.equals(EL_PROPERTIES)) {
                loadProperties(rootNode);
            } else if (nodeName.equals(EL_CERTIFICATES)) {
                loadCertificates(rootNode);
            } else if (nodeName.equals(EL_PROCESSOR)) {
                loadProcessor(rootNode);
            } else if (nodeName.equals(EL_CMDPROCESSOR)) {
                loadCommandProcessors(rootNode);
            } else if (nodeName.equals(EL_PARTNERSHIPS)) {
                loadPartnerships(rootNode);
            } else if (nodeName.equals(EL_COMMANDS)) {
                loadCommands(rootNode);
            } else if (nodeName.equals(EL_LOGGERS)) {
                loadLoggers(rootNode);
            } else if (nodeName.equals("#text")) {
                // do nothing
            } else if (nodeName.equals("#comment")) {
                // do nothing
            } else {
                throw new OpenAS2Exception("Undefined tag: " + nodeName);
            }
        }

        cmdManager.registerCommands(commandRegistry);
    }

    /**
     * First retrieves all properties specified in the <properties> element of the config.xml file (propNode param)
     * Then loads system properties into the OpenAS2 properties container.
     * Then adds the application title and version.
     * Finally checks if an additional property file was provided and loads those.
     * 
     * @param propNode - the "properties" element of the configuration file containing property values
     */
    private void loadProperties(Node propNode) {
        LOGGER.info("Loading properties...");

        Map<String, String> properties = XMLUtil.mapAttributes(propNode, false);
        @SuppressWarnings({ "unchecked", "rawtypes" })
        Map<String, String> sysProps = (Map)System.getProperties();
        properties.putAll(sysProps);
        // Make key things accessible via static object for things that do not have
        // accesss to session object
        properties.put(Properties.APP_TITLE_PROP, getAppTitle());
        properties.put(Properties.APP_VERSION_PROP, getAppVersion());
        Properties.setProperties(properties);
        String appPropsFile = System.getProperty("openas2.properties.file");
        if (appPropsFile != null && appPropsFile.length() > 1) {
            java.util.Properties appProps = new java.util.Properties();
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(appPropsFile);
                appProps.load(fis);
                Enumeration enuKeys = appProps.keys();
                while (enuKeys.hasMoreElements()) {
                    String key = (String) enuKeys.nextElement();
                    Properties.setProperty(key, appProps.getProperty(key));
                }

            } catch (FileNotFoundException e) {
                LOGGER.warn("Custom properties file specified but cannot be located:" + appPropsFile);
            } catch (IOException e) {
                LOGGER.warn("Custom properties file load failed:" + appPropsFile, e);
            } finally {
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        LOGGER.warn("Failed to close properties fiel input stream.", e);
                    }
                }
            }
        }
    }

    private void loadCertificates(Node rootNode) throws OpenAS2Exception {
        CertificateFactory certFx = (CertificateFactory) XMLUtil.getComponent(rootNode, this);
        setComponent(CertificateFactory.COMPID_CERTIFICATE_FACTORY, certFx);
    }

    private void loadCommands(Node rootNode) throws OpenAS2Exception {
        Component component = XMLUtil.getComponent(rootNode, this);
        commandRegistry = (CommandRegistry) component;
    }

    private void loadLoggers(Node rootNode) throws OpenAS2Exception {
        LOGGER.info("Loading log manager(s)...");

        LogManager manager = LogManager.getLogManager();
        if (LogManager.isRegisteredWithApache()) {
            // continue
        } else {
            // if using the OpenAS2 loggers the log manager must registered with the jvm
            // argument
            // -Dorg.apache.commons.logging.Log=org.openas2.logging.Log
            throw new OpenAS2Exception("the OpenAS2 loggers' log manager must be registered with the jvm argument -Dorg.apache.commons.logging.Log=org.openas2.logging.Log");
        }
        NodeList loggers = rootNode.getChildNodes();
        Node logger;

        for (int i = 0; i < loggers.getLength(); i++) {
            logger = loggers.item(i);

            if (logger.getNodeName().equals("logger")) {
                if ("true".equalsIgnoreCase(XMLUtil.getNodeAttributeValue(logger, "enabled", true))) {
                    loadLogger(manager, logger);
                } else {
                    LOGGER.info("Logger is disabled ... ignoring: " + XMLUtil.getNodeAttributeValue(logger, "classname", false));
                }
            }
        }
    }

    private void loadLogger(LogManager manager, Node loggerNode) throws OpenAS2Exception {
        Logger logger = (Logger) XMLUtil.getComponent(loggerNode, this);
        manager.addLogger(logger);
    }

    private void loadCommandProcessors(Node rootNode) throws OpenAS2Exception {

        // get a registry of Command objects, and add Commands for the Session
        LOGGER.info("Loading command processor(s)...");

        NodeList cmdProcessor = rootNode.getChildNodes();
        Node processor;

        for (int i = 0; i < cmdProcessor.getLength(); i++) {
            processor = cmdProcessor.item(i);

            if (processor.getNodeName().equals("commandProcessor")) {
                if ("true".equalsIgnoreCase(XMLUtil.getNodeAttributeValue(processor, "enabled", true))) {
                    loadCommandProcessor(cmdManager, processor);
                } else {
                    LOGGER.info("Command processor is disabled ... ignoring: " + XMLUtil.getNodeAttributeValue(processor, "classname", false));
                }
            }
        }
    }

    private void loadCommandProcessor(CommandManager manager, Node cmdPrcessorNode) throws OpenAS2Exception {
        BaseCommandProcessor cmdProcesor = (BaseCommandProcessor) XMLUtil.getComponent(cmdPrcessorNode, this);
        manager.addProcessor(cmdProcesor);

        setComponent(cmdProcesor.getName(), cmdProcesor);
    }

    private void loadPartnerships(Node rootNode) throws OpenAS2Exception {
        LOGGER.info("Loading partnerships...");

        PartnershipFactory partnerFx = (PartnershipFactory) XMLUtil.getComponent(rootNode, this);
        setComponent(PartnershipFactory.COMPID_PARTNERSHIP_FACTORY, partnerFx);
    }

    private void loadProcessor(Node rootNode) throws OpenAS2Exception {
        Processor proc = (Processor) XMLUtil.getComponent(rootNode, this);
        setComponent(Processor.COMPID_PROCESSOR, proc);

        LOGGER.info("Loading processor modules...");

        NodeList modules = rootNode.getChildNodes();
        Node module;

        for (int i = 0; i < modules.getLength(); i++) {
            module = modules.item(i);

            if (module.getNodeName().equals("module")) {
                // Allow no enabled attrib to default to "true"
                String enabledFlag = XMLUtil.getNodeAttributeValue(module, "enabled", true);
                if (enabledFlag == null || "true".equalsIgnoreCase(enabledFlag)) {
                    loadProcessorModule(proc, module);
                } else {
                    try {
                        LOGGER.info("Module is disabled ... ignoring: " + XMLUtil.toString(module, true));
                    } catch (TransformerException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private void loadProcessorModule(Processor proc, Node moduleNode) throws OpenAS2Exception {
        ProcessorModule procmod = (ProcessorModule) XMLUtil.getComponent(moduleNode, this);
        proc.getModules().add(procmod);
    }

    private void getManifestAttributes() throws OpenAS2Exception {
        Enumeration<?> resEnum;
        URL openAS2Manifest = null;
        try {
            resEnum = Thread.currentThread().getContextClassLoader().getResources(JarFile.MANIFEST_NAME);
            while (resEnum.hasMoreElements()) {
                try {
                    URL url = (URL) resEnum.nextElement();
                    InputStream is = url.openStream();
                    if (is != null) {
                        Manifest manifest = new Manifest(is);
                        Attributes mainAttribs = manifest.getMainAttributes();
                        is.close();
                        String vendor = mainAttribs.getValue(MANIFEST_VENDOR_ID_ATTRIB);
                        if (vendor != null && VENDOR_ID.equals(vendor)) {
                            // We have an OpenAS2 jar at least - check the project name
                            String project = mainAttribs.getValue(MANIFEST_TITLE_ATTRIB);
                            if (project != null && PROJECT_NAME.equals(project)) {
                                if (openAS2Manifest != null) {
                                    // A duplicate detected
                                    throw new OpenAS2Exception("|Duplicate manifests detected: " + openAS2Manifest.getPath() + " ::: " + url.getPath());
                                }
                                openAS2Manifest = url;
                                manifestAttributes = mainAttribs;
                            }
                        }
                    }
                } catch (Exception e) {
                    // Silently ignore wrong manifests on classpath?
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
    }

    @Nullable
    private String getManifestAttribValue(@Nonnull String attrib) throws OpenAS2Exception {
        if (manifestAttributes != null) {
            return manifestAttributes.getValue(attrib);
        }
        return "NO MANIFEST";
    }

    public String getAppVersion() {
        if (VERSION == null) {
            try {
                VERSION = getManifestAttribValue(MANIFEST_VERSION_ATTRIB);
            } catch (OpenAS2Exception e) {
                LOGGER.warn(e.getMessage());
            }
        }
        return VERSION;
    }

    public String getAppTitle() {
        if (TITLE == null) {
            try {
                TITLE = getManifestAttribValue(MANIFEST_TITLE_ATTRIB) + " v" + getAppVersion();
            } catch (OpenAS2Exception e) {
                LOGGER.warn(e.getMessage());
            }
        }
        return TITLE;

    }
}
