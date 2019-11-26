/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.openas2.cmd.processor.restapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.openas2.cert.AliasedCertificateFactory;
import org.openas2.cmd.CommandResult;
import org.openas2.cmd.processor.RestCommandProcessor;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.io.ByteArrayInputStream;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author javier
 */
@Path("api")
public class ApiResource {

    private final RestCommandProcessor processor;
    @Context
    UriInfo ui;
    @Context
    Request request;
    private final ObjectMapper mapper;

    public ApiResource(RestCommandProcessor processor) {
        this.processor = processor;
        // create the mapper
        mapper = new ObjectMapper();
        // enable pretty printing
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @RolesAllowed("ADMIN")
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public CommandResult getVersion() {
        return new CommandResult(CommandResult.TYPE_OK, processor.getSession().getAppTitle());
    }

    private CommandResult getCertificate(String itemId) throws Exception {
        try {
            List<String> params = new ArrayList<String>();
            params.add("view");
            params.add(itemId);
            CommandResult output = processor.feedCommand("cert", params);
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

    @RolesAllowed("ADMIN")
    @GET
    @Path("/{resource}/{action}{id:(/[^/]+?)?}")
    @Produces(MediaType.APPLICATION_JSON)
    public CommandResult getCommand(@PathParam("resource") String resource, @PathParam("action") @DefaultValue("list") String action, @PathParam("id") String itemId) throws Exception {
        try {
            // TODO: Figure out a better way to return proper JSON objects instead of this hack
            if (action.equalsIgnoreCase("view") && resource.equalsIgnoreCase("cert") && (itemId != null && itemId.length() > 1)) {
                return this.getCertificate(itemId.substring(1));
            }
            List<String> params = new ArrayList<String>();
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
            CommandResult output = processor.feedCommand(resource, params);
            return output;
        } catch (Exception ex) {
            Logger.getLogger(ApiResource.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            throw ex;
            // return Response.status(506).entity( ex.getMessage()).build();
        }

    }

    @RolesAllowed("ADMIN")
    @POST
    @Path("/{resource}/{action}{id:(/[^/]+?)?}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public CommandResult postCommand(@PathParam("resource") String resource, @PathParam("action") @DefaultValue("list") String action, @PathParam("id") String itemId, MultivaluedMap<String, String> formParams) throws Exception {
        try {
            // TODO: Figure out a better way to return proper JSON objects instead of this hack
            if (action.equalsIgnoreCase("view") && resource.equalsIgnoreCase("cert")) {
                return this.getCertificate(itemId);
            }
            if (action.equalsIgnoreCase("importbystream") && resource.equalsIgnoreCase("cert")) {
                return this.importCertificateByStream(itemId.substring(1), formParams);
            }
            List<String> params = new ArrayList<String>();
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
            int length = formParams.size();
            for (int index = 0; index < length; index++) {
                if (formParams.containsKey(String.valueOf(index))) {
                    params.add(formParams.getFirst(String.valueOf(index)));
                    formParams.remove(index);
                }
            }
            iter = formParams.keySet().iterator();
            while (iter.hasNext()) {
                String valueKey = iter.next();
                String valueParam = formParams.getFirst(valueKey);
                params.add(valueKey + "=" + valueParam);
            }
            CommandResult output = processor.feedCommand(resource, params);
            return output;
        } catch (Exception ex) {
            Logger.getLogger(ApiResource.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            throw ex;
            // return Response.status(506).entity( ex.getMessage()).build();
        }
    }

    @RolesAllowed("ADMIN")
    @PUT
    @Path("/{resource}/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public CommandResult putCommand(@PathParam("param") String resource, @PathParam("id") String itemId, MultivaluedMap<String, String> formParams) throws Exception {
        return postCommand(resource, "add", itemId, formParams);
    }

    @RolesAllowed("ADMIN")
    @DELETE
    @Path("/{resource}/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public CommandResult deleteCommand(@PathParam("resource") String resource, @PathParam("id") String itemId) throws Exception {
        return getCommand(resource, "delete", itemId);
    }

    @RolesAllowed("ADMIN")
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
            AliasedCertificateFactory certFx = (AliasedCertificateFactory) processor.getSession().getCertificateFactory();
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

}
