/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.openas2.cmd.processor.restapi;

import org.slf4j.Logger;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

/**
 * @author javier
 */
@Provider

public class LoggerRequestFilter implements ContainerRequestFilter, ContainerResponseFilter {
    private static Logger logger;

    public LoggerRequestFilter() {
        
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        getLogger().info("API Request: " + requestContext.getMethod() + " /" + requestContext.getUriInfo().getPath());
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        getLogger().info("API Response: " + responseContext.getStatus() + responseContext.getHeaders().toString());
    }

    /**
     * @return the logger
     */
     public static Logger getLogger() {
        return logger;
    }

    /**
     * @param aLogger the logger to set
     */
    public static void setLogger(Logger aLogger) {
        logger = aLogger;
    }

}
