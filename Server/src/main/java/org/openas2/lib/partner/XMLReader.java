package org.openas2.lib.partner;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Iterator;
import java.util.Map;

import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.openas2.lib.util.XMLException;
import org.openas2.lib.util.XMLUtil;

public class XMLReader {

	public static final String TYPE = "xml";
	public static final String ATTRIBUTE_URL = "url";
	public static final String ELEMENT_ROOT = "partnerships";
	public static final String ELEMENT_PARTNER = "partner";
	public static final String ATTRIBUTE_ALIAS = "alias";
	public static final String ELEMENT_PARTNERSHIP = "partnership";
	public static final String ATTRIBUTE_SENDER = "sender";
	public static final String ATTRIBUTE_RECEIVER = "receiver";

	public static IPartnerStore read(IPartnerStore store, InputStream in) throws DocumentException, XMLException {
		// get the root element of the XML document
		Element root = XMLUtil.getDocument(in).getRootElement();
		XMLUtil.requireElement(root, ELEMENT_ROOT);

		// load each of the partners and partnerships
		StringBuffer partnerAliases = new StringBuffer();
		StringBuffer partnershipAliases = new StringBuffer();
		for (Iterator<Element> elementIt = root.elementIterator(); elementIt.hasNext();) {
			Element childElement = (Element) elementIt.next();
			String childName = childElement.getName().toLowerCase();
			if (childName.equals(ELEMENT_PARTNER)) {
				if (partnerAliases.length() > 0) {
					partnerAliases.append(", ");
				}
				partnerAliases.append(loadPartner(store, childElement));

			} else if (childName.equals(ELEMENT_PARTNERSHIP)) {
				if (partnershipAliases.length() > 0) {
					partnershipAliases.append(", ");
				}
				partnershipAliases.append(loadPartnership(store, childElement));
			}
		}

		return store;
	}

	public static IPartnerStore read(IPartnerStore store, URL url) throws IOException, DocumentException, XMLException {
		InputStream in = new BufferedInputStream(url.openStream());
		try {
			return read(store, in);
		} finally {
			in.close();
		}
	}

	public static IPartnerStore read(IPartnerStore store, String filename) throws IOException, DocumentException,
			XMLException {
		InputStream in = new BufferedInputStream(new FileInputStream(filename));
		try {
			return read(store, in);
		} finally {
			in.close();
		}
	}

	protected static String loadPartner(IPartnerStore store, Element child) throws XMLException {
		String alias = requireAttribute(child, ATTRIBUTE_ALIAS);
		if (store.getPartner(alias) != null) {
			throw new XMLException(child, "Duplicate partner: " + alias, null);
		}

		// parse the partner values
		IPartner partner = store.createPartner();
		Map<String, String> attributes = XMLUtil.mapAttributes(child, new String[]{ATTRIBUTE_ALIAS});
		partner.getAttributes().putAll(attributes);

		// put the partner in the store
		store.setPartner(alias, partner);
		return alias;
	}

	protected static String loadPartnership(IPartnerStore store, Element child) throws XMLException {
		String alias = child.attributeValue(ATTRIBUTE_ALIAS);
		if (alias == null) {
			throw new XMLException(child, "Alias must be set", null);
		}
		if (store.getPartnership(alias) != null) {
			throw new XMLException(child, "Duplicate partnership: " + alias, null);
		}

		// parse the partnership values
		IPartnership partnership = store.createPartnership();
		String senderAlias = requireAttribute(child, ATTRIBUTE_SENDER);
		IPartner sender = store.getPartner(senderAlias);
		if (sender == null) {
			throw new XMLException(child, "Sender partner does not exist: " + senderAlias);
		}
		partnership.setSender(sender);

		String receiverAlias = requireAttribute(child, ATTRIBUTE_RECEIVER);
		IPartner receiver = store.getPartner(receiverAlias);
		if (receiver == null) {
			throw new XMLException(child, "Receiver partner does not exist: " + receiverAlias);
		}
		partnership.setReceiver(receiver);

		Map<String, String> attributes = XMLUtil.mapAttributes(child, new String[]{ATTRIBUTE_ALIAS, ATTRIBUTE_SENDER,
			ATTRIBUTE_RECEIVER});
		partnership.getAttributes().putAll(attributes);

		store.setPartnership(alias, partnership);
		return alias;
	}

	protected static String requireAttribute(Element element, String attributeName) throws XMLException {
		String value = element.attributeValue(attributeName);
		if (value == null) {
			throw new XMLException(element, attributeName + " must be set", null);
		}
		return value;
	}
}
