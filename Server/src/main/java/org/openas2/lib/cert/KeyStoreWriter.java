package org.openas2.lib.cert;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.GeneralSecurityException;


public class KeyStoreWriter {

    public static void write(KeyStore ks, String filename, char[] password) throws GeneralSecurityException, IOException {
        FileOutputStream fOut = new FileOutputStream(filename, false);
        try {
            write(ks, fOut, password);            
        } finally {
            fOut.close();
        }
    }
    
    public static void write(KeyStore ks, OutputStream out, char[] password) throws GeneralSecurityException, IOException  {
        ks.getKeyStore().store(out, password);
    }

}
