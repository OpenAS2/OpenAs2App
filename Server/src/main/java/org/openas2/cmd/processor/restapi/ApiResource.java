/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.openas2.cmd.processor.restapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.StringWriter;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;

import org.glassfish.grizzly.http.server.Request;

import org.openas2.cert.AliasedCertificateFactory;
import org.openas2.cert.CertificateFactory;
import org.openas2.cmd.CommandResult;
import org.openas2.cmd.processor.RestCommandProcessor;
import org.openas2.Session;
import org.openas2.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.NamedNodeMap;



/**
 * @author javier
 */
@Path("api")
public class ApiResource {

    /**
     * @return the processor
     */
    public static RestCommandProcessor getProcessor() {
        return processor;
    }

    /**
     * @param aProcessor the processor to set
     */
    public static void setProcessor(RestCommandProcessor aProcessor) {
        processor = aProcessor;
    }

    private static RestCommandProcessor processor;
    @Context
    UriInfo ui;
    Request request;
    private final ObjectMapper mapper;

    public ApiResource() {

        mapper = new ObjectMapper();
        // enable pretty printing
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @RolesAllowed({"ADMIN"})
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public CommandResult getVersion() {
        return new CommandResult(CommandResult.TYPE_OK, getProcessor().getSession().getAppTitle());
    }

    private CommandResult getCertificate(String itemId) throws Exception {
        try {
            List<String> params = new ArrayList<String>();
            params.add("view");
            params.add(itemId);
            CommandResult output = getProcessor().feedCommand("cert", params);
            Certificate cert = (Certificate) output.getResults().get(0);
            HashMap<String, String> map = new HashMap<>();
            map.put("data", Base64.getEncoder().encodeToString(cert.getEncoded()));
            map.put("alias", itemId);
            output.getResults().set(0, map);
            return output;
        } catch (Exception ex) {
            LoggerFactory.getLogger(ApiResource.class.getName()).error(ex.getMessage(), ex);
            throw ex;
            // return Response.status(506).entity( ex.getMessage()).build();
        }

    }

    private CommandResult processRequest(String resource, String action, String itemId, MultivaluedMap<String, String> formParams) throws Exception {
        List<String> params = new ArrayList<>();
        if (action != null) {
            params.add(action);
        }
        if (itemId != null && itemId.length() > 1) {
            params.add(itemId.substring(1));
        }
        Iterator<String> iter = ui.getQueryParameters().keySet().iterator();
        while (iter.hasNext()) {
            String valueKey = iter.next();
            String valueParam = ui.getQueryParameters().getFirst(valueKey);
            params.add(valueKey + "=" + valueParam);
        }
        if (formParams != null) {
            int length = formParams.size();
            for (int index = 0; index < length; index++) {
                String ndxAsStr = String.valueOf(index);
                if (formParams.containsKey(ndxAsStr)) {
                    params.add(formParams.getFirst(ndxAsStr));
                    formParams.remove(ndxAsStr);
                }
            }
            iter = formParams.keySet().iterator();
            while (iter.hasNext()) {
                String valueKey = iter.next();
                String valueParam = formParams.getFirst(valueKey);
                params.add(valueKey + "=" + valueParam);
            }
        }
        CommandResult output = getProcessor().feedCommand(resource, params);
        if (CommandResult.TYPE_OK.equals(output.getType()) && resource.startsWith("partner") && ("add".equals(action) || "delete".equals(action))) {
            // Store the partnership XML since a successful change was made to the partnerships
            CommandResult store_cmd_output = getProcessor().feedCommand("partnership", Arrays.asList("store"));
            output.getResults().addAll(store_cmd_output.getResults());
        }
        return output;
    }

    @RolesAllowed({"ADMIN"})
    @GET
    @Path("/{resource}/{action}{id:(/[^/]+?)?}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getCommand(@PathParam("resource") String resource, @PathParam("action") @DefaultValue("list") String action, @PathParam("id") String itemId) throws Exception {
        try {
            CommandResult output=null;
            // TODO: Figure out a better way to return proper JSON objects instead of this hack
            if (action.equalsIgnoreCase("view") && resource.equalsIgnoreCase("cert") && (itemId != null && itemId.length() > 1)) {
                output=this.getCertificate(itemId.substring(1));
            } else {
                output = processRequest(resource, action, itemId, null);
            }
            String jsonResult = this.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
            return Response.status(200).entity(jsonResult).type(MediaType.APPLICATION_JSON).build();
        } catch (Exception ex) {
            LoggerFactory.getLogger(ApiResource.class.getName()).error(ex.getMessage(), ex);
            throw ex;
            // return Response.status(506).entity( ex.getMessage()).build();
        }

    }

    @RolesAllowed({"ADMIN"})
    @POST
    @Path("/{resource}/{action}{id:(/[^/]+?)?}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response postCommand(@PathParam("resource") String resource, @PathParam("action") @DefaultValue("list") String action, @PathParam("id") String itemId, MultivaluedMap<String, String> formParams) throws Exception {
        try {
            CommandResult output=null;
            // TODO: Figure out a better way to return proper JSON objects instead of this hack
            if (action.equalsIgnoreCase("view") && resource.equalsIgnoreCase("cert")) {
                output= this.getCertificate(itemId);
            } else if (action.equalsIgnoreCase("importbystream") && resource.equalsIgnoreCase("cert")) {
                output= this.importCertificateByStream(itemId.substring(1), formParams);
            } else {
                output = processRequest(resource, action, itemId, formParams);
            }
            String jsonResult = this.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
            return Response.status(200).entity(jsonResult).type(MediaType.APPLICATION_JSON).build();
        } catch (Exception ex) {
            LoggerFactory.getLogger(ApiResource.class.getName()).error(ex.getMessage(), ex);
            throw ex;
            // return Response.status(506).entity( ex.getMessage()).build();
        }
    }

    @RolesAllowed({"ADMIN"})
    @PUT
    @Path("/{resource}/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response putCommand(@PathParam("param") String resource, @PathParam("id") String itemId, MultivaluedMap<String, String> formParams) throws Exception {
        return postCommand(resource, "add", itemId, formParams);
    }

    @RolesAllowed({"ADMIN"})
    @DELETE
    @Path("/{resource}/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteCommand(@PathParam("resource") String resource, @PathParam("id") String itemId) throws Exception {
        return getCommand(resource, "delete", itemId);
    }

    @RolesAllowed({"ADMIN"})
    @HEAD
    @Path("/{resource}{action:(/[^/]+?)?}{id:(/[^/]+?)?}")
    public Response headCommand(@PathParam("param") String command) {
        // Just an Empty response
        return Response.status(200).build();
    }

    @GET
    @RolesAllowed({"ADMIN"})
    @Path("/getPropertyList")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPropertyList(@Context Request request) {
        if (!request.isSecure() && !isLocalhost(request)) {
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"error\":\"SSL/TLS required\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        Map<String, String> result = new HashMap<>();
        try {
            result = (Map<String, String>) Properties.getProperties();
            for (String key : new HashSet<>(result.keySet())) {  // === Redact sensitive entries ===
                String lowerKey = key.toLowerCase();
                if (lowerKey.contains("password") || lowerKey.contains("pwd")) {
                    result.remove(key);
                    // result.computeIfPresent(key, (k, v) -> "***REDACTED***"); // mask instead of removing
                }
            }

        } catch (Exception ex) {
            LoggerFactory.getLogger(ApiResource.class.getName()).error(ex.getMessage(), ex);
            throw ex;
        }
        ObjectMapper om = new ObjectMapper();
        try {
            String js = om.writeValueAsString(result);
            return Response.ok(js, MediaType.APPLICATION_JSON).build();
        } catch (JsonProcessingException e) {
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                    .entity("{\"error\":\"Serialization failed\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    @GET
    @RolesAllowed({"ADMIN"})
    @Path("/getXml")
    @Produces({MediaType.APPLICATION_XML,MediaType.APPLICATION_JSON})
    public Response getXml(@Context Request request,
                           @QueryParam("filename") String filename,
                           @QueryParam("xpath") String xpathExpression) {
        if (!request.isSecure() && !isLocalhost(request)) {  // Require HTTPS unless localhost
            return Response.status(Response.Status.FORBIDDEN)
                    .entity("{\"error\":\"SSL/TLS required\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
        Session session = getProcessor().getSession();
        String filePath = session.getBaseDirectory() + "/" + filename;
        try {
            NodeList nodeList = getNodes(filePath, xpathExpression);
            DocumentBuilder db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document resultDocument = db.newDocument();
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node importedNode = resultDocument.importNode(nodeList.item(i), true);
                redactSensitiveAttributes(importedNode);  // === Redact sensitive attributes ===
                resultDocument.appendChild(importedNode);
            }
            StringWriter stringWriter = new StringWriter();// Convert XML document to string
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.transform(new DOMSource(resultDocument), new StreamResult(stringWriter));

            String xmlContent = stringWriter.toString();
            return Response.ok(xmlContent, MediaType.APPLICATION_XML).build();

        } catch (Exception exception) {
            LoggerFactory.getLogger(ApiResource.class.getName())
                    .error("Error building XML response", exception);
            return Response.serverError()
                    .entity("{\"error\":\"Internal Server Error\"}")
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }
    }

    /**
     * Remove or mask sensitive attributes (password/pwd).
     */
    private void redactSensitiveAttributes(Node node) {
        if (node == null){
            return;
        }
        if (node.hasAttributes()) {
            NamedNodeMap attrs = node.getAttributes();
            for (int j = attrs.getLength() - 1; j >= 0; j--) {
                Node attr = attrs.item(j);
                String attrName = attr.getNodeName().toLowerCase();
                if (attrName.contains("password") || attrName.contains("pwd")) {
                    // Either mask or remove
                    //attr.setNodeValue("***REDACTED***");
                    attrs.removeNamedItem(attr.getNodeName());
                }
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            redactSensitiveAttributes(children.item(i));
        }
    }

    private static boolean isLocalhost(Request request) {
        boolean isLocalhost = request.getRemoteAddr().equals("127.0.0.1") || request.getRemoteAddr().equals("::1");
        return isLocalhost;
    }

    private NodeList getNodes(String xmlFileName, String xpathExpression) {
        NodeList nodeList = null;
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

            // === XXE Protection ===
            dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
            dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            dbf.setXIncludeAware(false);
            dbf.setExpandEntityReferences(false);

            DocumentBuilder db = dbf.newDocumentBuilder();
            File file = new File(xmlFileName);
            Document document = db.parse(file);

            XPathExpression xPathExpr = XPathFactory.newInstance().newXPath().compile(xpathExpression);
            nodeList = (NodeList) xPathExpr.evaluate(document, XPathConstants.NODESET);


            if (nodeList != null) {
                for (int i = 0; i < nodeList.getLength(); i++) {
                    Node node = nodeList.item(i);
                    if (node.hasAttributes()) {
                        NamedNodeMap attrs = node.getAttributes();
                        for (int j = attrs.getLength() - 1; j >= 0; j--) {
                            Node attr = attrs.item(j);
                            String attrName = attr.getNodeName().toLowerCase();
                            if (attrName.contains("password") || attrName.contains("pwd")) {
                                attrs.removeNamedItem(attr.getNodeName()); // Remove the sensitive attribute
                                // Or mask instead: attr.setNodeValue("***REDACTED***");
                            }
                        }
                    }
                }
            }

        } catch (Exception ex) {
            LoggerFactory.getLogger(ApiResource.class.getName())
                    .error("Error parsing XML file: " + xmlFileName, ex);
            return null;
        }
        return nodeList;
    }



    private CommandResult importCertificateByStream(String itemId, MultivaluedMap<String, String> formParams) throws Exception {
        try {
            List<String> params = new ArrayList<String>();
            params.add("importbystream");
            params.add(itemId);
            String payload = formParams.getFirst("data");
            AliasedCertificateFactory certFx = (AliasedCertificateFactory) getProcessor().getSession().getCertificateFactory(CertificateFactory.COMPID_AS2_CERTIFICATE_FACTORY);
            ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(payload));

            java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
            CommandResult cmdRes = new CommandResult(CommandResult.TYPE_OK, "Certificate(s) imported successfully");

            while (bais.available() > 0) {
                Certificate cert = cf.generateCertificate(bais);

                if (cert instanceof X509Certificate) {
                    certFx.addCertificate(itemId, (X509Certificate) cert, true);
                    cmdRes.getResults().add("Imported certificate: " + itemId);
                    return cmdRes;
                }
            }
            return new CommandResult(CommandResult.TYPE_ERROR, "No valid X509 certificates found");
        } catch (Exception ex) {
            LoggerFactory.getLogger(ApiResource.class.getName()).error(ex.getMessage(), ex);
            throw ex;

        }
    }

}
