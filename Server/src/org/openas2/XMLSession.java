package org.openas2;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.openas2.cert.CertificateFactory;
import org.openas2.cmd.CommandManager;
import org.openas2.cmd.CommandRegistry;
import org.openas2.cmd.CommandRegistryFactory;
import org.openas2.cmd.processor.BaseCommandProcessor;
import org.openas2.logging.LogManager;
import org.openas2.logging.Logger;
import org.openas2.partner.PartnershipFactory;
import org.openas2.processor.Processor;
import org.openas2.processor.ProcessorModule;
import org.openas2.util.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * original author unknown
 * 
 * in this release added command registry methods
 * @author joseph mcverry
 *
 */
public class XMLSession extends BaseSession implements CommandRegistryFactory {
	public static final String EL_CERTIFICATES = "certificates";
	public static final String EL_CMDPROCESSOR = "commandProcessors";
	public static final String EL_PROCESSOR = "processor";
	public static final String EL_PARTNERSHIPS = "partnerships";
	public static final String EL_COMMANDS = "commands";
	public static final String EL_LOGGERS = "loggers";
	public static final String PARAM_BASE_DIRECTORY = "basedir";
	private CommandRegistry commandRegistry;
	private String baseDirectory;
	private CommandManager cmdManager;


	
	public XMLSession(InputStream in) throws OpenAS2Exception,
			ParserConfigurationException, SAXException, IOException {
		super();
		load(in);
	}

	public XMLSession(String filename) throws OpenAS2Exception,
			ParserConfigurationException, SAXException, IOException {
		File file = new File(filename).getAbsoluteFile();
		setBaseDirectory(file.getParent());
		FileInputStream fin = new FileInputStream(file);
		try {
			load(fin);
		} finally {
			fin.close();
		}
	}

	public void setCommandRegistry(CommandRegistry registry) {
		commandRegistry = registry;
	}

	public CommandRegistry getCommandRegistry() {
		return commandRegistry;
	}

	protected void load(InputStream in) throws ParserConfigurationException,
			SAXException, IOException, OpenAS2Exception {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

		DocumentBuilder parser = factory.newDocumentBuilder();
		Document document = parser.parse(in);
		Element root = document.getDocumentElement();

		NodeList rootNodes = root.getChildNodes();
		Node rootNode;
		String nodeName;

		for (int i = 0; i < rootNodes.getLength(); i++) {
			rootNode = rootNodes.item(i);

			nodeName = rootNode.getNodeName();

			if (nodeName.equals(EL_CERTIFICATES)) {
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
	}

	protected void loadCertificates(Node rootNode) throws OpenAS2Exception {
		CertificateFactory certFx = (CertificateFactory) XMLUtil.getComponent(
				rootNode, this);
		setComponent(CertificateFactory.COMPID_CERTIFICATE_FACTORY, certFx);
	}

	protected void loadCommands(Node rootNode) throws OpenAS2Exception {
		CommandRegistry cmdReg = (CommandRegistry) XMLUtil.getComponent(
				rootNode, this);
		setCommandRegistry(cmdReg);
	}

	protected void loadLoggers(Node rootNode) throws OpenAS2Exception {
		
		LogManager manager = LogManager.getLogManager();
		if (LogManager.isRegisteredWithApache())
			; // continue
		else {
			// if using the OpenAS2 loggers the log manager must registered with the jvm argument
			// -Dorg.apache.commons.logging.Log=org.openas2.logging.Log
			throw new OpenAS2Exception("the OpenAS2 loggers' log manager must registered with the jvm argument -Dorg.apache.commons.logging.Log=org.openas2.logging.Log");
		}
		NodeList loggers = rootNode.getChildNodes();
		Node logger;

		for (int i = 0; i < loggers.getLength(); i++) {
			logger = loggers.item(i);

			if (logger.getNodeName().equals("logger")) {
				loadLogger(manager, logger);
			}
		}
	}

	protected void loadLogger(LogManager manager, Node loggerNode)
			throws OpenAS2Exception {
		Logger logger = (Logger) XMLUtil.getComponent(loggerNode, this);
		manager.addLogger(logger);
	}

	protected void loadCommandProcessors(Node rootNode) throws OpenAS2Exception {
		cmdManager = CommandManager.getCmdManager();

		NodeList cmdProcessor = rootNode.getChildNodes();
		Node processor;

		for (int i = 0; i < cmdProcessor.getLength(); i++) {
			processor = cmdProcessor.item(i);

			if (processor.getNodeName().equals("commandProcessor")) {
				loadCommandProcessor(cmdManager, processor);
			}
		}
	}

	public CommandManager getCommandManager() {
		return cmdManager;
	}

	protected void loadCommandProcessor(CommandManager manager,
			Node cmdPrcessorNode) throws OpenAS2Exception {
		BaseCommandProcessor cmdProcesor = (BaseCommandProcessor) XMLUtil
				.getComponent(cmdPrcessorNode, this);
		manager.addProcessor(cmdProcesor);
	}
	protected void loadPartnerships(Node rootNode) throws OpenAS2Exception {
		PartnershipFactory partnerFx = (PartnershipFactory) XMLUtil
				.getComponent(rootNode, this);
		setComponent(PartnershipFactory.COMPID_PARTNERSHIP_FACTORY, partnerFx);
	}

	protected void loadProcessor(Node rootNode) throws OpenAS2Exception {
		Processor proc = (Processor) XMLUtil.getComponent(rootNode, this);
		setComponent(Processor.COMPID_PROCESSOR, proc);

		NodeList modules = rootNode.getChildNodes();
		Node module;

		for (int i = 0; i < modules.getLength(); i++) {
			module = modules.item(i);

			if (module.getNodeName().equals("module")) {
				loadProcessorModule(proc, module);
			}
		}
	}

	protected void loadProcessorModule(Processor proc, Node moduleNode)
			throws OpenAS2Exception {
		ProcessorModule procmod = (ProcessorModule) XMLUtil.getComponent(
				moduleNode, this);
		proc.getModules().add(procmod);
	}

	public String getBaseDirectory() {
		return baseDirectory;
	}

	public void setBaseDirectory(String dir) {
		baseDirectory = dir;
	}

}
