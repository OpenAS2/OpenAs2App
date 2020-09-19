package org.openas2.lib.xml;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Supports replacing XML element properties with system environment variables.
 * Support for system properties is provided in the AS2Util.attributeEnhancer method
 *
 */
public class PropertyReplacementFilter extends XMLFilterImpl {

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        super.endElement(uri, localName, qName);
    }

    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$ENV\\{([^\\}]++)\\}");
    private final Map<String, String> env_vars;

    public PropertyReplacementFilter() {
        super();
        this.env_vars = System.getenv();
    }

    public PropertyReplacementFilter(Map<String, String> env_vars) {
        super();
        this.env_vars = env_vars;
    }

    public PropertyReplacementFilter(XMLReader parent) {
        this(parent, System.getenv());
    }

    public PropertyReplacementFilter(XMLReader parent, Map<String, String> env_vars) {
        super(parent);
        this.env_vars = env_vars;
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
     * java.lang.String, java.lang.String, org.xml.sax.Attributes)
     */
    @Override
    public void startElement(String uri, String localName, String qName, Attributes attrs) throws SAXException {
        AttributesImpl attributes = (attrs instanceof AttributesImpl) ? (AttributesImpl) attrs : new AttributesImpl(attrs);

        int length = attributes.getLength();
        for (int i = 0; i < length; ++i) {
            attributes.setValue(i, this.replace(attributes.getValue(i)));
        }

        super.startElement(uri, localName, qName, attributes);
    }

    private String replace(String input) throws SAXException {
        StringBuffer strBuf = new StringBuffer();
        Matcher matcher = ENV_VAR_PATTERN.matcher(input);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = env_vars.get(key);

            if (value == null) {
                throw new SAXException("Missing environment variable for replacement: " + matcher.group() + " Using key: " + key);
            } else {
                matcher.appendReplacement(strBuf, Matcher.quoteReplacement(value));
            }
        }
        matcher.appendTail(strBuf);

        return strBuf.toString();
    }

}
