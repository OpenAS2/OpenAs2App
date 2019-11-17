/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.openas2.cmd.processor.restapi;

import org.apache.commons.logging.Log;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

/**
 * @author javier
 */
@Provider
public class LoggerRequestFilter implements ContainerRequestFilter, ContainerResponseFilter {
    protected Log logger;

    public LoggerRequestFilter(Log logger) {
        this.logger = logger;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        this.logger.info("API Request: " + requestContext.getMethod() + " /" + requestContext.getUriInfo().getPath());
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        this.logger.info("API Response: " + responseContext.getStatus() + responseContext.getHeaders().toString());
    }

}
