package org.openas2.lib.util.javamail;

import org.openas2.lib.message.AS2Standards;

import javax.activation.ActivationDataFlavor;
import javax.activation.DataContentHandler;
import javax.activation.DataSource;
import javax.mail.MessagingException;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import java.awt.datatransfer.DataFlavor;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;


public class DispositionDataContentHandler implements DataContentHandler {
    private static final ActivationDataFlavor ADF1;
    private static final DataFlavor[] ADFs;

    static {
        ADF1 = new ActivationDataFlavor(MimeBodyPart.class, AS2Standards.DISPOSITION_TYPE, "Disposition Notification");
        ADFs = new DataFlavor[]{ADF1};
    }

    public DispositionDataContentHandler() {
        super();
    }

    public Object getContent(DataSource ds) throws IOException {
        byte[] buf = new byte[4096];
        BufferedInputStream bIn = new BufferedInputStream(ds.getInputStream());
        ByteArrayOutputStream baOut = new ByteArrayOutputStream();
        BufferedOutputStream bOut = new BufferedOutputStream(baOut);
        int count = bIn.read(buf);

        while (count > -1) {
            bOut.write(buf, 0, count);
            count = bIn.read(buf);
        }

        bIn.close();
        bOut.close();

        return baOut.toByteArray();
    }

    public Object getTransferData(DataFlavor df, DataSource ds) throws IOException {
        if (ADF1.equals(df)) {
            return getContent(ds);
        }
        return null;
    }

    public DataFlavor[] getTransferDataFlavors() {
        return ADFs;
    }

    public void writeTo(Object obj, String mimeType, OutputStream os) throws IOException {
        if (obj instanceof MimeBodyPart) {
            try {
                ((MimeBodyPart) obj).writeTo(os);
            } catch (MessagingException me) {
                throw new IOException(me.getMessage());
            }
        } else if (obj instanceof MimeMultipart) {
            try {
                ((MimeMultipart) obj).writeTo(os);
            } catch (MessagingException me) {
                throw new IOException(me.getMessage());
            }
        } else if (obj instanceof byte[]) {
            os.write((byte[]) obj);
        } else if (obj instanceof String) {
            os.write(((String) obj).getBytes());
        } else {
            throw new IOException("Unknown object type: " + obj.getClass().getName());
        }
    }
}
