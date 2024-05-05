package org.openas2.cmd.processor.restapi;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

@Path("/xml")
public class XMLReaderService {

    private final Log logger = LogFactory.getLog(XMLReaderService.class.getSimpleName());
    @GET
    @Path("/read")
    @Produces(MediaType.APPLICATION_XML)
    public Response readXMLContent(@QueryParam("filepath") String filePath, @QueryParam("xpath") String xpathExpression) {
        try {

            logger.info("we are here");

            //APP_BASE_DIR_PROP

            // Load the XML file
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            Document doc = factory.newDocumentBuilder().parse(filePath);

            // Create XPath object
            XPath xpath = XPathFactory.newInstance().newXPath();

            // Compile XPath expression
            XPathExpression expr = xpath.compile(xpathExpression);

            // Evaluate XPath expression against XML document
            NodeList nodes = (NodeList) expr.evaluate(doc, XPathConstants.NODESET);

            // Prepare response
            StringBuilder responseBuilder = new StringBuilder();
            for (int i = 0; i < nodes.getLength(); i++) {
                responseBuilder.append(nodes.item(i).getTextContent()).append("\n");
            }

            // Return response
            return Response.ok(responseBuilder.toString()).build();

        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity("Error occurred: " + e.getMessage()).build();
        }
    }
}
