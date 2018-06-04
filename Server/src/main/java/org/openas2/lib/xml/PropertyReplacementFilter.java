package org.openas2.lib.xml;

import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;

public class PropertyReplacementFilter extends XMLFilterImpl {

	@Override
	public void endElement(String uri, String localName, String qName) throws SAXException {
		super.endElement(uri, localName, qName);
	}

	private static final Pattern PATTERN = Pattern.compile("\\$ENV\\{([^\\}]++)\\}");
	private final Properties properties;

	public PropertyReplacementFilter() {
		super();
		this.properties = System.getProperties();
	}

	public PropertyReplacementFilter(XMLReader parent) {
		this(parent, System.getProperties());
	}

	public PropertyReplacementFilter(XMLReader parent, Properties properties) {
		super(parent);
		this.properties = properties;
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see org.xml.sax.helpers.XMLFilterImpl#characters(char[], int, int)
	 */
	@Override
	public void characters(char[] data, int start, int length) throws SAXException {
		char[] value = this.replace(String.copyValueOf(data, start, length)).toCharArray();
		super.characters(value, 0, value.length);
	}

	/**
	 * {@inheritDoc}
	 * 
	 * @see org.xml.sax.helpers.XMLFilterImpl#startElement(java.lang.String,
	 *      java.lang.String, java.lang.String, org.xml.sax.Attributes)
	 */
	@Override
	public void startElement(String uri, String localName, String qName, Attributes attrs) throws SAXException {
		AttributesImpl attributes = (attrs instanceof AttributesImpl) ? (AttributesImpl) attrs
				: new AttributesImpl(attrs);

		int length = attributes.getLength();
		for (int i = 0; i < length; ++i) {
			attributes.setValue(i, this.replace(attributes.getValue(i)));
		}

		super.startElement(uri, localName, qName, attributes);
	}

	private String replace(String input) throws SAXException {
		StringBuffer strBuf = new StringBuffer();
		Matcher matcher = PATTERN.matcher(input);
		while (matcher.find()) {
			String key = matcher.group(1);
			String value = properties.getProperty(key);

			if (value == null) {
				throw new SAXException("Missing environment variable for replacement: " + matcher.group());
			} else {
				matcher.appendReplacement(strBuf,Matcher.quoteReplacement(value));
			}
		}
		matcher.appendTail(strBuf);

		return strBuf.toString();
	}

}
