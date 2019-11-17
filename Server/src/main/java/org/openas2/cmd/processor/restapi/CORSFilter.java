package org.openas2.cmd.processor.restapi;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

/**
 * @author javier munoz (javier@igwtech.net)
 */
@Provider
public class CORSFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        // if there is no Origin header, then it is not a
        // cross origin request. We don't do anything.
        if (requestContext.getHeaderString("Origin") == null) {
            return;
        }
        // If it is a preflight request, then we add all
        // the CORS headers here.
        MultivaluedMap<String, Object> headers = responseContext.getHeaders();
        headers.add("Access-Control-Allow-Origin", requestContext.getHeaderString("Origin")); // for now, allows CORS requests coming from any source
        if (this.isPreflightRequest(requestContext)) {

            headers.add("Access-Control-Allow-Credentials", true);


            headers.add("Access-Control-Allow-Headers", "X-Requested-With, Content-Type, Authorization");

            headers.add("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT, OPTIONS");

            headers.add("Access-Control-Max-Age", 86400);
            headers.add("Vary", "Accept-Encoding, Origin");
            responseContext.setStatus(200);
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        // Browser does pre-flight CORS checks
        // If it's a preflight request, we abort the request with
        // a 200 status, and the CORS headers are added in the
        // response filter method below.
        if (isPreflightRequest(requestContext)) {
            requestContext.abortWith(Response.ok().build());
        }
    }

    /**
     * A preflight request is an OPTIONS request with an Origin header.
     */
    private boolean isPreflightRequest(ContainerRequestContext request) {
        return request.getHeaderString("Origin") != null && request.getMethod().equalsIgnoreCase("OPTIONS");
    }

}
