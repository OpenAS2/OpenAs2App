package org.openas2.partner;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.WrappedException;
import org.openas2.XMLSession;
import org.openas2.params.InvalidParameterException;
import org.openas2.schedule.HasSchedule;
import org.openas2.support.FileMonitorAdapter;
import org.openas2.util.AS2Util;
import org.openas2.util.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
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
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * original author unknown
 * <p>
 * this release added logic to store partnerships and provide methods for partner/partnership command line processor
 *
 * @author joseph mcverry
 */
public class XMLPartnershipFactory extends BasePartnershipFactory implements HasSchedule {

    public static final String PARAM_FILENAME = "filename";
    public static final String PARAM_INTERVAL = "interval";

    private Document partnershipsXml = null;


    private Map<String, Object> partners;

    private Log logger = LogFactory.getLog(XMLPartnershipFactory.class.getSimpleName());


    private int getRefreshInterval() throws InvalidParameterException {
        return getParameterInt(PARAM_INTERVAL, false);
    }

    String getFilename() throws InvalidParameterException {
        return getParameter(PARAM_FILENAME, true);
    }

    public Map<String, Object> getPartners() {
        if (partners == null) {
            partners = new HashMap<String, Object>();
        }

        return partners;
    }

    private void setPartners(Map<String, Object> map) {
        partners = map;
    }

    public void init(Session session, Map<String, String> parameters) throws OpenAS2Exception {
        super.init(session, parameters);

        refresh();
    }

    void refresh() throws OpenAS2Exception {
        loadPartnershipsFile();
        refreshConfig();
    }


    void loadPartnershipsFile() throws OpenAS2Exception {
        try (FileInputStream inputStream = new FileInputStream(getFilename())) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

            DocumentBuilder parser = factory.newDocumentBuilder();
            Document document = parser.parse(inputStream);
            setPartnershipsXml(document);
        } catch (Exception e) {
            throw new WrappedException(e);
        }
    }

    void refreshConfig() throws OpenAS2Exception {
        getSession().destroyPartnershipPollers(Session.PARTNERSHIP_POLLER);
        try {
            Element root = getPartnershipsXml().getDocumentElement();
            NodeList rootNodes = root.getChildNodes();
            Node rootNode;
            String nodeName;

            Map<String, Object> newPartners = new HashMap<String, Object>();
            List<Partnership> newPartnerships = new ArrayList<Partnership>();

            for (int i = 0; i < rootNodes.getLength(); i++) {
                rootNode = rootNodes.item(i);

                nodeName = rootNode.getNodeName();

                if (nodeName.equals("partner")) {
                    loadPartner(newPartners, rootNode);
                } else if (nodeName.equals("partnership")) {
                    loadPartnership(newPartners, newPartnerships, rootNode);
                }
            }

            synchronized (this) {
                setPartners(newPartners);
                setPartnerships(newPartnerships);
            }
        } catch (Exception e) {
            throw new WrappedException(e);
        }
    }

    private void loadAttributes(Node node, Partnership partnership) throws OpenAS2Exception {
        Map<String, String> attributes = XMLUtil.mapAttributeNodes(node.getChildNodes(), "attribute", "name", "value");

        AS2Util.attributeEnhancer(attributes);
        partnership.getAttributes().putAll(attributes);
    }

    public void loadPartner(Map<String, Object> partners, Node node) throws OpenAS2Exception {
        String[] requiredAttributes = {Partnership.PID_NAME};

        Map<String, String> newPartner = XMLUtil.mapAttributes(node, requiredAttributes);
        String name = newPartner.get(Partnership.PID_NAME);

        if (partners.get(name) != null) {
            throw new OpenAS2Exception("Partner is defined more than once: " + name);
        }

        partners.put(name, newPartner);
    }


    private void loadPartnerIDs(Map<String, Object> partners, String partnershipName, Node partnershipNode, String partnerType, Map<String, Object> idMap) throws OpenAS2Exception {
        Node partnerNode = XMLUtil.findChildNode(partnershipNode, partnerType);

        if (partnerNode == null) {
            throw new OpenAS2Exception("Partnership \"" + partnershipName + "\" is missing a node entry for the " +  partnerType + ".");
        }

        Map<String, String> partnerAttr = XMLUtil.mapAttributes(partnerNode);

        // check for a partner name, and look up in partners list if one is found
        String partnerName = partnerAttr.get(Partnership.PID_NAME);

        if (partnerName != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) partners.get(partnerName);
            Map<String, Object> partner = map;

            if (partner == null) {
                throw new OpenAS2Exception("Partnership " + partnershipName + " has an undefined " + partnerType + ": " + partnerName);
            }

            idMap.putAll(partner);
        }

        // copy all other attributes to the partner id map
        idMap.putAll(partnerAttr);
    }

    public void loadPartnership(Map<String, Object> partners, List<Partnership> partnerships, Node node) throws OpenAS2Exception {
        Partnership partnership = new Partnership();
        String[] requiredAttributes = {"name"};

        Map<String, String> psAttributes = XMLUtil.mapAttributes(node, requiredAttributes);
        String name = psAttributes.get("name");

        if (getPartnership(partnerships, name) != null) {
            throw new OpenAS2Exception("Partnership is defined more than once: " + name);
        }

        partnership.setName(name);

        // load the sender and receiver information
        loadPartnerIDs(partners, name, node, Partnership.PTYPE_SENDER, partnership.getSenderIDs());
        loadPartnerIDs(partners, name, node, Partnership.PTYPE_RECEIVER, partnership.getReceiverIDs());

        // read in the partnership attributes
        loadAttributes(node, partnership);
        // Now check if we need to enable Content-Type mappings for this partnership
        if ("true".equalsIgnoreCase(partnership.getAttributeOrProperty(Partnership.PA_USE_DYNAMIC_CONTENT_TYPE_MAPPING, "false"))) {
            try {
                partnership.setUseDynamicContentTypeLookup(true);
            } catch (IOException e) {
                logger.error("Error setting up dynamic Content-Type lookup: " + e.getMessage(), e);
                throw new OpenAS2Exception("Partnership failed to be set up correctly for dynamic Content-Type lookup: " + getName());
            }
        }
        // add the partnership to the list of available partnerships
        partnerships.add(partnership);
        
        // Now check if we need to add a directory polling module
        Node pollerCfgNode = XMLUtil.findChildNode(node, Partnership.PCFG_POLLER);
        if (pollerCfgNode != null) {
            /* Load a poller configuration.
             * This will require fetching the base configuration for the pollers loaded from
             * the config.xml and merging with the configured setup in the partnership 
             * overriding the base attribute values with any found in the partnership
             * pollerConfig element then enhancing the attribute values to cater for embedded
             * dynamic variables before activating the poller.
             */
            String[] requiredPollerAttributes = {"enabled"};
            Map<String, String> partnershipPollerCfgAttributes = XMLUtil.mapAttributes(pollerCfgNode, requiredPollerAttributes);
            if ("true".equalsIgnoreCase(partnershipPollerCfgAttributes.get("enabled"))) {
                if (logger.isTraceEnabled()) {
                        logger.trace("Found partnership poller for partnership: " + name);
                }
                // Create a copy of the base config node
                Node basePollerConfigNode = ((XMLSession)getSession()).getBasePartnershipPollerConfig();
                if (basePollerConfigNode == null) {
                    throw new OpenAS2Exception("Missing base poller config node in config.xml to configure partnership poller.");
                }
                Document pollerDoc;
                try {
                    pollerDoc = XMLUtil.createDoc(basePollerConfigNode);
                } catch (Exception e) {
                    throw new OpenAS2Exception("Failed to create a poller document: " + e.getMessage(), e);
                }
                Element pollerConfigElem = pollerDoc.getDocumentElement();
                // Merge the attributes from the base config with the partnership specific ones
                Map<String, String> attributes = XMLUtil.mapAttributes(pollerConfigElem);
                attributes.putAll(partnershipPollerCfgAttributes);
                // Enhance the attribute values in case they are using dynamic variables
                AS2Util.attributeEnhancer(attributes);
                // Now update the XML with the attribute values
                attributes.forEach((key, value) -> {
                    pollerConfigElem.setAttribute(key, value);
                }); 
                // replace the $partnertship.* placeholders
                replacePartnershipPlaceHolders(pollerDoc, partnership);
                // Now launch a directory poller module for this config
                getSession().loadPartnershipPoller(pollerConfigElem, name, Session.PARTNERSHIP_POLLER);
            }
        }
    }

    /**
     * Appends the passed element as a child of the root in the partnership document.
     * It does NOT check if the passed element is a valid element.
     * @param newElement - the element to be added.
     */
    public void addElement(Element newElement) {
        Document doc = getPartnershipsXml();
        Node importedNode = doc.importNode(newElement, true);
        doc.getDocumentElement().appendChild(importedNode);
    }

    /**
     * Appends the passed element as a child of the root in the partnership document.
     * It does NOT check if the passed element is a valid element.
     * @param newElement - the element to be added.
     */
    public boolean deleteElement(String xpath) {
        Document doc = getPartnershipsXml();
        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList nodes;
        try {
            nodes = (NodeList)xPath.evaluate(xpath, doc, XPathConstants.NODESET);
        } catch (XPathExpressionException e) {
            logger.error("Error trying to find any nodes in the XPATH expression: " + xpath, e);
            return false;
        }
        int nodeCount = nodes.getLength();
        if (nodeCount == 0) {
            logger.error(" Failed to find a node using XPATH expression: " + xpath);
            return false;
        } else if (nodeCount > 1) {
            logger.error(" Delete aborted. More than 1 node found using XPATH expression: " + xpath);
            return false;
        }
        nodes.item(0).getParentNode().removeChild(nodes.item(0));
        return true;
    }

    public void storePartnership() throws OpenAS2Exception {
        String fn = getFilename();

        DecimalFormat df = new DecimalFormat("0000000");
        long l = 0;
        File f = null;
        while (true) {
            f = new File(fn + '.' + df.format(l));
            if (f.exists() == false) {
                break;
            }
            l++;
        }

        logger.info("Backing up " + fn + " to " + f.getName());

        File fr = new File(fn);
        fr.renameTo(f);

        try (FileWriter writer = new FileWriter(new File(getFilename()))) {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
            DOMSource source = new DOMSource(getPartnershipsXml());
            StreamResult result = new StreamResult(writer);
            transformer.transform(source, result);
        } catch (IOException | TransformerException e) {
            throw new WrappedException(e);
        }
    }

    @Override
    public void schedule(ScheduledExecutorService executor) throws OpenAS2Exception {
        new FileMonitorAdapter() {
            @Override
            public void onConfigFileChanged() throws OpenAS2Exception {
                logger.info("Partnerships file change detected. Starting refresh...");
                refresh();
                getSession().startPartnershipPollers();
                logger.info("Partnerships file change detected - Partnerships Reloaded");
            }
        }.scheduleIfNeed(executor, new File(getFilename()), getRefreshInterval(), TimeUnit.SECONDS);
    }

    public Document getPartnershipsXml() {
        return partnershipsXml;
    }

    public void setPartnershipsXml(Document partnershipsXml) {
        this.partnershipsXml = partnershipsXml;
    }

    public void replacePartnershipPlaceHolders(Document doc, Partnership partnership) throws OpenAS2Exception {
        String xpathExpression = "//*[@*[contains(.,'$partnership.')]]/@*";
        // Create XPathFactory object
        XPathFactory xpathFactory = XPathFactory.newInstance();
        // Create XPath object
        XPath xpath = xpathFactory.newXPath();
        try {
            // Create XPathExpression object
            XPathExpression expr = xpath.compile(xpathExpression);
            // Evaluate expression result on XML document
            NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);
            Pattern PATTERN = Pattern.compile("\\$partnership\\.([^\\$]++)\\$");

            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                String val = node.getNodeValue();
                //logger.debug("Partnership place holder NODE processing: " + val);
                StringBuffer strBuf = new StringBuffer();
                Matcher matcher = PATTERN.matcher(val);
                boolean hasChanged = false;
                while (matcher.find()) {
                    String value = null;
                    String[] keys = matcher.group(1).split("\\.");
                    if (keys.length == 1) {
                        switch (keys[0]) {
                        case "name":
                            value = partnership.getName();
                            break;
                        default:
                            throw new OpenAS2Exception(
                                    "The partnership placeholder cannot be resolved: " + keys[0] + " in " + val);
                        }
                    } else if (keys.length == 2) {
                        switch (keys[0]) {
                        case "receiver":
                            value = partnership.getReceiverID(keys[1]);
                            break;
                        case "sender":
                            value = partnership.getSenderID(keys[1]);
                            break;
                        default:
                            throw new OpenAS2Exception(
                                    "The partnership placeholder cannot be resolved: " + keys[0] + " in " + val);
                        }
                    } else {
                        // don't know how to handle this
                        throw new OpenAS2Exception(
                                "The partnership placeholder is invalid and cannot be parsed: " + val);
                    }
                    if (value == null) {
                        throw new OpenAS2Exception(
                                "Missing attribute value for replacement: " + matcher.group() + " in " + val);
                    } else {
                        hasChanged = true;
                        matcher.appendReplacement(strBuf, Matcher.quoteReplacement(value));
                        if (logger.isTraceEnabled()) {
                            logger.trace("Partnership place holder replaced: " + keys + " :: Replaced with: " + value);
                        }
                    }
                }
                if (hasChanged) {
                    matcher.appendTail(strBuf);
                    node.setNodeValue(strBuf.toString());
                }
            }

        } catch (XPathExpressionException e) {
            e.printStackTrace();
        }
    }
}
