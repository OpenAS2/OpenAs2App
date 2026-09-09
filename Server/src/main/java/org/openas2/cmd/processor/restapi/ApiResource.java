/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.openas2.cmd.processor.restapi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.openas2.cert.AliasedCertificateFactory;
import org.openas2.cert.CertificateFactory;
import org.openas2.cmd.CommandResult;
import org.openas2.cmd.processor.RestCommandProcessor;
import org.openas2.processor.ProcessorModule;
import org.openas2.processor.msgtracking.DbTrackingModule;
import org.openas2.processor.msgtracking.TrackingModule;
import org.openas2.util.AS2Util;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.Consumes;

import jakarta.ws.rs.DefaultValue;


import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.HEAD;


import jakarta.ws.rs.PathParam;

import jakarta.ws.rs.core.Context;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import org.slf4j.LoggerFactory;

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

    /**
     * Partially updates one object: only what is supplied changes and anything omitted is left alone.
     * <p>
     * For a partner or a partnership this runs the "update" command, which exists so a caller does
     * not have to delete and recreate an entry to change it. For a certificate it runs the same
     * import the POST endpoint uses, because importing already replaces the certificate held under
     * an alias in place.
     *
     * @param resource - "partner", "partnership" or "cert"
     * @param itemId - the name of the partner or partnership, or the certificate alias
     * @param formParams - the attributes to set, or for a certificate the "data" field
     * @return the command result as JSON
     */
    @RolesAllowed({"ADMIN"})
    @PATCH
    @Path("/{resource}/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response patchCommand(@PathParam("resource") String resource, @PathParam("id") String itemId, MultivaluedMap<String, String> formParams) throws Exception {
        try {
            if (itemId == null || itemId.trim().length() == 0) {
                CommandResult error = new CommandResult(CommandResult.TYPE_ERROR, "The name of the item to update must be supplied.");
                return Response.status(400).entity(this.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(error))
                        .type(MediaType.APPLICATION_JSON).build();
            }
            CommandResult output;
            if ("cert".equalsIgnoreCase(resource)) {
                // Importing a certificate already overwrites the alias, so there is no separate
                // update command for one
                try {
                    String keyStorePassword = formParams == null ? null : formParams.getFirst("password");
                    if (keyStorePassword != null && keyStorePassword.length() > 0) {
                        // A password means the payload is a keystore holding a key pair, so the
                        // certificate and its private key are replaced together
                        output = this.importKeyPairByStream(itemId, formParams, keyStorePassword);
                    } else {
                        output = this.importCertificateByStream(itemId, formParams);
                    }
                } catch (Exception e) {
                    /*
                     * The keystore refuses to replace the certificate of an alias that holds a
                     * private key, since that would orphan the key. Report it to the caller instead
                     * of failing the request.
                     */
                    LoggerFactory.getLogger(ApiResource.class.getName()).error("Failed to replace the certificate for alias " + itemId, e);
                    output = new CommandResult(CommandResult.TYPE_ERROR,
                            "Could not replace the certificate for alias \"" + itemId + "\": " + e.getMessage());
                }
            } else {
                /*
                 * processRequest expects the item ID with the leading path separator still on it
                 * because the generic endpoints capture it that way, so put one back before handing
                 * the bare ID from this endpoint's path over.
                 */
                output = processRequest(resource, "update", "/" + itemId, formParams);
            }
            String jsonResult = this.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
            return Response.status(200).entity(jsonResult).type(MediaType.APPLICATION_JSON).build();
        } catch (Exception ex) {
            LoggerFactory.getLogger(ApiResource.class.getName()).error(ex.getMessage(), ex);
            throw ex;
        }
    }

    @RolesAllowed({"ADMIN"})
    @PUT
    @Path("/{resource}/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response putCommand(@PathParam("resource") String resource, @PathParam("id") String itemId, MultivaluedMap<String, String> formParams) throws Exception {
        return postCommand(resource, "add", "/" + itemId, formParams);
    }

    @RolesAllowed({"ADMIN"})
    @DELETE
    @Path("/{resource}/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response deleteCommand(@PathParam("resource") String resource, @PathParam("id") String itemId) throws Exception {
        return getCommand(resource, "delete", "/" + itemId);
    }

    @RolesAllowed({"ADMIN"})
    @HEAD
    @Path("/{resource}{action:(/[^/]+?)?}{id:(/[^/]+?)?}")
    public Response headCommand(@PathParam("resource") String resource) {
        // Just an Empty response
        return Response.status(200).build();
    }

    /**
     * Downloads the stored MDN for a message identified by its AS2 message ID.
     * <p>
     * The file that gets streamed is only ever the path recorded in the tracking database by the
     * MDN storage module: the caller supplies a message ID, never a path, so this cannot be used to
     * read arbitrary files. The content originates from a trading partner, so it is served as an
     * attachment with no-sniff set rather than as anything a browser will render inline.
     *
     * @param msgId - the AS2 message ID of the message the MDN was returned for, URL encoded
     * @return the MDN file as an attachment, or a JSON error result
     */
    @RolesAllowed({"ADMIN"})
    @GET
    @Path("/messages/mdn/{msgId}")
    @Produces({MediaType.APPLICATION_OCTET_STREAM, MediaType.APPLICATION_JSON})
    public Response downloadMdn(@PathParam("msgId") String msgId) throws Exception {
        try {
            if (msgId == null || msgId.trim().length() == 0) {
                return errorResponse(400, "An AS2 message ID must be supplied.");
            }
            DbTrackingModule db = getDbTrackingModule();
            if (db == null) {
                return errorResponse(503, "No DB tracking module available so no MDN can be located.");
            }

            String trimmedId = msgId.trim();
            String mdnFilePath = db.getMdnFilePathByMessageId(trimmedId);
            if (mdnFilePath == null) {
                return errorResponse(404, "No stored MDN found for message ID: " + trimmedId);
            }

            File mdnFile = new File(mdnFilePath);
            if (!mdnFile.isFile() || !mdnFile.canRead()) {
                // The tracking record outlives the file, so a missing file is a normal operational
                // state (archived or cleaned up) rather than a server fault
                LoggerFactory.getLogger(ApiResource.class.getName())
                        .warn("MDN file recorded for message ID " + trimmedId + " is not readable: " + mdnFilePath);
                return errorResponse(404, "The MDN recorded for message ID " + trimmedId
                        + " is no longer available on disk.");
            }

            return Response.status(200)
                    .entity(mdnFile)
                    .type(MediaType.APPLICATION_OCTET_STREAM)
                    .header("Content-Disposition", "attachment; filename=\"" + toSafeDownloadName(mdnFile) + "\"")
                    .header("X-Content-Type-Options", "nosniff")
                    .build();
        } catch (Exception ex) {
            LoggerFactory.getLogger(ApiResource.class.getName()).error(ex.getMessage(), ex);
            throw ex;
        }
    }

    /**
     * @return the first configured DB tracking module, or null if message tracking is not using one
     */
    private DbTrackingModule getDbTrackingModule() throws Exception {
        List<ProcessorModule> modules = getProcessor().getSession().getProcessor()
                .getModulesSupportingAction(TrackingModule.DO_TRACK_MSG);
        if (modules == null) {
            return null;
        }
        for (ProcessorModule module : modules) {
            if (module instanceof DbTrackingModule) {
                return (DbTrackingModule) module;
            }
        }
        return null;
    }

    /**
     * Reduces the stored file name to characters that are safe to place in a quoted
     * Content-Disposition value. The name comes from the database so it is treated as untrusted:
     * stripping everything else rules out header injection and quote breaking.
     */
    private String toSafeDownloadName(File mdnFile) {
        String name = mdnFile.getName().replaceAll("[^A-Za-z0-9._-]", "_");
        return name.length() == 0 ? "mdn" : name;
    }

    /**
     * Builds an error response in the same JSON shape as the command backed endpoints so API clients
     * get one consistent error format.
     */
    private Response errorResponse(int status, String message) throws Exception {
        CommandResult result = new CommandResult(CommandResult.TYPE_ERROR, message);
        return Response.status(status)
                .entity(this.mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }

    /**
     * Replaces the certificate and private key held under an alias from a PKCS12 keystore supplied as
     * base64 in the "data" field, with the password that opens it in the "password" field.
     * <p>
     * This is the path for rotating an identity of our own: the alias already holds a private key, and
     * replacing only the certificate is refused because it would orphan the key. The previous key pair
     * stays in place unless the new one is written successfully.
     *
     * @param alias - the keystore alias to write the key pair to
     * @param formParams - carries "data", the base64 encoded PKCS12
     * @param keyStorePassword - the password that opens the supplied PKCS12
     * @return the command result to return to the caller
     */
    private CommandResult importKeyPairByStream(String alias, MultivaluedMap<String, String> formParams, String keyStorePassword) throws Exception {
        String payload = formParams.getFirst("data");
        if (payload == null || payload.length() == 0) {
            return new CommandResult(CommandResult.TYPE_ERROR, "No \"data\" field holding a base64 encoded PKCS12 keystore was supplied.");
        }
        AliasedCertificateFactory certFx = (AliasedCertificateFactory) getProcessor().getSession()
                .getCertificateFactory(CertificateFactory.COMPID_AS2_CERTIFICATE_FACTORY);
        KeyStore sourceKeyStore;
        try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(payload))) {
            sourceKeyStore = AS2Util.getCryptoHelper().loadKeyStore(bais, keyStorePassword.toCharArray());
        }
        if (!certFx.importPrivateKey(alias, sourceKeyStore, keyStorePassword)) {
            return new CommandResult(CommandResult.TYPE_ERROR,
                    "The supplied keystore holds no certificate with a private key, so there is nothing to replace the key pair with.");
        }
        return new CommandResult(CommandResult.TYPE_OK, "Replaced the certificate and private key for alias: " + alias);
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
            // return Response.status(506).entity( ex.getMessage()).build();
        }
    }

}
