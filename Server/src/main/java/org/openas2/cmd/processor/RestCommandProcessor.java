/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.openas2.cmd.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.io.StringWriter;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.internal.util.collection.ImmutableMultivaluedMap;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.WrappedException;
import org.openas2.cmd.Command;
import org.openas2.cmd.CommandResult;
import org.openas2.cmd.processor.restapi.ControlResource;
import org.openas2.util.CommandTokenizer;

/**
 *
 * @author javier
 */
public class RestCommandProcessor extends BaseCommandProcessor {

    private final Log logger = LogFactory.getLog(RestCommandProcessor.class.getSimpleName());

    public static final String BASE_URI = "http://localhost:8080/";
    private HttpServer server;
    private final ObjectMapper mapper;
    public RestCommandProcessor() {
        super();
        // create the mapper
        mapper = new ObjectMapper();
        // enable pretty printing
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public void processCommand() throws Exception {
        //throw new UnsupportedOperationException("Not supported yet."); 
        //logger.info("Rest API Ready");
    }


    public String feedCommand(String commandText,  List<String> params) throws WrappedException, Exception {
        StringWriter output = new StringWriter();
        if (commandText != null && commandText.length() > 0) {
            String commandName = commandText.toLowerCase();
            if (commandName.equals(StreamCommandProcessor.EXIT_COMMAND)) {
                terminate();
            } else {
                Command cmd = getCommand(commandName);
                if (cmd != null) {
                    CommandResult result = cmd.execute(params.toArray());
                    if (result.getType() == null ? CommandResult.TYPE_OK == null : result.getType().equals(CommandResult.TYPE_OK)) {
                        // serialize the object
                        mapper.writeValue(output,result.getResults());
                    } else {
                        output.append(StreamCommandProcessor.COMMAND_ERROR);
                        mapper.writeValue(output,result.getResults());
                    }
                } else {
                    output.append(StreamCommandProcessor.COMMAND_NOT_FOUND);
                    List<Command> l = getCommands();
                    output.append("List of commands:" + "\r\n");
                    for (int i = 0; i < l.size(); i++) {
                        cmd = l.get(i);
                        output.append(cmd.getName() + "\r\n");
                    }
                }
            }
        }

        return output.toString();
    }

    @Override
    public void destroy() throws Exception {
        super.destroy();
        server.shutdown();
        logger.info(this.getName() + " destroyed...");
    }

    @Override
    public void init(Session session, Map<String, String> parameters) throws OpenAS2Exception {
        super.init(session, parameters);
        logger.info(this.getName() + " initialized...");
        // create a resource config that scans for JAX-RS resources and providers
        // in com.example.rest package
        final ResourceConfig rc = new ResourceConfig();//.packages("org.openas2.cmd.processor.restapi");
        rc.register(new ControlResource(this));
        /**
         * Secure Server SSLContextConfigurator sslCon = new
         * SSLContextConfigurator();
         *
         * sslCon.setKeyStoreFile(ConfigLoader.getKeystoreLocation()); //
         * contains server keypair
         * sslCon.setKeyStorePass(ConfigLoader.getKeystorePassword());
         *
         * HttpHandler hand =
         * ContainerFactory.createContainer(HttpHandler.class, rc);
         *
         * HttpServer secure =
         * GrizzlyServerFactory.createHttpServer(BASE_URI_SECURED, hand, true,
         * new SSLEngineConfigurator(sslCon, false, false, false));
         *
         * return secure;
        *
         */
        // create and start a new instance of grizzly http server
        // exposing the Jersey application at BASE_URI
        try {
            server = GrizzlyHttpServerFactory.createHttpServer(URI.create(parameters.getOrDefault("baseuri", BASE_URI)), rc);
            server.start();
        } catch (IOException ex) {
            Logger.getLogger(RestCommandProcessor.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
