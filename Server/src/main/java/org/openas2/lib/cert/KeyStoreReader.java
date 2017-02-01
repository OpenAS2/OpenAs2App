package org.openas2.lib.cert;

import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;

import org.openas2.lib.helper.ICryptoHelper;

public class KeyStoreReader {

    public static void read(KeyStore keyStore, InputStream in, char[] password, ICryptoHelper cryptoHelper)
            throws Exception {
        java.security.KeyStore ks = cryptoHelper.loadKeyStore(in, password);
        keyStore.setKeyStore(ks);
    }

    public static KeyStore read(InputStream in, char[] password, ICryptoHelper cryptoHelper) throws Exception {
        KeyStore keyStore = new KeyStore(null);
        read(keyStore, in, password, cryptoHelper);
        return keyStore;
    }

    public static void read(KeyStore keyStore, URL url, char[] password, ICryptoHelper cryptoHelper) throws Exception {
        InputStream in = url.openStream();
        try {
            read(keyStore, in, password, cryptoHelper);
        } finally {
            in.close();
        }
    }

    public static KeyStore read(URL url, char[] password, ICryptoHelper cryptoHelper) throws Exception {
        KeyStore keyStore = new KeyStore(null);
        read(keyStore, url, password, cryptoHelper);
        return keyStore;
    }

    public static void read(KeyStore keyStore, String filename, char[] password, ICryptoHelper cryptoHelper)
            throws Exception {
        FileInputStream in = new FileInputStream(filename);
        try {
            read(keyStore, in, password, cryptoHelper);
        } finally {
            in.close();
        }
    }

    public static KeyStore read(String filename, char[] password, ICryptoHelper cryptoHelper) throws Exception {
        KeyStore keyStore = new KeyStore(null);
        read(keyStore, filename, password, cryptoHelper);
        return keyStore;
    }

}