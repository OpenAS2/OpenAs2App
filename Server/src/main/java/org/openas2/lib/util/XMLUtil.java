package org.openas2.lib.util;

import org.dom4j.Attribute;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;

import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class XMLUtil {
    public static Document getDocument(InputStream in) throws DocumentException {
        SAXReader reader = new SAXReader();
        return reader.read(in);
    }

    public static Document getDocument(String xml) throws DocumentException {
        SAXReader reader = new SAXReader();
        return reader.read(new StringReader(xml));
    }

    public static void requireElement(Element element, String name) throws DocumentException {
        if (!element.getName().equalsIgnoreCase(name)) {
            throw new DocumentException("Missing required element: " + name);
        }
    }

    public static Element requireChildElement(Element parent, String name) throws DocumentException {
        Element child = parent.element(name);
        if (child == null) {
            throw new DocumentException("Missing required element: " + name);
        }
        return child;
    }

    public static String[] getChildNames(Element parent) {
        List<String> parameterNames = new ArrayList<String>();
        for (Iterator<Element> it = parent.elementIterator(); it.hasNext(); ) {
            Element child = it.next();
            parameterNames.add(child.getName());
        }
        return GeneralUtil.convert(parameterNames);
    }

    public static Map<String, String> mapAttributes(Element element) {
        Map<String, String> attributeMap = new HashMap<String, String>();
        for (Iterator<Attribute> it = element.attributeIterator(); it.hasNext(); ) {
            Attribute attribute = it.next();
            attributeMap.put(attribute.getName(), attribute.getStringValue());
        }
        return attributeMap;
    }

    public static Map<String, String> mapAttributes(Element element, String[] ignore) {
        Map<String, String> attributeMap = mapAttributes(element);
        for (int i = 0; i < ignore.length; i++) {
            attributeMap.remove(ignore[i]);
        }
        return attributeMap;
    }

    public static Map<String, String> mapChildValues(Element parent) {
        Map<String, String> valueMap = new HashMap<String, String>();
        for (Iterator<Element> it = parent.elementIterator(); it.hasNext(); ) {
            Element child = it.next();
            valueMap.put(child.getName(), child.getStringValue());
        }
        return valueMap;
    }

    public static void convertMap(Element parent, Map<Object, Object> values) {
        Iterator<Map.Entry<Object, Object>> it = values.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Object, Object> entry = it.next();
            Element child = parent.addElement(entry.getKey().toString());
            child.setText(entry.getValue().toString());
        }
    }

    public static void removeElements(Element parent, String name) {
        for (Iterator<Element> it = parent.elementIterator(name); it.hasNext(); ) {
            Element el = it.next();
            parent.remove(el);
        }
    }
}
