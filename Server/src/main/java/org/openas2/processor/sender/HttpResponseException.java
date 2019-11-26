package org.openas2.processor.sender;

import org.openas2.OpenAS2Exception;

public class HttpResponseException extends OpenAS2Exception {
    /**
     *
     */
    private static final long serialVersionUID = 1L;
    private String message;
    private String url;
    private int code;

    public HttpResponseException(String url, int code, String message) {
        super("Http Response from " + url + ": " + code + " - " + message);
        this.code = code;
        this.message = message;
        this.url = url;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUrl() {
        return url;
    }
}
