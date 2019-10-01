/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.openas2.cmd.processor.restapi;

import java.io.IOException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.ext.Provider;
import org.apache.commons.logging.Log;

/**
 *
 * @author javier
 */
@Provider
public class LoggerRequestFilter implements javax.ws.rs.container.ContainerRequestFilter{
    protected Log logger;
    public LoggerRequestFilter(Log logger) {
        this.logger=logger;
    }
    
    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        this.logger.info("API Request: " + requestContext.getMethod() + " /" +requestContext.getUriInfo().getPath());
    }
    
}
