package org.openas2.util;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import javax.activation.DataSource;


public class ByteArrayDataSource implements DataSource {
    String contentType;
    String name;
    byte[] bytes;

    public ByteArrayDataSource(byte[] bytes, String contentType, String name) {
        this.bytes = bytes;

        if (contentType == null) {
            this.contentType = "application/octet-stream";
        } else {
            this.contentType = contentType;
        }

        this.name = name;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getContentType() {
        return contentType;
    }

    public InputStream getInputStream() {
        // remove the final CR/LF
        int len = bytes.length;
        /*if (len > 1 && new String(bytes, len-2, 2).equals("\r\n")) {
        	len -= 2;
        }*/
        return new ByteArrayInputStream(bytes, 0, len);
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public OutputStream getOutputStream() throws IOException {
        throw new FileNotFoundException();
    }
}
