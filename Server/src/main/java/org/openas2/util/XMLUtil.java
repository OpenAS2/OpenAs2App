package org.openas2.util;

import org.openas2.Component;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.WrappedException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;
import org.xml.sax.helpers.XMLReaderFactory;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamResult;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;


public class XMLUtil {
    public static Document parseXML(InputStream in, XMLFilterImpl handler) throws Exception {
        XMLReader xmlparser = XMLReaderFactory.createXMLReader();
        handler.setParent(xmlparser);

        SAXSource source = new SAXSource(handler, new InputSource(in));
        DOMResult result = new DOMResult();
        TransformerFactory tFactory = TransformerFactory.newInstance();
        Transformer transformer = tFactory.newTransformer();
        transformer.transform(source, result);
        return (Document) result.getNode();
    }

    public static Component getComponent(Node node, Session session) throws OpenAS2Exception {
        Node classNameNode = node.getAttributes().getNamedItem("classname");

        if (classNameNode == null) {
            throw new OpenAS2Exception("Missing classname");
        }

        String className = classNameNode.getNodeValue();

        try {
            Class<?> objClass = Class.forName(className);

            if (!Component.class.isAssignableFrom(objClass)) {
                throw new OpenAS2Exception("Class " + className + " must implement " + Component.class.getName());
            }

            Component obj = (Component) objClass.newInstance();

            Map<String, String> parameters = XMLUtil.mapAttributes(node);
            AS2Util.attributeEnhancer(parameters);

            updateDirectories(session.getBaseDirectory(), parameters);

            obj.init(session, parameters);

            return obj;
        } catch (Exception e) {
            throw new WrappedException("Error creating component: " + className, e);
        }
    }

    public static Node findChildNode(Node parent, String childName) {
        NodeList childNodes = parent.getChildNodes();
        int childCount = childNodes.getLength();
        Node child;

        for (int i = 0; i < childCount; i++) {
            child = childNodes.item(i);

            if (child.getNodeName().equals(childName)) {
                return child;
            }
        }

        return null;
    }

    public static String getNodeAttributeValue(Node node, String attrib, boolean enhance) throws OpenAS2Exception {
        Node attribNode = node.getAttributes().getNamedItem(attrib);
        if (attribNode == null) {
            return null;
        }
        String val = attribNode.getNodeValue();
        if (!enhance) {
            return val;
        }
        Map<String, String> map = new HashMap<String, String>();
        map.put(attrib, val);
        AS2Util.attributeEnhancer(map);
        return map.get(attrib);

    }

    public static Map<String, String> mapAttributeNodes(NodeList nodes, String nodeName, String nodeKeyName, String nodeValueName) throws OpenAS2Exception {
        Map<String, String> attributes = new HashMap<String, String>();
        int nodeCount = nodes.getLength();
        Node attrNode;
        NamedNodeMap nodeAttributes;
        Node tmpNode;
        String attrName;
        String attrValue;

        for (int i = 0; i < nodeCount; i++) {
            attrNode = nodes.item(i);

            if (attrNode.getNodeName().equals(nodeName)) {
                nodeAttributes = attrNode.getAttributes();
                tmpNode = nodeAttributes.getNamedItem(nodeKeyName);

                if (tmpNode == null) {
                    throw new OpenAS2Exception(attrNode.toString() + " does not have key attribute: " + nodeKeyName);
                }

                attrName = tmpNode.getNodeValue();
                tmpNode = nodeAttributes.getNamedItem(nodeValueName);

                if (tmpNode == null) {
                    throw new OpenAS2Exception(attrNode.toString() + " does not have value attribute: " + nodeValueName);
                }

                attrValue = tmpNode.getNodeValue();
                attributes.put(attrName, attrValue);
            }
        }

        return attributes;
    }

    public static Map<String, String> mapAttributes(Node node, boolean keyToLowerCase) {
        Map<String, String> attrMap = new HashMap<String, String>();
        NamedNodeMap attrNodes = node.getAttributes();
        int attrCount = attrNodes.getLength();
        Node attribute;

        for (int i = 0; i < attrCount; i++) {
            attribute = attrNodes.item(i);
            String key = attribute.getNodeName();
            if (keyToLowerCase) {
                key = key.toLowerCase();
            }
            attrMap.put(key, attribute.getNodeValue());
        }

        return attrMap;
    }

    public static Map<String, String> mapAttributes(Node node) {
        return mapAttributes(node, true);
    }

    public static Map<String, String> mapAttributes(Node node, String[] requiredAttributes) throws OpenAS2Exception {
        Map<String, String> attributes = mapAttributes(node);
        String attrName;

        for (String requiredAttribute : requiredAttributes) {
            attrName = requiredAttribute;

            if (attributes.get(attrName) == null) {
                throw new OpenAS2Exception(node.toString() + " is missing required attribute: " + attrName);
            }
        }

        return attributes;
    }

    private static void updateDirectories(String baseDirectory, Map<String, String> attributes) throws OpenAS2Exception {
        Iterator<Entry<String, String>> attrIt = attributes.entrySet().iterator();
        Map.Entry<String, String> attrEntry;
        String value;

        while (attrIt.hasNext()) {
            attrEntry = attrIt.next();
            value = attrEntry.getValue();

            if (value.startsWith("%home%")) {
                if (baseDirectory != null) {
                    value = baseDirectory + value.substring(6);
                    attributes.put(attrEntry.getKey(), value);
                } else {
                    throw new OpenAS2Exception("Base directory isn't set");
                }
            }
        }
    }

    public static String toString(Node node, boolean omitXmlDeclaration) throws TransformerException {
        return domToString(new DOMSource(node), omitXmlDeclaration);
    }

    public static String domToString(Document doc) throws TransformerException {
        return domToString(new DOMSource(doc), true);
    }

    public static String domToString(DOMSource ds, boolean omitXmlDeclaration) throws TransformerException {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        if (omitXmlDeclaration == true) {
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        }
        transformer.setOutputProperty(OutputKeys.INDENT, "no");
        StringWriter writer = new StringWriter();
        transformer.transform(ds, new StreamResult(writer));
        return writer.toString().replaceAll("\n|\r", "");
    }
}
