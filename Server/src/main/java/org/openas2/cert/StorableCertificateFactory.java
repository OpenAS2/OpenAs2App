package org.openas2.cert;


import org.openas2.OpenAS2Exception;
import org.openas2.params.InvalidParameterException;

import java.io.InputStream;
import java.io.OutputStream;


public interface StorableCertificateFactory extends CertificateFactory {
    void setFilename(String filename);

    String getFilename() throws InvalidParameterException;

    void setPassword(char[] password);

    char[] getPassword() throws InvalidParameterException;

    void load() throws OpenAS2Exception;

    void load(String filename, char[] password) throws OpenAS2Exception;

    void load(InputStream in, char[] password) throws OpenAS2Exception;

    void save() throws OpenAS2Exception;

    void save(String filename, char[] password) throws OpenAS2Exception;

    void save(OutputStream out, char[] password) throws OpenAS2Exception;
}
