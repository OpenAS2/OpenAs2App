package org.openas2.util;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Trusts a certificate chain that any one of several trust stores trusts.
 * <p>
 * An SSLContext only uses the first X509TrustManager it is given, and a TrustManagerFactory is
 * initialised from a single KeyStore, so trusting certificates from more than one store needs a trust
 * manager that consults each in turn. This lets a trust keystore of our own add to the certificates the
 * JVM already trusts instead of replacing them.
 */
public class MultiKeyStoreTrustManager implements X509TrustManager {

    private final List<X509TrustManager> trustManagers;

    /**
     * @param trustManagers - consulted in this order, so put the most specific store first
     */
    public MultiKeyStoreTrustManager(List<X509TrustManager> trustManagers) {
        if (trustManagers.isEmpty()) {
            throw new IllegalArgumentException("At least one trust manager is required");
        }
        this.trustManagers = Collections.unmodifiableList(new ArrayList<X509TrustManager>(trustManagers));
    }

    /**
     * Builds a trust manager over the given key stores.
     *
     * @param keyStores - the stores to trust, consulted in this order; a null entry stands for the JVM's
     *                    own trust store, as it does when initialising a TrustManagerFactory
     * @return a trust manager that trusts what any of the stores trusts
     * @throws GeneralSecurityException if a store cannot be used to initialise a trust manager
     */
    public static MultiKeyStoreTrustManager forKeyStores(KeyStore... keyStores) throws GeneralSecurityException {
        List<X509TrustManager> managers = new ArrayList<X509TrustManager>();
        for (KeyStore keyStore : keyStores) {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keyStore);
            managers.add(x509TrustManager(tmf));
        }
        return new MultiKeyStoreTrustManager(managers);
    }

    private static X509TrustManager x509TrustManager(TrustManagerFactory tmf) throws GeneralSecurityException {
        for (TrustManager tm : tmf.getTrustManagers()) {
            if (tm instanceof X509TrustManager) {
                return (X509TrustManager) tm;
            }
        }
        throw new GeneralSecurityException("No X509TrustManager available from " + tmf.getAlgorithm());
    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        CertificateException rejection = null;
        for (X509TrustManager tm : trustManagers) {
            try {
                tm.checkServerTrusted(chain, authType);
                return;
            } catch (CertificateException e) {
                rejection = combine(rejection, e);
            }
        }
        throw rejection;
    }

    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        CertificateException rejection = null;
        for (X509TrustManager tm : trustManagers) {
            try {
                tm.checkClientTrusted(chain, authType);
                return;
            } catch (CertificateException e) {
                rejection = combine(rejection, e);
            }
        }
        throw rejection;
    }

    /*
     * Rejected by every store: report the first store's reason, since that is the most specific one,
     * with the others' attached so none of them is lost.
     */
    private static CertificateException combine(CertificateException first, CertificateException next) {
        if (first == null) {
            return next;
        }
        first.addSuppressed(next);
        return first;
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        List<X509Certificate> issuers = new ArrayList<X509Certificate>();
        for (X509TrustManager tm : trustManagers) {
            issuers.addAll(Arrays.asList(tm.getAcceptedIssuers()));
        }
        return issuers.toArray(new X509Certificate[0]);
    }
}
