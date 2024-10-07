/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.openas2.cmd.processor.restapi;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.http.HttpHeaders;
import org.bouncycastle.util.encoders.Base64;
import org.openas2.cmd.CommandResult;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import jakarta.ws.rs.ext.Provider;
import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;



/**
 * @author javier
 */
@Provider
@PreMatching
public class AuthenticationRequestFilter implements ContainerRequestFilter {

    @Context
    private ResourceInfo resourceInfo;

    private static final String AUTHORIZATION_PROPERTY = "Authorization";
    private static final String AUTHENTICATION_SCHEME = "Basic";
    public static final String ACCESS_DENIED_ERROR_MSG = "You cannot access this resource";
    private static final CommandResult ERROR_ACCESS_DENIED = new CommandResult(CommandResult.TYPE_ERROR, ACCESS_DENIED_ERROR_MSG);
    //private static final CommandResult ERROR_ACCESS_FORBIDDEN = new CommandResult(CommandResult.TYPE_ERROR, "Access blocked for all users !!");
    private static String adminUsername;
    private static String adminPassword;
    private final Logger logger = LoggerFactory.getLogger(AuthenticationRequestFilter.class);


    public static void setCredentials(String userId,String password) {
        adminUsername = userId;
        adminPassword = password;
    }
    
    // Response including indication to remote server which scheme to authenticate with
    private void setUnauthorizedResponse(ContainerRequestContext requestContext) {
        requestContext.abortWith(Response.status(Status.UNAUTHORIZED)
                   .header(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"realm\"")
                   .entity(AuthenticationRequestFilter.ERROR_ACCESS_DENIED).build());
    }

    @Override
    public void filter(final ContainerRequestContext requestContext) throws IOException {
        requestContext.setSecurityContext(new SecurityContext() {
            @Override
            public Principal getUserPrincipal() {
                return new Principal() {
                    @Override
                    public String getName() {
                        return "OpenAS2";
                    }
                };
                
            }

            @Override
            public boolean isUserInRole(String role) {
                return "ADMIN".equals(role);
            }

            @Override
            public boolean isSecure() {
               return false;
            }

            @Override
            public String getAuthenticationScheme() {
               return AUTHENTICATION_SCHEME;
            }
            
        });
        
        //Get request headers
        final MultivaluedMap<String, String> headers = requestContext.getHeaders();

        //Fetch authorization header
        final List<String> authorization = headers.get(AUTHORIZATION_PROPERTY);

        //If no authorization information present; block access
        if (authorization == null || authorization.isEmpty()) {
            setUnauthorizedResponse(requestContext);
            return;
        }

        //Get encoded username and password
        final String encodedUserPassword = authorization.get(0).replaceFirst(AUTHENTICATION_SCHEME + " ", "");
        //Decode username and password
        String usernameAndPassword = new String(Base64.decode(encodedUserPassword.getBytes()));

        //Split username and password tokens
        final StringTokenizer tokenizer = new StringTokenizer(usernameAndPassword, ":");
        final String username = tokenizer.nextToken();
        final String password = tokenizer.nextToken();

        //Log Username and password for verification
        logger.info("Username: " + username);
        /*
         * NEVER LOG PASSWORDS
        if (password.length() > 0) {
            logger.info("password: " + new String(new char[password.length()]).replace("\0", "*"));
        } else {
            logger.info("password: <none>");
        }
        */
         
        //Verify user access
        Set<String> rolesSet = new HashSet<>();
        rolesSet.add("ADMIN");

        //Is user valid?
        if (!isUserAllowed(username, password, rolesSet)) {
            setUnauthorizedResponse(requestContext);
            return;           
        }
         
    }

    private boolean isUserAllowed(final String username, final String password, final Set<String> rolesSet) {
        boolean isAllowed = false;

        if (username.equals(adminUsername) && password.equals(adminPassword)) {
            String userRole = "ADMIN";

            //Step 2. Verify user role
            if (rolesSet.contains(userRole)) {
                isAllowed = true;
            }
        }
        return isAllowed;
    }

}
