package org.openas2;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

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
    //private static final String PARAM_BASE_DIRECTORY = "basedir";

    private CommandRegistry commandRegistry;
    private CommandManager cmdManager = new CommandManager();

    private String VERSION;
    private String TITLE;

    private static final Log LOGGER = LogFactory.getLog(XMLSession.class.getSimpleName());

    public XMLSession(String configAbsPath) throws Exception
    {
        File configXml = new File(configAbsPath);
        File configDir = configXml.getParentFile();

        FileInputStream configAsStream = new FileInputStream(configXml);
        setBaseDirectory(configDir.getAbsolutePath());

        load(configAsStream);

        // scheduler should be initializer after all modules
        addSchedulerComponent();
    }

    private void addSchedulerComponent() throws OpenAS2Exception
    {
        SchedulerComponent comp = new SchedulerComponent();
        setComponent("scheduler", comp);
        comp.init(this, Collections.<String, String>emptyMap());
    }


    protected void load(InputStream in) throws Exception
    {
        Document document = XMLUtil.parseXML(in, new PropertyReplacementFilter());

        Element root = document.getDocumentElement();

        NodeList rootNodes = root.getChildNodes();
        Node rootNode;
        String nodeName;

        // this is used by all other objects to access global configs and functionality
        LOGGER.info("Loading configuration...");
        for (int i = 0; i < rootNodes.getLength(); i++)
        {
            rootNode = rootNodes.item(i);

            nodeName = rootNode.getNodeName();

            // enter the command processing loop
            if (nodeName.equals(EL_PROPERTIES))
            {
                loadProperties(rootNode);
            } else if (nodeName.equals(EL_CERTIFICATES))
            {
                loadCertificates(rootNode);
            } else if (nodeName.equals(EL_PROCESSOR))
            {
                loadProcessor(rootNode);
            } else if (nodeName.equals(EL_CMDPROCESSOR))
            {
                loadCommandProcessors(rootNode);
            } else if (nodeName.equals(EL_PARTNERSHIPS))
            {
                loadPartnerships(rootNode);
            } else if (nodeName.equals(EL_COMMANDS))
            {
                loadCommands(rootNode);
            } else if (nodeName.equals(EL_LOGGERS))
            {
                loadLoggers(rootNode);
            } else if (nodeName.equals("#text"))
            {
                // do nothing
            } else if (nodeName.equals("#comment"))
            {
                // do nothing
            } else
            {
                throw new OpenAS2Exception("Undefined tag: " + nodeName);
            }
        }

        cmdManager.registerCommands(commandRegistry);
    }

    private void loadProperties(Node propNode)
    {
        LOGGER.info("Loading properties...");

        Map<String, String> properties = XMLUtil.mapAttributes(propNode, false);
        // Make key things accessible via static object for things that do not have accesss to session object
        properties.put(Properties.APP_TITLE_PROP, getAppTitle());
        properties.put(Properties.APP_VERSION_PROP, getAppVersion());
        Properties.setProperties(properties);
    }

    private void loadCertificates(Node rootNode) throws OpenAS2Exception
    {
        CertificateFactory certFx = (CertificateFactory) XMLUtil.getComponent(
                rootNode, this);
        setComponent(CertificateFactory.COMPID_CERTIFICATE_FACTORY, certFx);
    }

    private void loadCommands(Node rootNode) throws OpenAS2Exception
    {
        Component component = XMLUtil.getComponent(rootNode, this);
        commandRegistry = (CommandRegistry) component;
    }

    private void loadLoggers(Node rootNode) throws OpenAS2Exception
    {
        LOGGER.info("Loading log manager(s)...");

        LogManager manager = LogManager.getLogManager();
        if (LogManager.isRegisteredWithApache())
        {
            ; // continue
        } else
        {
            // if using the OpenAS2 loggers the log manager must registered with the jvm argument
            // -Dorg.apache.commons.logging.Log=org.openas2.logging.Log
            throw new OpenAS2Exception("the OpenAS2 loggers' log manager must be registered with the jvm argument -Dorg.apache.commons.logging.Log=org.openas2.logging.Log");
        }
        NodeList loggers = rootNode.getChildNodes();
        Node logger;

        for (int i = 0; i < loggers.getLength(); i++)
        {
            logger = loggers.item(i);

            if (logger.getNodeName().equals("logger"))
            {
                loadLogger(manager, logger);
            }
        }
    }

    private void loadLogger(LogManager manager, Node loggerNode)
            throws OpenAS2Exception
    {
        Logger logger = (Logger) XMLUtil.getComponent(loggerNode, this);
        manager.addLogger(logger);
    }

    private void loadCommandProcessors(Node rootNode) throws OpenAS2Exception
    {

        // get a registry of Command objects, and add Commands for the Session
        LOGGER.info("Loading command processor(s)...");

        NodeList cmdProcessor = rootNode.getChildNodes();
        Node processor;

        for (int i = 0; i < cmdProcessor.getLength(); i++)
        {
            processor = cmdProcessor.item(i);

            if (processor.getNodeName().equals("commandProcessor"))
            {
                loadCommandProcessor(cmdManager, processor);
            }
        }
    }

    private void loadCommandProcessor(CommandManager manager,
                                      Node cmdPrcessorNode) throws OpenAS2Exception
    {
        BaseCommandProcessor cmdProcesor = (BaseCommandProcessor) XMLUtil
                .getComponent(cmdPrcessorNode, this);
        manager.addProcessor(cmdProcesor);

        setComponent(cmdProcesor.getName(), cmdProcesor);
    }

    private void loadPartnerships(Node rootNode) throws OpenAS2Exception
    {
        LOGGER.info("Loading partnerships...");

        PartnershipFactory partnerFx = (PartnershipFactory) XMLUtil
                .getComponent(rootNode, this);
        setComponent(PartnershipFactory.COMPID_PARTNERSHIP_FACTORY, partnerFx);
    }

    private void loadProcessor(Node rootNode) throws OpenAS2Exception
    {
        Processor proc = (Processor) XMLUtil.getComponent(rootNode, this);
        setComponent(Processor.COMPID_PROCESSOR, proc);

        LOGGER.info("Loading processor modules...");

        NodeList modules = rootNode.getChildNodes();
        Node module;

        for (int i = 0; i < modules.getLength(); i++)
        {
            module = modules.item(i);

            if (module.getNodeName().equals("module"))
            {
                loadProcessorModule(proc, module);
            }
        }
    }

    private void loadProcessorModule(Processor proc, Node moduleNode)
            throws OpenAS2Exception
    {
        ProcessorModule procmod = (ProcessorModule) XMLUtil.getComponent(
                moduleNode, this);
        proc.getModules().add(procmod);
    }

    @Nullable
    private String getManifestAttribValue(@Nonnull String attrib)
    {
        Enumeration<?> resEnum;
        try
        {
            resEnum = Thread.currentThread().getContextClassLoader().getResources(JarFile.MANIFEST_NAME);
            while (resEnum.hasMoreElements())
            {
                try
                {
                    URL url = (URL) resEnum.nextElement();
                    if (!url.getPath().contains("openas2"))
                    {
                        continue;
                    }
                    InputStream is = url.openStream();
                    if (is != null)
                    {
                        Manifest manifest = new Manifest(is);
                        Attributes mainAttribs = manifest.getMainAttributes();
                        String value = mainAttribs.getValue(attrib);
                        if (value != null)
                        {
                            return value;
                        }
                    }
                } catch (Exception e)
                {
                    // Silently ignore wrong manifests on classpath?
                }
            }
        } catch (IOException e1)
        {
            // Silently ignore wrong manifests on classpath?
        }
        return null;
    }

    public String getAppVersion()
    {
        if (VERSION == null)
        {
            VERSION = getManifestAttribValue("Implementation-Version");
        }
        return VERSION;
    }

    public String getAppTitle()
    {
        if (TITLE == null)
        {
            TITLE = getManifestAttribValue("Implementation-Title") + " v" + getAppVersion();
        }
        return TITLE;

    }
}
