package org.openas2.cert;


import java.io.InputStream;
import java.io.OutputStream;

import org.openas2.OpenAS2Exception;
import org.openas2.params.InvalidParameterException;



public interface StorableCertificateFactory extends CertificateFactory {
    public void setFilename(String filename);

    public String getFilename() throws InvalidParameterException;

    public void setPassword(char[] password);

    public char[] getPassword() throws InvalidParameterException;

    public void load() throws OpenAS2Exception;

    public void load(String filename, char[] password) throws OpenAS2Exception;

    public void load(InputStream in, char[] password) throws OpenAS2Exception;

    public void save() throws OpenAS2Exception;

    public void save(String filename, char[] password) throws OpenAS2Exception;

    public void save(OutputStream out, char[] password) throws OpenAS2Exception;
}