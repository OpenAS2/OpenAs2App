package org.openas2.partner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private Logger logger = LoggerFactory.getLogger(XMLPartnershipFactory.class);


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
        Map<String, Object> newPartners = new HashMap<String, Object>();
        List<Partnership> newPartnerships = new ArrayList<Partnership>();
        /* Parse the whole file before touching the running pollers. Anything that goes wrong
         * parsing then leaves the currently running configuration untouched rather than killing
         * the pollers and aborting part way through the reload.
         */
        List<Node> pollerNodes = new ArrayList<Node>();
        List<Partnership> pollerPartnerships = new ArrayList<Partnership>();
        try {
            Element root = getPartnershipsXml().getDocumentElement();
            NodeList rootNodes = root.getChildNodes();
            Node rootNode;
            String nodeName;

            for (int i = 0; i < rootNodes.getLength(); i++) {
                rootNode = rootNodes.item(i);

                nodeName = rootNode.getNodeName();

                if (nodeName.equals("partner")) {
                    loadPartner(newPartners, rootNode, false);
                } else if (nodeName.equals("partnership")) {
                    Partnership partnership = loadPartnership(newPartners, newPartnerships, rootNode, false);
                    if (partnership != null) {
                        pollerNodes.add(rootNode);
                        pollerPartnerships.add(partnership);
                    }
                }
            }
        } catch (Exception e) {
            throw new WrappedException(e);
        }

        synchronized (this) {
            setPartners(newPartners);
            setPartnerships(newPartnerships);
        }

        // The parsed config is good so now swap the pollers over to the new configuration
        getSession().destroyPartnershipPollers(Session.PARTNERSHIP_POLLER);
        for (int i = 0; i < pollerNodes.size(); i++) {
            Partnership partnership = pollerPartnerships.get(i);
            try {
                setupPartnershipPoller(pollerNodes.get(i), partnership);
            } catch (Exception e) {
                /* A poller that cannot be configured must not prevent the rest of the partnerships
                 * from having their pollers started so report it and keep going.
                 */
                logger.error("Failed to configure the directory poller for partnership " + partnership.getName() + ": " + org.openas2.util.Logging.getExceptionMsg(e), e);
            }
        }
    }

    private void loadAttributes(Node node, Partnership partnership) throws OpenAS2Exception {
        Map<String, String> attributes = XMLUtil.mapAttributeNodes(node.getChildNodes(), "attribute", "name", "value");

        AS2Util.attributeEnhancer(attributes);
        partnership.getAttributes().putAll(attributes);
    }

    public void loadPartner(Map<String, Object> partners, Node node) throws OpenAS2Exception {
        loadPartner(partners, node, true);
    }

    /**
     * Load a partner definition into the passed map of partners.
     *
     * @param partners          - the map of partners the loaded partner is added to
     * @param node              - the XML node containing the partner definition
     * @param failOnDuplicate   - if true a partner that is already defined causes an exception,
     *                            otherwise the duplicate is logged as an error and ignored so that
     *                            the first definition found remains in effect
     * @throws OpenAS2Exception - the partner definition could not be loaded
     */
    public void loadPartner(Map<String, Object> partners, Node node, boolean failOnDuplicate) throws OpenAS2Exception {
        String[] requiredAttributes = {Partnership.PID_NAME};

        Map<String, String> newPartner = XMLUtil.mapAttributes(node, requiredAttributes);
        String name = newPartner.get(Partnership.PID_NAME);

        if (partners.get(name) != null) {
            if (failOnDuplicate) {
                throw new OpenAS2Exception("Partner is defined more than once: " + name);
            }
            logger.error("Partner is defined more than once in the partnerships file so the duplicate definition is ignored and the first one found is used: " + name);
            return;
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
        Partnership partnership = loadPartnership(partners, partnerships, node, true);
        if (partnership != null) {
            setupPartnershipPoller(node, partnership);
        }
    }

    /**
     * Load a partnership definition into the passed list of partnerships. This only parses the
     * partnership. Use {@link #setupPartnershipPoller(Node, Partnership)} to activate any directory
     * poller configured for it.
     *
     * @param partners          - the map of partners the partnership sender and receiver are looked up in
     * @param partnerships      - the list of partnerships the loaded partnership is added to
     * @param node              - the XML node containing the partnership definition
     * @param failOnDuplicate   - if true a partnership that is already defined causes an exception,
     *                            otherwise the duplicate is logged as an error and ignored so that
     *                            the first definition found remains in effect
     * @return the loaded partnership or null if it was ignored as a duplicate
     * @throws OpenAS2Exception - the partnership definition could not be loaded
     */
    public Partnership loadPartnership(Map<String, Object> partners, List<Partnership> partnerships, Node node, boolean failOnDuplicate) throws OpenAS2Exception {
        Partnership partnership = new Partnership();
        String[] requiredAttributes = {"name"};

        Map<String, String> psAttributes = XMLUtil.mapAttributes(node, requiredAttributes);
        String name = psAttributes.get("name");

        if (getPartnership(partnerships, name) != null) {
            if (failOnDuplicate) {
                throw new OpenAS2Exception("Partnership is defined more than once: " + name);
            }
            logger.error("Partnership is defined more than once in the partnerships file so the duplicate definition is ignored and the first one found is used: " + name);
            return null;
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

        return partnership;
    }

    /**
     * Activate the directory polling module configured for a partnership, if there is one.
     * Any poller currently running for the partnership must have been destroyed before calling
     * this otherwise the polled directory will be rejected as already in use.
     *
     * @param node          - the XML node containing the partnership definition
     * @param partnership   - the partnership the poller belongs to
     * @throws OpenAS2Exception - the poller could not be configured
     */
    public void setupPartnershipPoller(Node node, Partnership partnership) throws OpenAS2Exception {
        String name = partnership.getName();
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
                if (isPartnershipPollingDisabled()) {
                    /*
                     * Partnership polling is globally disabled so no poller is created for this
                     * partnership. The partnership itself remains fully usable for receiving messages.
                     */
                    if (logger.isDebugEnabled()) {
                        logger.debug("Partnership polling is globally disabled so no poller was started for partnership: " + name);
                    }
                    return;
                }
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
     * Merges attributes into an existing partner element and reloads the in-memory partnerships so
     * they match. Attributes that are not supplied are left as they are, so this is a partial update
     * rather than a replacement. The caller is responsible for persisting the document afterwards.
     *
     * @param name - the name of the partner to update. Partners are not renamed by this method
     * @param attributes - the attributes to set, replacing the value of any that already exist
     * @throws OpenAS2Exception if the partner does not exist
     */
    public void updatePartner(String name, Map<String, String> attributes) throws OpenAS2Exception {
        Element partner = findNamedElement("partner", name);
        if (partner == null) {
            throw new OpenAS2Exception("Unknown partner name: " + name);
        }
        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
            partner.setAttribute(attribute.getKey(), attribute.getValue());
        }
        // Rebuild the in-memory partners and partnerships from the changed document so a partnership
        // that inherits from this partner picks the change up too
        refreshConfig();
    }

    /**
     * Merges attributes, poller configuration and partner references into an existing partnership
     * element and reloads the in-memory partnerships so they match. Anything not supplied is left as
     * it is. The caller is responsible for persisting the document afterwards.
     *
     * @param name - the name of the partnership to update. Partnerships are not renamed by this method
     * @param attributes - partnership attributes to set, or null to leave them alone
     * @param pollerConfig - poller configuration attributes to set, or null to leave them alone
     * @param senderName - the partner to point the sender at, or null to leave it alone
     * @param receiverName - the partner to point the receiver at, or null to leave it alone
     * @throws OpenAS2Exception if the partnership, or a partner it is being pointed at, does not exist
     */
    public void updatePartnership(String name, Map<String, String> attributes, Map<String, String> pollerConfig, String senderName, String receiverName) throws OpenAS2Exception {
        Element partnership = findNamedElement("partnership", name);
        if (partnership == null) {
            throw new OpenAS2Exception("Partnership not found: " + name);
        }
        if (senderName != null) {
            repointPartnership(partnership, name, Partnership.PTYPE_SENDER, senderName);
        }
        if (receiverName != null) {
            repointPartnership(partnership, name, Partnership.PTYPE_RECEIVER, receiverName);
        }
        if (attributes != null) {
            for (Map.Entry<String, String> attribute : attributes.entrySet()) {
                setPartnershipAttribute(partnership, attribute.getKey(), attribute.getValue());
            }
        }
        if (pollerConfig != null && !pollerConfig.isEmpty()) {
            Node pollerNode = XMLUtil.findChildNode(partnership, Partnership.PCFG_POLLER);
            Element pollerElem;
            if (pollerNode == null) {
                pollerElem = getPartnershipsXml().createElement(Partnership.PCFG_POLLER);
                partnership.appendChild(pollerElem);
            } else {
                pollerElem = (Element) pollerNode;
            }
            for (Map.Entry<String, String> attribute : pollerConfig.entrySet()) {
                pollerElem.setAttribute(attribute.getKey(), attribute.getValue());
            }
        }
        refreshConfig();
    }

    private void repointPartnership(Element partnership, String partnershipName, String partnerType, String partnerName) throws OpenAS2Exception {
        if (getPartners().get(partnerName) == null) {
            throw new OpenAS2Exception("Partnership " + partnershipName + " has an undefined " + partnerType + ": " + partnerName);
        }
        Node partnerRef = XMLUtil.findChildNode(partnership, partnerType);
        Element partnerElem;
        if (partnerRef == null) {
            partnerElem = getPartnershipsXml().createElement(partnerType);
            partnership.appendChild(partnerElem);
        } else {
            partnerElem = (Element) partnerRef;
        }
        partnerElem.setAttribute(Partnership.PID_NAME, partnerName);
    }

    /**
     * Sets an "attribute" child of a partnership, replacing the value if one with that name is
     * already present rather than appending a second one.
     */
    private void setPartnershipAttribute(Element partnership, String attrName, String attrValue) {
        NodeList children = partnership.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!"attribute".equals(child.getNodeName())) {
                continue;
            }
            Node nameAttrib = child.getAttributes().getNamedItem("name");
            if (nameAttrib != null && attrName.equals(nameAttrib.getNodeValue())) {
                ((Element) child).setAttribute("value", attrValue);
                return;
            }
        }
        Element elem = getPartnershipsXml().createElement("attribute");
        elem.setAttribute("name", attrName);
        elem.setAttribute("value", attrValue);
        partnership.appendChild(elem);
    }

    /**
     * Finds a top level element of the given type by its name attribute. Walking the children rather
     * than using XPath keeps names containing quotes from breaking the lookup.
     *
     * @return the matching element, or null if there is not exactly one
     */
    private Element findNamedElement(String elementName, String name) {
        Element found = null;
        NodeList children = getPartnershipsXml().getDocumentElement().getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (!elementName.equals(child.getNodeName())) {
                continue;
            }
            Node nameAttrib = child.getAttributes().getNamedItem(Partnership.PID_NAME);
            if (nameAttrib != null && name.equals(nameAttrib.getNodeValue())) {
                if (found != null) {
                    logger.error("More than one " + elementName + " element is named \"" + name + "\" so it cannot be updated.");
                    return null;
                }
                found = (Element) child;
            }
        }
        return found;
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
}
