package org.openas2.cert;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.WrappedException;
import org.openas2.message.Message;
import org.openas2.message.MessageMDN;
import org.openas2.params.InvalidParameterException;
import org.openas2.partner.Partnership;
import org.openas2.partner.SecurePartnership;
import org.openas2.util.AS2Util;
import org.openas2.util.FileMonitor;
import org.openas2.util.FileMonitorListener;

public class PKCS12CertificateFactory extends BaseCertificateFactory implements
        AliasedCertificateFactory, KeyStoreCertificateFactory, StorableCertificateFactory,
        FileMonitorListener {
    public static final String PARAM_FILENAME = "filename";
    public static final String PARAM_PASSWORD = "password";
    public static final String PARAM_INTERVAL = "interval";
    private FileMonitor fileMonitor;
    private KeyStore keyStore;

	private Log logger = LogFactory.getLog(PKCS12CertificateFactory.class.getSimpleName());
    
    public String getAlias(Partnership partnership, String partnershipType) throws OpenAS2Exception {
        String alias = null;

        if (partnershipType == Partnership.PTYPE_RECEIVER) {
            alias = partnership.getReceiverID(SecurePartnership.PID_X509_ALIAS);
        } else if (partnershipType == Partnership.PTYPE_SENDER) {
            alias = partnership.getSenderID(SecurePartnership.PID_X509_ALIAS);
        }

        if (alias == null) {
            throw new CertificateNotFoundException(partnershipType, null);
        }

        return alias;
    }

    public X509Certificate getCertificate(String alias) throws OpenAS2Exception {
        try {
            KeyStore ks = getKeyStore();
            X509Certificate cert = (X509Certificate) ks.getCertificate(alias);

            if (cert == null) {
                throw new CertificateNotFoundException(null, alias);
            }

            return cert;
        } catch (KeyStoreException kse) {
            throw new WrappedException(kse);
        }
    }

    public X509Certificate getCertificate(Message msg, String partnershipType)
            throws OpenAS2Exception {
        try {
            return getCertificate(getAlias(msg.getPartnership(), partnershipType));
        } catch (CertificateNotFoundException cnfe) {
            cnfe.setPartnershipType(partnershipType);
            throw cnfe;
        }
    }

    public X509Certificate getCertificate(MessageMDN mdn, String partnershipType)
            throws OpenAS2Exception {
        try {
            return getCertificate(getAlias(mdn.getPartnership(), partnershipType));
        } catch (CertificateNotFoundException cnfe) {
            cnfe.setPartnershipType(partnershipType);
            throw cnfe;
        }
    }

    public Map<String,X509Certificate> getCertificates() throws OpenAS2Exception {
        KeyStore ks = getKeyStore();

        try {
            Map<String,X509Certificate> certs = new HashMap<String,X509Certificate>();
            String certAlias;

            Enumeration<String> e = ks.aliases();

            while (e.hasMoreElements()) {
                certAlias = (String) e.nextElement();
                certs.put(certAlias, (X509Certificate) ks.getCertificate(certAlias));
            }

            return certs;
        } catch (GeneralSecurityException gse) {
            throw new WrappedException(gse);
        }
    }

    public void setFileMonitor(FileMonitor fileMonitor) {
        this.fileMonitor = fileMonitor;
    }

    public FileMonitor getFileMonitor() throws InvalidParameterException {
        boolean createMonitor = ((fileMonitor == null) && (getParameter(PARAM_INTERVAL, false) != null));

        if (!createMonitor && fileMonitor != null) {
            String filename = fileMonitor.getFilename();
            createMonitor = ((filename != null) && !filename.equals(getFilename()));
        }

        if (createMonitor) {
            if (fileMonitor != null) {
                fileMonitor.stop();
            }

            int interval = getParameterInt(PARAM_INTERVAL, true);
            File file = new File(getFilename());
            fileMonitor = new FileMonitor(file, interval);
            fileMonitor.addListener(this);
        }

        return fileMonitor;
    }

    public void setFilename(String filename) {
        getParameters().put(PARAM_FILENAME, filename);
    }

    public String getFilename() throws InvalidParameterException {
        return getParameter(PARAM_FILENAME, true);
    }

    public void setKeyStore(KeyStore keyStore) {
        this.keyStore = keyStore;
    }

    public KeyStore getKeyStore() {
        return keyStore;
    }

    public void setPassword(char[] password) {
        getParameters().put(PARAM_PASSWORD, new String(password));
    }

    public char[] getPassword() throws InvalidParameterException {
        return getParameter(PARAM_PASSWORD, true).toCharArray();
    }

    public PrivateKey getPrivateKey(X509Certificate cert) throws OpenAS2Exception {
        KeyStore ks = getKeyStore();
        String alias = null;

        try {
            alias = ks.getCertificateAlias(cert);

            if (alias == null) {
                throw new KeyNotFoundException(cert, null);
            }

            PrivateKey key = (PrivateKey) ks.getKey(alias, getPassword());

            if (key == null) {
                throw new KeyNotFoundException(cert, null);
            }

            return key;
        } catch (GeneralSecurityException e) {
            throw new KeyNotFoundException(cert, alias, e);
        }
    }

    public PrivateKey getPrivateKey(Message msg, X509Certificate cert) throws OpenAS2Exception {
        return getPrivateKey(cert);
    }

    public PrivateKey getPrivateKey(MessageMDN mdn, X509Certificate cert) throws OpenAS2Exception {
        return getPrivateKey(cert);
    }

    public void addCertificate(String alias, X509Certificate cert, boolean overwrite)
            throws OpenAS2Exception {
        KeyStore ks = getKeyStore();

        try {
            if (ks.containsAlias(alias) && !overwrite) {
                throw new CertificateExistsException(alias);
            }

            ks.setCertificateEntry(alias, cert);
            save(getFilename(), getPassword());
        } catch (GeneralSecurityException gse) {
            throw new WrappedException(gse);
        }
    }

    public void addPrivateKey(String alias, Key key, String password) throws OpenAS2Exception {
        KeyStore ks = getKeyStore();

        try {
            if (!ks.containsAlias(alias)) {
                throw new CertificateNotFoundException(null, alias);
            }

            Certificate[] certChain = ks.getCertificateChain(alias);
            ks.setKeyEntry(alias, key, password.toCharArray(), certChain);

            save(getFilename(), getPassword());
        } catch (GeneralSecurityException gse) {
            throw new WrappedException(gse);
        }
    }

    public void clearCertificates() throws OpenAS2Exception {
        KeyStore ks = getKeyStore();

        try {
            Enumeration<String> aliases = ks.aliases();

            while (aliases.hasMoreElements()) {
                ks.deleteEntry((String) aliases.nextElement());
            }

            save(getFilename(), getPassword());
        } catch (GeneralSecurityException gse) {
            throw new WrappedException(gse);
        }
    }

    public void handle(FileMonitor monitor, File file, int eventID) {
        switch (eventID) {
        case FileMonitorListener.EVENT_MODIFIED:

            try {
                load();
                logger.info("- Certificates Reloaded -");
            } catch (OpenAS2Exception oae) {
                oae.terminate();
            }

            break;
        }
    }

    public void init(Session session, Map<String, String> options) throws OpenAS2Exception {
        super.init(session, options);

        try {
            this.keyStore = AS2Util.getCryptoHelper().getKeyStore();
        } catch (Exception e) {
            throw new WrappedException(e);
        }

        load(getFilename(), getPassword());
    }

    public void load(String filename, char[] password) throws OpenAS2Exception {
        try {
            FileInputStream fIn = new FileInputStream(filename);

            load(fIn, password);

            fIn.close();
        } catch (IOException ioe) {
            throw new WrappedException(ioe);
        }
    }

    public void load(InputStream in, char[] password) throws OpenAS2Exception {
        try {
            KeyStore ks = getKeyStore();

            synchronized (ks) {
                ks.load(in, password);
            }

            getFileMonitor();
        } catch (IOException ioe) {
            throw new WrappedException(ioe);
        } catch (GeneralSecurityException gse) {
            throw new WrappedException(gse);
        }
    }

    public void load() throws OpenAS2Exception {
        load(getFilename(), getPassword());
    }

    public void removeCertificate(X509Certificate cert) throws OpenAS2Exception {
        KeyStore ks = getKeyStore();

        try {
            String alias = ks.getCertificateAlias(cert);

            if (alias == null) {
                throw new CertificateNotFoundException(cert);
            }

            removeCertificate(alias);
        } catch (GeneralSecurityException gse) {
            throw new WrappedException(gse);
        }
    }

    public void removeCertificate(String alias) throws OpenAS2Exception {
        KeyStore ks = getKeyStore();

        try {
            if (ks.getCertificate(alias) == null) {
                throw new CertificateNotFoundException(null, alias);
            }

            ks.deleteEntry(alias);
            save(getFilename(), getPassword());
        } catch (GeneralSecurityException gse) {
            throw new WrappedException(gse);
        }
    }

    public void save() throws OpenAS2Exception {
        save(getFilename(), getPassword());
    }

    public void save(String filename, char[] password) throws OpenAS2Exception {
        try {
            FileOutputStream fOut = new FileOutputStream(filename, false);

            save(fOut, password);

            fOut.close();
        } catch (IOException ioe) {
            throw new WrappedException(ioe);
        }
    }

    public void save(OutputStream out, char[] password) throws OpenAS2Exception {
        try {
            getKeyStore().store(out, password);
        } catch (IOException ioe) {
            throw new WrappedException(ioe);
        } catch (GeneralSecurityException gse) {
            throw new WrappedException(gse);
        }
    }
}