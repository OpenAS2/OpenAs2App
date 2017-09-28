/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.openas2.cmd.processor.restapi;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
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
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.UriInfo;
import org.openas2.cmd.processor.RestCommandProcessor;
import org.openas2.cmd.processor.SocketCommandParser;
import org.xml.sax.SAXException;
/**
 *
 * @author javier
 */
@Path("control")
public class ControlResource {
    private final RestCommandProcessor processor;
    @Context UriInfo ui;
    
    public ControlResource(RestCommandProcessor processor) {
        this.processor = processor;
    }
    
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    public String getIt() {
        return processor.getSession().getAppTitle();
    }
    
    @POST
    @Produces(MediaType.TEXT_XML)
    @Consumes(MediaType.TEXT_PLAIN)
    public String postIt(String data) {
        try {
            List<String> params = new ArrayList<String>();
            Iterator<String> iter = ui.getQueryParameters().keySet().iterator();
            while(iter.hasNext()) {
                String valueParam = ui.getQueryParameters().getFirst(iter.next());
                params.add(valueParam);
            }
            return processor.feedCommand(data,params);
        } catch (Exception ex) {
            Logger.getLogger(ControlResource.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            return ex.getMessage();
        }
    }
    
    @GET
    @Path("/{resource}/{action}{id:(/[^/]+?)?}")
    public Response getCommand(@PathParam("resource") String resource, 
            @PathParam("action") @DefaultValue("list") String action, @PathParam("id") String itemId) {
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
                String valueParam = ui.getQueryParameters().getFirst(iter.next());
                params.add(valueParam);
            }
            String output = processor.feedCommand(resource,params);
            return Response.status(200).entity(output).build();
        } catch (Exception ex) {
            Logger.getLogger(ControlResource.class.getName()).log(Level.SEVERE, ex.getMessage(), ex);
            return Response.status(506).entity( ex.getMessage()).build();
        }
        
    }
    
    @POST
    @Path("/{resource}/{action}{id:(/[^/]+?)?}")
    public Response postCommand(@PathParam("param") String command) {
        String output = "POST:Jersey say : " + command;
        return Response.status(200).entity(output).build();
    }

    @POST
    @Path("/post")
    @Consumes(MediaType.TEXT_XML)
    public Response postStrMsg( String msg) {
        String output = "POST:Jersey say : " + msg;
        return Response.status(200).entity(output).build();
    }

    @PUT
    @Path("/{resource}/{id}")
    public Response putCommand(@PathParam("param") String resource, @PathParam("id") String itemId) {
        return getCommand(resource, "create", itemId);
    }

    @DELETE
    @Path("/{resource}/{id}")
    public Response deleteCommand(@PathParam("resource") String resource, @PathParam("id") String itemId) {
        return getCommand(resource, "delete", itemId);
    }

    @HEAD
    @Path("/{resource}{action:(/[^/]+?)?}{id:(/[^/]+?)?}")
    public Response headCommand(@PathParam("param") String command) {
        String output = "HEAD:Jersey say : " + command;
        return Response.status(200).entity(output).build();
    }

    
}
