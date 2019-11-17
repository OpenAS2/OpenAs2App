/**
 *
 */
package org.openas2.cmd.processor;

import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.StringReader;

/**
 * used to parse  commands from the socket command processor
 * message format 
 *   &lt;command userid="abc" pasword="xyz"&gt; the actual command &lt;/command&gt;
 *
 * @author joseph mcverry
 *
 */
public class SocketCommandParser extends DefaultHandler implements EntityResolver, ContentHandler {

    SAXParser parser;
    private String userid;
    private String password;
    private String commandText;

    /**     simple string processor    */
    protected CharArrayWriter contents = new CharArrayWriter();

    /** construct the factory with a xml parser
     * @throws Exception an xml parser exception
     */

    public SocketCommandParser() throws Exception {
        SAXParserFactory spf = SAXParserFactory.newInstance();
        parser = spf.newSAXParser();

    }

    public void parse(String inLine) throws SAXException, IOException {
        userid = "";
        password = "";
        commandText = "";
        contents.reset();

        StringReader sr = new StringReader(inLine);
        parser.parse(new InputSource(sr), this);
    }

    /**
     * Method handles #PCDATA
     * @param ch array
     * @param start position in array where next has been placed
     * @param length int
     *
     *
     */
    public void characters(char[] ch, int start, int length) {

        contents.write(ch, start, length);

    }

    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {

        if (qName.equals("command")) {
            userid = attributes.getValue("id");
            password = attributes.getValue("password");
        }
    }

    public String getCommandText() {
        return commandText;
    }

    public String getPassword() {
        return password;
    }

    public String getUserid() {
        return userid;
    }

    public void endElement(String uri, String localName, String qName) throws SAXException {
        if (qName.equals("command")) {
            this.commandText = contents.toString();
        } else {
            contents.flush();
        }
    }

}
