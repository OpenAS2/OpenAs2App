package org.openas2.lib.util;

import org.openas2.Session;
import org.openas2.lib.util.javamail.ByteArrayDataSource;

import javax.activation.DataHandler;
import javax.mail.MessagingException;
import javax.mail.internet.InternetHeaders;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;


public class MimeUtil {
    private static final String HEADER_VALUE_SEPARATOR = ", ";

    public static int getContentLength(InternetHeaders headers) throws IOException {
        // get the content length
        String contentLengthStr = getHeader(headers, "Content-Length");

        // content-length is required
        if (contentLengthStr == null) {
            throw new IOException("Content-Length missing");
        }

        // convert the content length to an int
        try {
            return Integer.parseInt(contentLengthStr);
        } catch (NumberFormatException nfe) {
            throw new IOException("Invalid Content-Length: " + contentLengthStr);
        }
    }

    public static String getHeader(InternetHeaders headers, String key) {
        return getHeader(headers, key, HEADER_VALUE_SEPARATOR);
    }

    public static String getHeader(InternetHeaders headers, String key, String delimiter) {
        // TODO test this to make sure it returns null if no header values exist
        // - I remember something about it returning a blank string instead
        String value = headers.getHeader(key, delimiter);

        if (value == null) {
            return null;
        } else if (value.equalsIgnoreCase("null")) {
            return null;
        } else {
            return value;
        }
    }

    public static MimeBodyPart createMimeBodyPart(byte[] data, String contentType, String contentTransferEncoding) throws MessagingException {
        // create a MimeBodyPart and set up it's content and content headers
        MimeBodyPart part = new MimeBodyPart();
        part.setDataHandler(new DataHandler(new ByteArrayDataSource(data, contentType, null)));
        part.setHeader("Content-Type", contentType);
        part.setHeader("Content-Transfer-Encoding", contentTransferEncoding);

        return part;
    }

    public static MimeBodyPart createMimeBodyPart(MimeMultipart multipart) throws MessagingException {
        MimeBodyPart part = new MimeBodyPart();
        part.setContent(multipart);
        part.setHeader("Content-Type", multipart.getContentType());

        return part;
    }

    public static MimeMultipart createMimeMultipart(MimeBodyPart bodypart) throws MessagingException {
        return new MimeMultipart(bodypart.getDataHandler().getDataSource());
    }

    public static InternetHeaders readHeaders(InputStream source) throws MessagingException {
        // read in the MIME headers
        return new InternetHeaders(source);
    }

    public static MimeBodyPart readMimeBodyPart(InputStream source, InternetHeaders headers) throws IOException, MessagingException {
        // get the length of the Mime body's data
        int contentLength = getContentLength(headers);

        // read the data into a byte array
        DataInputStream dataIn = new DataInputStream(source);
        byte[] data = new byte[contentLength];
        dataIn.readFully(data);

        // convert the byte array to a MimeBodyPart
        String contentTransferEncoding = getHeader(headers, "Content-Transfer-Encoding");
        if (contentTransferEncoding == null) {
            contentTransferEncoding = Session.DEFAULT_CONTENT_TRANSFER_ENCODING;
        }
        return createMimeBodyPart(data, getHeader(headers, "Content-Type"), contentTransferEncoding);
    }

    public static String toString(MimeBodyPart mbp, boolean addDelimiterText) throws IOException, MessagingException {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        if (addDelimiterText) {
            os.write("========BEGIN MIMEBODYPART=========\n".getBytes());
        }
        mbp.writeTo(os);
        if (addDelimiterText) {
            os.write("\n========END MIMEBODYPART=========".getBytes());
        }
        return os.toString();
    }

}
