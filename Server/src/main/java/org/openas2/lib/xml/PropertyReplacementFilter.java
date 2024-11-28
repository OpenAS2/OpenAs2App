package org.openas2.lib.xml;

import org.openas2.OpenAS2Exception;
import org.openas2.lib.util.StringEnvVarReplacer;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;

import java.util.Map;

/**
 * An XML handler to support replacing XML element properties with system environment variables.
 * Support for system properties is provided in the AS2Util.attributeEnhancer method
 *
 */
public class PropertyReplacementFilter extends XMLFilterImpl {

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        super.endElement(uri, localName, qName);
    }

    @SuppressWarnings("unused")
    private String appHomeDir = null;
    private final StringEnvVarReplacer envVarReplacer = new StringEnvVarReplacer();

    public PropertyReplacementFilter() {
        super();
        envVarReplacer.setEnvVarMap(System.getenv());
    }

    public PropertyReplacementFilter(Map<String, String> envVars) {
        super();
        envVarReplacer.setEnvVarMap(envVars);
    }

    public PropertyReplacementFilter(XMLReader parent) {
        this(parent, System.getenv());
    }

    public PropertyReplacementFilter(XMLReader parent, Map<String, String> envVars) {
        super(parent);
        envVarReplacer.setEnvVarMap(envVars);
    }

    public void setAppHomeDir(String appHomeDir) {
        this.appHomeDir = appHomeDir;
        envVarReplacer.setAppHomeDir(appHomeDir);
    }

    /**
     * {@inheritDoc}
     *
     * @see org.xml.sax.helpers.XMLFilterImpl#characters(char[], int, int)
     */
    @Override
    public void characters(char[] data, int start, int length) throws SAXException {
        char[] value;
        try {
            value = envVarReplacer.replace(String.copyValueOf(data, start, length)).toCharArray();
        } catch (OpenAS2Exception e) {
            throw new SAXException(e);
        }
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
            try {
                attributes.setValue(i, envVarReplacer.replace(attributes.getValue(i)));
            } catch (OpenAS2Exception e) {
                throw new SAXException(e);
            }
        }

        super.startElement(uri, localName, qName, attributes);
    }
}
