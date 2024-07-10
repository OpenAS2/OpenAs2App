package org.openas2;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import org.glassfish.jersey.client.authentication.HttpAuthenticationFeature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.MockedStatic;
import org.openas2.cmd.processor.restapi.ApiResource;
import org.openas2.logging.Logger;
import org.openas2.util.Properties;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class ApiResourceTest {

    @Mock
    private Logger logger;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ApiResource apiResource;

    private Client client;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // Set up the client with basic authentication
        HttpAuthenticationFeature authFeature = HttpAuthenticationFeature.basicBuilder()
                .credentials("userID", "pWd")
                .build();

        client = ClientBuilder.newClient().register(authFeature);
    }

    @Test
    public void testGetProperties() throws Exception {
            Response response = apiResource.getPropertyList();
            assertEquals(Response.Status.OK.getStatusCode(), response.getStatus());

    }
}
