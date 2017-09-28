/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.openas2.cmd.processor.restapi;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.HEAD;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.UriInfo;
import org.openas2.cmd.CommandResult;
import org.openas2.cmd.processor.RestCommandProcessor;
/**
 *
 * @author javier
 */
@Path("control")
public class ControlResource {
    private final RestCommandProcessor processor;
    @Context UriInfo ui;
    @Context Request request;
    private final ObjectMapper mapper;
    
    public ControlResource(RestCommandProcessor processor) {
        this.processor = processor;
        // create the mapper
        mapper = new ObjectMapper();
        // enable pretty printing
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }
    
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getVersion() {
        return processor.getSession().getAppTitle();
    }
    
    @GET
    @Path("/{resource}/{action}{id:(/[^/]+?)?}")
    @Produces(MediaType.APPLICATION_JSON)
    public CommandResult getCommand(@PathParam("resource") String resource, 
            @PathParam("action") @DefaultValue("list") String action, @PathParam("id") String itemId) throws Exception {
        try {
            List<String> params = new ArrayList<String>();
            if(action != null) {
                params.add(action);
            }
            if(itemId != null && itemId.length() > 1) {
                params.add(itemId.substring(1));
            }
            Iterator<String> iter = ui.getQueryParameters().keySet().iterator();
            while(iter.hasNext()) {
                String valueKey=iter.next();
                String valueParam = ui.getQueryParameters().getFirst(valueKey);
                params.add(valueKey+"="+valueParam);
            }
            CommandResult output = processor.feedCommand(resource,params);
            return output;
        } catch (Exception ex) {
            Logger.getLogger(ControlResource.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            throw ex;
           // return Response.status(506).entity( ex.getMessage()).build();
        }
        
    }
    
    @POST
    @Path("/{resource}/{action}{id:(/[^/]+?)?}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public CommandResult postCommand(@PathParam("resource") String resource, 
            @PathParam("action") @DefaultValue("list") String action, @PathParam("id") String itemId,MultivaluedMap<String, String> formParams) throws Exception {
        try {
            List<String> params = new ArrayList<String>();
            if(action != null) {
                params.add(action);
            }
            if(itemId != null && itemId.length() > 1) {
                params.add(itemId.substring(1));
            }
            Iterator<String> iter = ui.getQueryParameters().keySet().iterator();
            while(iter.hasNext()) {
                String valueKey=iter.next();
                String valueParam = ui.getQueryParameters().getFirst(valueKey);
                params.add(valueKey+"="+valueParam);
            }
            iter = formParams.keySet().iterator();
            while(iter.hasNext()) {
                String valueKey=iter.next();
                String valueParam = formParams.getFirst(valueKey);
                params.add(valueKey+"="+valueParam);
            }
            CommandResult output = processor.feedCommand(resource,params);
            return output;
        } catch (Exception ex) {
            Logger.getLogger(ControlResource.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            throw ex;
           // return Response.status(506).entity( ex.getMessage()).build();
        }
    }

    @PUT
    @Path("/{resource}/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public CommandResult putCommand(@PathParam("param") String resource, @PathParam("id") String itemId,MultivaluedMap<String, String> formParams) throws Exception {
        return postCommand(resource, "add", itemId,formParams);
    }

    @DELETE
    @Path("/{resource}/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public CommandResult deleteCommand(@PathParam("resource") String resource, @PathParam("id") String itemId) throws Exception {
        return getCommand(resource, "delete", itemId);
    }

    @HEAD
    @Path("/{resource}{action:(/[^/]+?)?}{id:(/[^/]+?)?}")
    public Response headCommand(@PathParam("param") String command) {
        String output = "HEAD:Jersey say : " + command;
        return Response.status(200).entity(output).build();
    }

    
}
