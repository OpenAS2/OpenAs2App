package org.openas2.upgrades;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringReader;
import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.openas2.WrappedException;
import org.openas2.XMLSession;
import org.openas2.util.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.XMLFilterImpl;


public class MigratePollingModuleConfig {
    static Document cfgDoc = null;
    static Document partnershipDoc = null;
    static NamedNodeMap basePollerAttribs = null;
    static boolean hasHomeInConfig = false;

    static Map<String, String> extractParameters(String encodedParams) throws Exception {
        StringTokenizer params = new StringTokenizer(encodedParams, "=,", false);
        String key;
        Map<String, String>  map = new HashMap<String, String>();
        //System.out.println("Procesing defaults parameter: " + encodedParams);
        //System.out.println("Tokens found: " + params.countTokens());

        while (params.hasMoreTokens()) {
            key = params.nextToken().trim();
            if (!params.hasMoreTokens()) {
                throw new Exception("Invalid value for poller defaults param: " + encodedParams);
            }
            String value = params.nextToken();
            System.out.println("Token: " + key + " = " + value);
            map.put(key, value);
        }
        return map;
    }

    static void addPollerConfigToConfigXml() throws Exception {
        final String xmlStr = "<pollerConfigBase classname=\"org.openas2.processor.receiver.AS2DirectoryPollingModule\"\n"
                + "           outboxdir=\"$properties.storageBaseDir$/outbox/$partnership.receiver.as2_id$\"\n"
                + "           errordir=\"$properties.storageBaseDir$/outbox/error/$date.YYYY$-$date.MM$-$date.dd/$partnership.receiver.as2_id$\"\n"
                + "           interval=\"5\"\n"
                + "           defaults=\"sender.as2_id=$partnership.sender.as2_id$, receiver.as2_id=$partnership.receiver.as2_id$\"\n"
                + "           sendfilename=\"true\"\n"
                + "           mimetype=\"application/EDI-X12\"/>";
        Document pollerCfgDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new InputSource(new StringReader(xmlStr)));
        Node pollerNode = cfgDoc.importNode(pollerCfgDoc.getDocumentElement(), true);
        basePollerAttribs = pollerNode.getAttributes();
        Element root = cfgDoc.getDocumentElement();
        root.appendChild(pollerNode);
        NodeList nodeList = cfgDoc.getElementsByTagName(XMLSession.EL_PARTNERSHIPS);
        if (nodeList.getLength() != 1) {
            throw new Exception("More than 1 " + XMLSession.EL_PARTNERSHIPS + " element found in config.xml file.");
        }
        Element partnershipNode = (Element)nodeList.item(0);
        root.removeChild(partnershipNode);
        root.appendChild(partnershipNode);
    }
    
    static String getPartnerName(String as2Id) throws Exception {
        String partnerXpath = "//partner[@as2_id='" + as2Id + "']/@name";
        //System.out.println("Using Xpath for partner lookup: " + partnerXpath);
        XPathExpression partnerExpr = XPathFactory.newInstance().newXPath().compile(partnerXpath);
        String partnerName = (String) partnerExpr.evaluate(partnershipDoc, XPathConstants.STRING);
        if (partnerName.length() < 1) {
            System.out.println("WARNING: Cannot find a matching partnership for AS2 ID: " + as2Id);
            return null;
        }
        return partnerName;
    }

    static void movePollerConfigToPartnershipsXml() throws Exception {
        final String xmlStr = "<pollerConfig enabled=\"true\"/>";
        Document pollerConfigDoc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new InputSource(new StringReader(xmlStr)));
        String srcPollerXpath = "//module[@classname='org.openas2.processor.receiver.AS2DirectoryPollingModule']";
        // Create XPath object
        XPath xpath = XPathFactory.newInstance().newXPath();
        // Create XPathExpression object
        XPathExpression expr = xpath.compile(srcPollerXpath);
        // Evaluate expression result on XML document
        NodeList cfgFilePollerNodes = (NodeList) expr.evaluate(cfgDoc, XPathConstants.NODESET);
        System.out.println("Found " + cfgFilePollerNodes.getLength() + " poller nodes in config XML file ...");
        for (int i=0; i < cfgFilePollerNodes.getLength(); i++) {
            Node cfgFilePollerNode = cfgFilePollerNodes.item(i);
            NamedNodeMap cfgFilePollerAttribs = cfgFilePollerNode.getAttributes();
            Node formatNode = cfgFilePollerAttribs.getNamedItem("format");
            if (formatNode != null && formatNode.getNodeValue().length() > 0) {
                // Ignore a generic poller
                System.out.println("Ignoring generic poller node in config XML file ...");
                continue;
            }
            String partnerDef = ((Element)cfgFilePollerNode).getAttribute("defaults");
            //System.out.println(XMLUtil.toString(cfgFilePollerNode, true));
            Map<String, String>  pollerMap = extractParameters(partnerDef);
            String senderAS2Id = pollerMap.get("sender.as2_id");
            String receiverAS2Id = pollerMap.get("receiver.as2_id");
            String partnershipXpath = "//partnership[sender[@name='" + getPartnerName(senderAS2Id) + "'] and receiver[@name='" + getPartnerName(receiverAS2Id) + "']]";
            System.out.println("Using Xpath for partnership lookup: " + partnershipXpath);
            XPathExpression partnershipExpr = xpath.compile(partnershipXpath);
            NodeList partnershipNodes = (NodeList) partnershipExpr.evaluate(partnershipDoc, XPathConstants.NODESET);
            if (partnershipNodes.getLength() > 1) {
                System.out.println("\nWARNING: Cannot find a matching partnership for : \n\t" + XMLUtil.toString(cfgFilePollerNode, true));
                System.out.println("The node will be removed and no poller configured for this node.");
                cfgFilePollerNode.getParentNode().removeChild(cfgFilePollerNode);
                continue;
            }
            Node partnershipNode = partnershipNodes.item(0);
            Node newPollerNode = partnershipDoc.importNode(pollerConfigDoc.getDocumentElement(), true);
            Element elem = (Element)newPollerNode;
            for (int j=0; j<cfgFilePollerAttribs.getLength(); j++) {
                Node attrib = cfgFilePollerAttribs.item(j);
                String name = attrib.getNodeName();
                if ("defaults".equals(name) || "format".equals(name)) {
                    continue;
                }
                String value = attrib.getNodeValue();
                Node basePollerAttribNode = basePollerAttribs.getNamedItem(name);
                if (basePollerAttribNode != null && basePollerAttribNode.getNodeValue().equals(value)) {
                    // Already set to the same value in the base config so ignore
                    continue;
                }
                if (value.startsWith("%home%")) {
                    hasHomeInConfig = true;
                }
                elem.setAttribute(name, value);
            }
            partnershipNode.appendChild(newPollerNode);
            cfgFilePollerNode.getParentNode().removeChild(cfgFilePollerNode);
            //System.out.println("Moved poller node in config XML file  to partnership file...");
        }
    }

    static void storeXmlDoc(File target, Document doc) throws Exception {
        DecimalFormat df = new DecimalFormat("00");
        String tgtFileName = target.getAbsolutePath();
        long l = 0;
        File f = null;
        while (true) {
            f = new File(tgtFileName + '.' + df.format(l));
            if (f.exists() == false) {
                break;
            }
            l++;
        }

        System.out.println("Backing up " + tgtFileName + " to " + f.getName());

        target.renameTo(f);

        try (FileWriter writer = new FileWriter(new File(tgtFileName))) {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            DOMSource source = new DOMSource(doc);
            StreamResult result = new StreamResult(writer);
            transformer.transform(source, result);
        } catch (IOException | TransformerException e) {
            throw new WrappedException(e);
        }
    }

    public static void main(String[] args) throws Exception {
        FileInputStream configAsStream = null;
        FileInputStream partnershipAsStream = null;
        try {
            if (args == null || args.length != 2) {
                System.out.println("Requires 2 arguments: <source config.xml file> <source partnership.xml file>");
                System.exit(1);
            }
            File configInFile = new File(args[0]);
            if (!configInFile.exists()) {
                System.out.println("No source config file found: " + args[0]);
                System.exit(1);
            }
            File partnershipInFile = new File(args[1]);
            if (!partnershipInFile.exists()) {
                System.out.println("No source partnership file found: " + args[1]);
                System.exit(1);
            }
            XMLFilterImpl xmlFilter = new XMLFilterImpl();
            configAsStream = new FileInputStream(configInFile);
            cfgDoc = XMLUtil.parseXML(configAsStream, xmlFilter);
            partnershipAsStream = new FileInputStream(partnershipInFile);
            partnershipDoc = XMLUtil.parseXML(partnershipAsStream, xmlFilter);
            System.out.println("Modifying " + args[0] + " to include base poller config and move partnersnips to last element...");
            addPollerConfigToConfigXml();
            System.out.println("Moving declared poller config from " + args[0] + " to " + args[1] + " ...");
            movePollerConfigToPartnershipsXml();
            System.out.println("Writing " + args[0] + " ...");
            storeXmlDoc(configInFile, cfgDoc);
            System.out.println("Writing " + args[1] + " ..."); 
            storeXmlDoc(partnershipInFile, partnershipDoc);
            System.out.println("Configuration has been ugraded.");
            if (hasHomeInConfig) {
                System.out.println("\n\nWARNING: The poller config migrated to the partnership but 1 or more attributes contain the string \"%home%\" which must be replaced with a property or a fixed string.");
            }
       } finally {
            if (configAsStream != null) {
                configAsStream.close();
            }
            if (partnershipAsStream != null) {
                partnershipAsStream.close();
            }
        }
    }


}
