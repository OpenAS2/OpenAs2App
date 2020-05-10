/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.openas2.cmd.processor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.ssl.SSLContextConfigurator;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpContainer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ContainerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.jackson.JacksonFeature;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.cmd.Command;
import org.openas2.cmd.CommandResult;
import org.openas2.cmd.processor.restapi.ApiResource;
import org.openas2.cmd.processor.restapi.AuthenticationRequestFilter;
import org.openas2.cmd.processor.restapi.CORSFilter;
import org.openas2.cmd.processor.restapi.LoggerRequestFilter;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author javier
 */
public class RestCommandProcessor extends BaseCommandProcessor {

    private final Log logger = LogFactory.getLog(RestCommandProcessor.class.getSimpleName());
    public static final String BASE_URI = "http://localhost:8080/";
    private HttpServer server;

    @Override
    public void processCommand() throws Exception {
        //throw new UnsupportedOperationException("Commands received by HTTP Server thread");
    }

    public CommandResult feedCommand(String commandText, List<String> params) throws Exception {
        CommandResult result = null;
        if (commandText != null && commandText.length() > 0) {
            String commandName = commandText.toLowerCase();
            if (commandName.equals(StreamCommandProcessor.SERVER_EXIT_COMMAND)) {
                terminate();
            } else {
                Command cmd = getCommand(commandName);
                if (cmd != null) {
                    result = cmd.execute(params.toArray());
                } else {
                    result = new CommandResult(StreamCommandProcessor.COMMAND_NOT_FOUND);
                    List<Command> l = getCommands();
                    for (int i = 0; i < l.size(); i++) {
                        cmd = l.get(i);
                        result.getResults().add(cmd.getName());
                    }
                }
            }
        }
        return result;
    }

    @Override
    public void destroy() throws Exception {
        try {
            super.destroy();
            server.shutdown();
            logger.info(this.getName() + " destroyed...");
        } catch (Exception e) {
            logger.error("failed to cleanup RestAPI command processor", e);
            throw e;
        }
    }

    @Override
    public void init(Session session, Map<String, String> parameters) throws OpenAS2Exception {
        try {
            super.init(session, parameters);
            logger.info(this.getName() + " initialized...");
            // create a resource config that scans for JAX-RS resources and providers
            final String userId = parameters.getOrDefault("userid", "userid");
            final String password = parameters.getOrDefault("password", "pWd");
            final ResourceConfig rc = new ResourceConfig();
            rc.register(new LoggerRequestFilter(logger))
              .register(new AuthenticationRequestFilter(userId, password))
              .register(new ApiResource(this))
              .register(new CORSFilter())
              .register(new JacksonFeature());
            URI baseUri = URI.create(parameters.getOrDefault("baseuri", BASE_URI));


            logger.info("Creating and starting a new instance of grizzly http server");
            logger.info("Exposing the Jersey application at " + baseUri);
            if (baseUri.getScheme().equalsIgnoreCase("https")) {
                //Secure Server
                SSLContextConfigurator sslCon = new SSLContextConfigurator();
                String keystore = parameters.get("ssl_keystore");
                if (null == keystore) {
                    throw new RuntimeException("Missing SSL Keystore parameter in the configuration. You cannot use SSL without a Certificate");
                }
                sslCon.setKeyStoreFile(keystore);
                sslCon.setKeyStorePass(parameters.getOrDefault("ssl_keystore_password", ""));
                sslCon.setSecurityProtocol(parameters.getOrDefault("ssl_protocol", "TLS"));

                GrizzlyHttpContainer container = ContainerFactory.createContainer(GrizzlyHttpContainer.class, rc);
                SSLEngineConfigurator sslEngineConfigurator = new SSLEngineConfigurator(sslCon, false, false, false);
                server = GrizzlyHttpServerFactory.createHttpServer(baseUri, container, true, sslEngineConfigurator, true);
            } else {
                server = GrizzlyHttpServerFactory.createHttpServer(baseUri, rc);
            }
            server.start();
        } catch (IOException ex) {
            Logger.getLogger(RestCommandProcessor.class.getName()).log(Level.SEVERE, null, ex);
            throw new OpenAS2Exception(ex);
        } catch (RuntimeException ex) {
            Logger.getLogger(RestCommandProcessor.class.getName()).log(Level.SEVERE, null, ex);
            throw new OpenAS2Exception(ex);
        }
    }

}
