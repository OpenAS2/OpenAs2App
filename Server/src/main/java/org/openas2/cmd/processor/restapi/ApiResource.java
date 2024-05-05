/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.openas2.cmd.processor.restapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.ComponentNotFoundException;
import org.openas2.Session;
import org.openas2.cert.AliasedCertificateFactory;
import org.openas2.cmd.CommandResult;
import org.openas2.cmd.processor.RestCommandProcessor;
import org.openas2.partner.Partnership;
import org.openas2.partner.PartnershipFactory;
import org.openas2.util.Properties;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.security.RolesAllowed;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObjectBuilder;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * @author javier
 */
@Path("api")
public class ApiResource {
    private final Log logger = LogFactory.getLog(ApiResource.class.getSimpleName());

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
    @Context
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
        logger.debug("@getVersion " + getProcessor().getSession().getAppVersion());

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
            Logger.getLogger(ApiResource.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
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
            logger.debug("itemId" + itemId);

        }
        Iterator<String> iter = ui.getQueryParameters().keySet().iterator();
        while (iter.hasNext()) {
            String valueKey = iter.next();
            String valueParam = ui.getQueryParameters().getFirst(valueKey);
            params.add(valueKey + "=" + valueParam);
            logger.debug(valueKey + "=" + valueParam);

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
                logger.debug(valueKey + "=" + valueParam);
            }
        }
        CommandResult output = getProcessor().feedCommand(resource, params);

        logger.debug("getProcessor().feedCommand(" + resource + ", " + String.join(", ", params) + ")");

        if (CommandResult.TYPE_OK.equals(output.getType()) && resource.startsWith("partner") && ("add".equals(action) || "delete".equals(action))) {
            // Store the partnership XML since a successful change was made to the partnerships
            CommandResult store_cmd_output = getProcessor().feedCommand("partnership", Collections.singletonList("store"));
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
            CommandResult output = null;
            logger.debug("resource=" + resource + " action=" + action + " itemId" + itemId);
            // TODO: Figure out a better way to return proper JSON objects instead of this hack
            if (action.equalsIgnoreCase("view") && resource.equalsIgnoreCase("cert") && (itemId != null && itemId.length() > 1)) {
                output = this.getCertificate(itemId.substring(1));
            } else {
                output = processRequest(resource, action, itemId, null);
            }
            String jsonResult = this.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
            logger.debug("jsonresult=" + jsonResult);
            return Response.status(200).entity(jsonResult).type(MediaType.APPLICATION_JSON).build();

        } catch (Exception ex) {
            Logger.getLogger(ApiResource.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
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
            CommandResult output = null;
            // TODO: Figure out a better way to return proper JSON objects instead of this hack
            if (action.equalsIgnoreCase("view") && resource.equalsIgnoreCase("cert")) {
                output = this.getCertificate(itemId);
            } else if (action.equalsIgnoreCase("importbystream") && resource.equalsIgnoreCase("cert")) {
                output = this.importCertificateByStream(itemId.substring(1), formParams);
            } else {
                output = processRequest(resource, action, itemId, formParams);
            }
            String jsonResult = this.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
            return Response.status(200).entity(jsonResult).type(MediaType.APPLICATION_JSON).build();
        } catch (Exception ex) {
            Logger.getLogger(ApiResource.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
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

    private CommandResult importCertificateByStream(String itemId, MultivaluedMap<String, String> formParams) throws Exception {
        try {
            List<String> params = new ArrayList<String>();
            params.add("importbystream");
            params.add(itemId);
            String payload = formParams.getFirst("data");
            AliasedCertificateFactory certFx = (AliasedCertificateFactory) getProcessor().getSession().getCertificateFactory();
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
            Logger.getLogger(ApiResource.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            throw ex;
            // return Response.status(506).entity( ex.getMessage()).build();
        }
    }

    //--------------------------------------------
    @POST
    @RolesAllowed({"ADMIN"})
    @Path("/v2/ClearConsole")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response ClearConsole() {
        try {
            // For Windows OS
            new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            return Response.ok().build();
        } catch (Exception e) {
            e.printStackTrace();
            return Response.serverError().entity("Error occured while clearing console").build();
        }
    }

    @POST
    @RolesAllowed({"ADMIN"})

    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/v2/WriteToConsole/{id}")
    public Response WriteToConsole(@PathParam("id") String whatToWrite) {
        logger.debug("Received Post Request with parametr=str:" + whatToWrite);
        // You can perform additional processing or return a response as needed
        // For now, let's just return a simple response
        PrintWriter w = System.console().writer();
        w.println(whatToWrite);
        w.flush();
        return Response.ok("[" + whatToWrite + "] written to console..").build();
    }

    @GET
    @RolesAllowed({"ADMIN"})
    @Path("/v2/configuration/propertylist")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPropertyList() {
        Map<String, String> result = new HashMap<>();
        result = Properties.getProperties();
        ObjectMapper om = new ObjectMapper();
        try {
            String js = om.writeValueAsString(result);
            return Response.ok(js, MediaType.APPLICATION_JSON).build();
        } catch (JsonProcessingException e) {
            logger.error(e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("error")
                .type(MediaType.APPLICATION_JSON)
                .build();
        }
    }

    private List<Partnership> getSessionPartnerships() {
        Session s = getProcessor().getSession();
        PartnershipFactory pfx = null;
        try {
            pfx = s.getPartnershipFactory();
        } catch (ComponentNotFoundException e) {
            logger.error(e);
        }
        List<Partnership> partnerships = pfx.getPartnerships();
        return partnerships;
    }

    @GET
    @RolesAllowed({"ADMIN"})
    @Path("/v2/partnership/list")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listPartnerships() {
        List<Partnership> partnerships = getSessionPartnerships();
        Map<String, List<Partnership>> result = new HashMap<>();
        result.put("partnership", partnerships);
        try {
            String json = mapper.writeValueAsString(result);
            return Response.ok(json, MediaType.APPLICATION_JSON).build();
        } catch (JsonProcessingException e) {
            logger.error("Error converting Partnership list to JSON", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("error")
                .type(MediaType.APPLICATION_JSON)
                .build();
        }
    }

    @GET
    @RolesAllowed({"ADMIN"})
    @Path("/v2/getAnnotatedMehtods")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPathAnnotatedMethods() {
        Map<String, String> annotatedMethods = new HashMap<>();
        Method[] methods = this.getClass().getDeclaredMethods();
        for (Method method : methods) {
            if (method.isAnnotationPresent(Path.class)) {
                String methodName = method.getName();
                Path pathAnnotation = method.getAnnotation(Path.class);
                String pathValue = pathAnnotation.value();
                annotatedMethods.put(methodName, pathValue);
            }
        }
        try {
            String json = mapper.writeValueAsString(annotatedMethods);
            return Response.ok(json, MediaType.APPLICATION_JSON).build();
        } catch (JsonProcessingException e) {
            logger.error("Error converting Partnership list to JSON", e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity("error")
                .type(MediaType.APPLICATION_JSON)
                .build();
        }
    }

    //---------------------------------------------------
    @GET
    @RolesAllowed({"ADMIN"})
    @Path("/v2/getxml")
    @Produces(MediaType.APPLICATION_XML)
    public Response readXMLContent(@QueryParam("filename") String filename,
                                   @QueryParam("xpath") String xpathExpression) {
        logger.debug("@readXMLContent ");
        Session session = getProcessor().getSession();
        logger.debug("App Title:" + session.getAppTitle());
        logger.debug("App Version:" + session.getAppVersion());
        logger.debug("@pp[filename]:" + filename);


        String filePath = session.getBaseDirectory() + '\\' + filename;
        logger.debug("filePath:" + filePath);
        logger.debug("xpath:" + xpathExpression);
        try {
            File file = new File(filePath); // Create a File object using the file path
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            DocumentBuilder db = dbf.newDocumentBuilder();
            Document document = db.parse(file); // Pass the File object to parse()

            XPathFactory xpathFactory = XPathFactory.newInstance();
            XPath xpath = xpathFactory.newXPath();

            // Compile XPath expression
            XPathExpression expr = xpath.compile(xpathExpression);

            // Evaluate XPath expression against XML document
            NodeList nodeList = (NodeList) expr.evaluate(document, XPathConstants.NODESET);

            Document resultDocument = db.newDocument();
            for (int i = 0; i < nodeList.getLength(); i++) {
                Node importedNode = resultDocument.importNode(nodeList.item(i), true);
                resultDocument.appendChild(importedNode);
            }

            // Convert the XML document to a string
            StringWriter stringWriter = new StringWriter();
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.transform(new DOMSource(resultDocument), new StreamResult(stringWriter));
            String xmlContent = stringWriter.toString();
            return Response.ok(xmlContent, MediaType.APPLICATION_XML).build();

        } catch (Exception exception) {
            // Handle any exceptions
            return Response.serverError()
                .entity("error")
                .type(MediaType.APPLICATION_JSON)
                .build();
        }
    }

    //--------------------------------------------------
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @RolesAllowed("ADMIN")
    @Path("/v2/getrestanotations")
    public Response GetRestAnnotations() {
        Class<ApiResource> clazz = ApiResource.class;
        Method[] methods = clazz.getDeclaredMethods();
        JsonArrayBuilder jsonArrayBuilder = Json.createArrayBuilder();

        for (Method method : methods) {
            Path pathAnnotation = method.getAnnotation(Path.class);
            if (pathAnnotation != null) {
                JsonObjectBuilder jsonObjectBuilder = Json.createObjectBuilder();
                jsonObjectBuilder.add("method", method.getName());
                jsonObjectBuilder.add("path", pathAnnotation.value());

                // Add method parameters
                Parameter[] parameters = method.getParameters();
                JsonArrayBuilder parametersArrayBuilder = Json.createArrayBuilder();
                for (Parameter parameter : parameters) {
                    JsonObjectBuilder parameterObjectBuilder = Json.createObjectBuilder();
                    parameterObjectBuilder.add("name", parameter.getName());
                    parameterObjectBuilder.add("type", parameter.getType().getName());

                    // Check for @QueryParam annotation
                    QueryParam queryParamAnnotation = parameter.getAnnotation(QueryParam.class);
                    if (queryParamAnnotation != null) {
                        parameterObjectBuilder.add("queryParam", queryParamAnnotation.value());
                    }

                    parametersArrayBuilder.add(parameterObjectBuilder);
                }
                jsonObjectBuilder.add("parameters", parametersArrayBuilder);

                // Add method annotations
                Annotation[] annotations = method.getAnnotations();
                JsonObjectBuilder annotationsArrayBuilder = Json.createObjectBuilder();
                for (Annotation annotation : annotations) {
                    String annotationName = annotation.annotationType().getSimpleName();
                    if (annotationName.equals("Path")) {
                        annotationsArrayBuilder.add("Path", ((Path) annotation).value());
                    } else if (annotationName.equals("GET") || annotationName.equals("POST")) {
                        annotationsArrayBuilder.add("HTTPMethod", annotationName);
                    } else if (annotationName.equals("Produces")) {
                        // Include the value of @Produces annotation in the JSON response
                        annotationsArrayBuilder.add("Produces", ((Produces) annotation).value()[0]);
                    } else if (annotationName.equals("RolesAllowed")) {
                        String[] roles = ((RolesAllowed) annotation).value();
                        JsonArrayBuilder rolesArrayBuilder = Json.createArrayBuilder();
                        for (String role : roles) {
                            rolesArrayBuilder.add(role);
                        }
                        annotationsArrayBuilder.add("RolesAllowed", rolesArrayBuilder);
                    } else {
                        // Handle other annotations
                    }
                }
                jsonObjectBuilder.add("annotations", annotationsArrayBuilder);

                jsonArrayBuilder.add(jsonObjectBuilder);
            }
        }
        return Response.ok(jsonArrayBuilder.build().toString()).build();
    }
}





