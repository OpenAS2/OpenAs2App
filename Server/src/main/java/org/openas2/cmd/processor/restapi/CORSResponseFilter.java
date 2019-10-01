package org.openas2.cmd.processor.restapi;
import java.io.IOException;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.ext.Provider;

/**
 *
 * @author javier munoz (javier@igwtech.net)
 */
@Provider
public class CORSResponseFilter
implements ContainerResponseFilter {

	public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
			throws IOException {

		MultivaluedMap<String, Object> headers = responseContext.getHeaders();
                
		headers.add("Access-Control-Allow-Origin", requestContext.getHeaderString("Origin")); // for now, allows CORS requests coming from any source
		headers.add("Access-Control-Allow-Methods", "GET, POST, DELETE, PUT");			
		headers.add("Access-Control-Allow-Headers", "X-Requested-With, Content-Type");
                headers.add("Access-Control-Allow-Credentials", true);
                headers.add("Access-Control-Max-Age", 86400);
                headers.add("Vary", "Accept-Encoding, Origin");
	}

}