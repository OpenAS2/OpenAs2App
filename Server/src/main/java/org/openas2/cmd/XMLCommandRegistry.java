package org.openas2.cmd;

import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.WrappedException;
import org.openas2.util.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;


public class XMLCommandRegistry extends BaseCommandRegistry {
    public static final String PARAM_FILENAME = "filename";

    public void init(Session session, Map<String, String> parameters) throws OpenAS2Exception {
        super.init(session, parameters);

        refresh();
    }

    public void load(InputStream in) throws ParserConfigurationException, SAXException, IOException, OpenAS2Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        DocumentBuilder parser = factory.newDocumentBuilder();
        Document document = parser.parse(in);
        Element root = document.getDocumentElement();
        NodeList rootNodes = root.getChildNodes();
        Node rootNode;
        String nodeName;

        getCommands().clear();

        for (int i = 0; i < rootNodes.getLength(); i++) {
            rootNode = rootNodes.item(i);

            nodeName = rootNode.getNodeName();

            if (nodeName.equals("command")) {
                loadCommand(rootNode, null);
            } else if (nodeName.equals("multicommand")) {
                loadMultiCommand(rootNode, null);
            }
        }
    }

    public void refresh() throws OpenAS2Exception {
        try {
            load(new FileInputStream(getParameter(PARAM_FILENAME, true)));
        } catch (Exception e) {
            throw new WrappedException(e);
        }
    }

    protected void loadCommand(Node node, MultiCommand parent) throws OpenAS2Exception {
        Command cmd = (Command) XMLUtil.getComponent(node, getSession());

        if (parent != null) {
            parent.getCommands().add(cmd);
        } else {
            getCommands().add(cmd);
        }
    }

    protected void loadMultiCommand(Node node, MultiCommand parent) throws OpenAS2Exception {
        MultiCommand cmd = new MultiCommand();
        cmd.init(getSession(), XMLUtil.mapAttributes(node));

        if (parent != null) {
            parent.getCommands().add(cmd);
        } else {
            getCommands().add(cmd);
        }

        NodeList childCmds = node.getChildNodes();

        Node childNode;
        String childName;

        for (int i = 0; i < childCmds.getLength(); i++) {
            childNode = childCmds.item(i);

            childName = childNode.getNodeName();

            if (childName.equals("command")) {
                loadCommand(childNode, cmd);
            } else if (childName.equals("multicommand")) {
                loadMultiCommand(childNode, cmd);
            }
        }
    }
}
