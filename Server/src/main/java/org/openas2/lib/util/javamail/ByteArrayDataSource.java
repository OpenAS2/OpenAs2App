package org.openas2.lib.util.javamail;

import javax.activation.DataSource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;


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
        return new ByteArrayInputStream(bytes, 0, bytes.length);
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public OutputStream getOutputStream() throws IOException {
        return new WrappedOutputStream(this);
    }

    private class WrappedOutputStream extends ByteArrayOutputStream {
        private ByteArrayDataSource owner;

        public WrappedOutputStream(ByteArrayDataSource owner) {
            super();
            this.owner = owner;
        }

        public void close() {
            if (getOwner() != null) {
                getOwner().setBytes(toByteArray());
            }
        }

        public ByteArrayDataSource getOwner() {
            return owner;
        }
    }
}
