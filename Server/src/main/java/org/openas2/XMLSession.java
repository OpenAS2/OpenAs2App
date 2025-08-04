package org.openas2;

import org.openas2.app.OpenAS2Server;
import org.openas2.cert.CertificateFactory;
import org.openas2.cmd.CommandManager;
import org.openas2.cmd.CommandRegistry;
import org.openas2.cmd.processor.BaseCommandProcessor;
import org.openas2.lib.util.StringEnvVarReplacer;
import org.openas2.lib.xml.PropertyReplacementFilter;
import org.openas2.params.CompositeParameters;
import org.openas2.params.ParameterParser;
import org.openas2.message.MessageFactory;
import org.openas2.partner.Partnership;
import org.openas2.partner.PartnershipFactory;
import org.openas2.processor.Processor;
import org.openas2.processor.ProcessorModule;
import org.openas2.processor.receiver.PollingModule;
import org.openas2.schedule.SchedulerComponent;
import org.openas2.util.FileUtil;
import org.openas2.util.Properties;
import org.openas2.util.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.transform.TransformerException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.jar.Attributes;

/**
 * original author unknown
 * <p>
 * in this release added command registry methods
 *
 * @author joseph mcverry
 */
public class XMLSession extends BaseSession {
    public static final String EL_PROPERTIES = "properties";
    public static final String EL_CERTIFICATES = "certificates";
    public static final String EL_CMDPROCESSOR = "commandProcessors";
    public static final String EL_PROCESSOR = "processor";
    public static final String EL_PARTNERSHIPS = "partnerships";
    public static final String EL_MESSAGES = "messages";
    public static final String EL_COMMANDS = "commands";
    public static final String EL_POLLER_CONFIG = "pollerConfigBase";
    // private static final String PARAM_BASE_DIRECTORY = "basedir";

    private CommandRegistry commandRegistry;
    private CommandManager cmdManager = new CommandManager();

    // Poller base config that will be used for partnership based pollers. Can be overridden in the partnership
    private Node basePollerConfigNode = null;

    private Attributes manifestAttributes = null;
    private String VERSION;
    private String TITLE;

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
        PropertyReplacementFilter prf = new PropertyReplacementFilter();
        prf.setAppHomeDir(getBaseDirectory());
        Document document = XMLUtil.parseXML(in, prf);

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
            } else if (nodeName.equals(EL_POLLER_CONFIG)) {
                loadBasePartnershipPollerConfig(rootNode);
            } else if (nodeName.equals(EL_MESSAGES)) {
                loadMessages(rootNode);
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
     * @throws IOException 
     * @throws OpenAS2Exception 
     */
    private void loadProperties(Node propNode) throws IOException, OpenAS2Exception {
        LOGGER.info("Loading properties...");

        Map<String, String> properties = XMLUtil.mapAttributes(propNode, false);
        // Make key things accessible via static object for things that do not have
        // accesss to session object
        properties.put(Properties.APP_TITLE_PROP, getAppTitle());
        properties.put(Properties.APP_VERSION_PROP, getAppVersion());
        Properties.setProperties(properties);
        String appPropsFile = System.getProperty(Properties.OPENAS2_PROPERTIES_FILE_PROP);
        if (appPropsFile != null && appPropsFile.length() > 1) {
            LOGGER.info("Processing OpenAS2 configuration properties file: {}", appPropsFile);
            java.util.Properties appProps = new java.util.Properties();
            // Support $ENV{some_env_var} replacement in properties
            StringEnvVarReplacer envVarReplacer = new StringEnvVarReplacer();
            envVarReplacer.setAppHomeDir(getBaseDirectory());
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(appPropsFile);
                appProps.load(fis);
                Enumeration<Object> enuKeys = appProps.keys();
                while (enuKeys.hasMoreElements()) {
                    String key = (String) enuKeys.nextElement();
                    String val = envVarReplacer.replace(appProps.getProperty(key));
                    Properties.setProperty(key, val);
                    LOGGER.debug("Adding OpenAS2 properties file property: {} : {}", key, val);
                }

            } catch (FileNotFoundException e) {
                throw new OpenAS2Exception("Custom properties file specified but cannot be located:" + appPropsFile, e);
            } catch (IOException e) {
                LOGGER.warn("Custom properties file load failed:" + appPropsFile, e);
            } finally {
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (IOException e) {
                        LOGGER.warn("Failed to close properties file input stream.", e);
                    }
                }
            }
        }
        /* Process all loaded values in case they reference other properties in the value
           Use the properties object instead of Properties so we only parse the properties that were in the config.xml
           so that we can use system property values to replace config.xml properties.
         */
        // Pass "true" to ignore unmatched parse ID's in case the properties contain dynamic parameters needed for JIT evaluation
        CompositeParameters parser = new CompositeParameters(true);
        parser.setReturnParamStringForMissingParsers(true);
        for (Map.Entry<String, String> entry : Properties.getProperties().entrySet()) {
            String key = entry.getKey();
            String val = entry.getValue();
            LOGGER.debug("Parsing property: {} : {}", key, val);
            String parsedVal = ParameterParser.parse(val, parser);
            // Parser will return empty string if there is an unmatched parser ID in the string
            if (parsedVal.length() > 0 && !val.equals(parsedVal)) {
                LOGGER.debug("Overriding property with new parsed value: {} : {}", key, parsedVal);
                // Put the changed value into the Properties set
                Properties.setProperty(key, parsedVal);
            }
        }
        /* Put system properties in afterwards to avoid parsing embedded properties that may have 
           a $ sign in the value but only if the key does not exist.
        */
        @SuppressWarnings({ "unchecked", "rawtypes" })
        Map<String, String> sysProps = (Map)System.getProperties();
        for (Map.Entry<String, String> entry : sysProps.entrySet()) {
            String key = entry.getKey();
            if (Properties.getProperty(key, null) == null) {
                Properties.setProperty(key, entry.getValue());
            }
        }
        // Now check if we need to load Content-Type mappings
        String contentTypeMapFilename = Properties.getProperty(Partnership.PA_CONTENT_TYPE_MAPPING_FILE, null);
        if (contentTypeMapFilename != null) {
            Properties.setContentTypeMap(FileUtil.loadProperties(contentTypeMapFilename));
        }
    }

    private void loadCertificates(Node rootNode) throws OpenAS2Exception {
        CertificateFactory certFx = (CertificateFactory) XMLUtil.getComponent(rootNode, this);
        if (certFx == null) {
            // Must be disable so do nothing
            return;
        }
        String identifier = certFx.getIdentifier();
        setComponent(identifier, certFx);
    }

    private void loadBasePartnershipPollerConfig(Node node) throws OpenAS2Exception {
        this.basePollerConfigNode = node;
    }

    public Node getBasePartnershipPollerConfig() throws OpenAS2Exception {
        return this.basePollerConfigNode;
    }

   private void loadCommands(Node rootNode) throws OpenAS2Exception {
        Component component = XMLUtil.getComponent(rootNode, this);
        if (component == null) {
            // Must be disable so do nothing
            return;
        }
        commandRegistry = (CommandRegistry) component;
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
        if (cmdProcesor == null) {
            // Must be disable so do nothing
            return;
        }
        manager.addProcessor(cmdProcesor);

        setComponent(cmdProcesor.getName(), cmdProcesor);
    }

    private void loadPartnerships(Node rootNode) throws OpenAS2Exception {
        LOGGER.info("Loading partnerships...");

        PartnershipFactory partnerFx = (PartnershipFactory) XMLUtil.getComponent(rootNode, this);
        if (partnerFx == null) {
            // Must be disabled so do nothing
            return;
        }
        setComponent(PartnershipFactory.COMPID_PARTNERSHIP_FACTORY, partnerFx);
    }

    private void loadProcessor(Node rootNode) throws OpenAS2Exception {
        Processor proc = (Processor) XMLUtil.getComponent(rootNode, this);
        if (proc == null) {
            // Must be disable so do nothing
            return;
        }
        setComponent(Processor.COMPID_PROCESSOR, proc);

        LOGGER.info("Loading processor nodes...");

        NodeList processorChildNodes = rootNode.getChildNodes();
        Node processorChildNode;

        for (int i = 0; i < processorChildNodes.getLength(); i++) {
            processorChildNode = processorChildNodes.item(i);
            if (processorChildNode.getNodeName().equals("module")) {
                // Allow no enabled attrib to default to "true"
                String enabledFlag = XMLUtil.getNodeAttributeValue(processorChildNode, "enabled", true);
                if (enabledFlag != null && !"true".equalsIgnoreCase(enabledFlag)) {
                    try {
                        LOGGER.info("Module is disabled ... ignoring: " + XMLUtil.toString(processorChildNode, true));
                    } catch (TransformerException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    continue;
                }
                loadProcessorModule(proc, processorChildNode);
            } else {
                // Not a known node so ignore for loader
                continue;
            }
        }
    }

    private void loadProcessorModule(Processor proc, Node moduleNode) throws OpenAS2Exception {
        if (isPollerModule(moduleNode)) {
            // Special handling for poller modules using the old style polling config
            String partnershipName = null;
            Node defaultsNode = moduleNode.getAttributes().getNamedItem("defaults");
            if (defaultsNode == null) {
                // If there is a format node then this is a generic poller module
                Node formatNode = moduleNode.getAttributes().getNamedItem("format");
                if (formatNode == null) {
                    throw new OpenAS2Exception("Invalid poller module coniguration. Missing the \"format\" attribute in the module: " + moduleNode.getNodeName());
                }
                partnershipName = "generic";
            } else {
                // Since the partnerships will not have loaded yet, just use the defaults string as the partnership name
                partnershipName = defaultsNode.getNodeValue();
            }
            loadPartnershipPoller(moduleNode, partnershipName, Session.CONFIG_POLLER);
            return;
        }
        ProcessorModule procmod = (ProcessorModule) XMLUtil.getComponent(moduleNode, this);
        if (procmod == null) {
            // Must be disable so do nothing
            return;
        }
        proc.getModules().add(procmod);
    }

    private boolean isPollerModule(Node node) {
        Node classNameNode = node.getAttributes().getNamedItem("classname");
        if (classNameNode == null) {
            return false;
        }
        String className = classNameNode.getNodeValue();
        try {
            Class<?> objClass = Class.forName(className);
            if (PollingModule.class.isAssignableFrom(objClass)) {
                return true;
            }
        } catch (Exception e) {

        }
        return false;

    }
    private void getManifestAttributes() throws OpenAS2Exception {
        manifestAttributes = OpenAS2Server.getManifestAttributes();
    }

    public String getAppVersion() {
        if (VERSION == null) {
            try {
                VERSION = OpenAS2Server.getAppVersion(manifestAttributes);
            } catch (Exception e) {
                LOGGER.warn(e.getMessage());
            }
        }
        return VERSION;
    }

    public String getAppTitle() {
        if (TITLE == null) {
            try {
                TITLE = OpenAS2Server.getAppTitle(manifestAttributes);
            } catch (Exception e) {
                LOGGER.warn(e.getMessage());
            }
        }
        return TITLE;

    }

    private void loadMessages(Node rootNode) throws OpenAS2Exception {
        LOGGER.info("Loading messages...");
        MessageFactory messageFx = (MessageFactory) XMLUtil.getComponent(rootNode, this);
        if (messageFx == null) {
            // Must be disable so do nothing
            return;
        }
        setComponent(MessageFactory.COMPID_MESSAGE_FACTORY, messageFx);
    }
}
