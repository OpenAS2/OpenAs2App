package org.openas2.util;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.openas2.OpenAS2Exception;

import javax.mail.internet.InternetHeaders;
import java.io.IOException;

public class ResponseWrapper {
    private String _transferTimeMs = "-1"; // amount of time in milliseconds taken to send a message and receive a response
    private int _statusCode = 0;
    private String _statusPhrase = null;
    private InternetHeaders _headers = new InternetHeaders();

    private byte[] _body = null;

    public ResponseWrapper(HttpResponse response) throws OpenAS2Exception {
        super();
        setStatusCode(response.getStatusLine().getStatusCode());
        setStatusPhrase(response.getStatusLine().getReasonPhrase());

        for (org.apache.http.Header header : response.getAllHeaders()) {
            this.addHeaderLine(header.toString());
        }

        HttpEntity entity = response.getEntity();
        if (entity == null) {
            return;
        }
        byte[] data = null;
        try {
            data = EntityUtils.toByteArray(entity);
        } catch (IOException e) {
            throw new OpenAS2Exception("Failed to read response content", e);
        }
        setBody(data);
    }

    public InternetHeaders getHeaders() {
        return _headers;
    }

    public void setHeaders(InternetHeaders headers) {
        this._headers = headers;
    }

    public void addHeaderLine(String headerLine) {
        getHeaders().addHeaderLine(headerLine);
    }

    public String getTransferTimeMs() {
        return _transferTimeMs;
    }

    public void setTransferTimeMs(String transferTimeMs) {
        this._transferTimeMs = transferTimeMs;
    }

    public int getStatusCode() {
        return _statusCode;
    }

    protected void setStatusCode(int n) {
        this._statusCode = n;
    }

    public String getStatusPhrase() {
        return _statusPhrase;
    }

    public void setStatusPhrase(String statusPhrase) {
        this._statusPhrase = statusPhrase;
    }

    public byte[] getBody() {
        return _body;
    }

    protected void setBody(byte[] body) {
        this._body = body;
    }

}
