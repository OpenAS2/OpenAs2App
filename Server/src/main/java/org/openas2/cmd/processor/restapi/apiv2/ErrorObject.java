package org.openas2.cmd.processor.restapi.apiv2;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

public class ErrorObject {
    private String errorMessage;

    public ErrorObject() {
    }

    public ErrorObject(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public static Response getResponse(String errorMessage, Status status, ObjectMapper mapper) throws JsonProcessingException {
        return Response.status(status).entity(mapper.writeValueAsString(new ErrorObject(errorMessage))).type(MediaType.APPLICATION_JSON).build();
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
