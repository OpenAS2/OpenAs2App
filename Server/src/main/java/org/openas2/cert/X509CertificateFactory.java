package org.openas2.cert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.WrappedException;
import org.openas2.params.InvalidParameterException;
import org.openas2.util.AS2Util;
import org.apache.commons.io.FileUtils;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class X509CertificateFactory extends BaseCertificateFactory implements AliasedCertificateFactory, KeyStoreCertificateFactory, StorableCertificateFactory {
    public static final String PARAM_IDENTIFIER = "identifier";
    public static final String PARAM_FILENAME = "filename";
    public static final String PARAM_PASSWORD = "password";
    public static final String PARAM_INTERVAL = "interval";
    private KeyStore keyStore;

    private Logger logger = LoggerFactory.getLogger(X509CertificateFactory.class);

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

    public Map<String, X509Certificate> getCertificates() throws OpenAS2Exception {
        KeyStore ks = getKeyStore();

        try {
            Map<String, X509Certificate> certs = new HashMap<String, X509Certificate>();
            String certAlias;

            Enumeration<String> e = ks.aliases();

            while (e.hasMoreElements()) {
                certAlias = e.nextElement();
                certs.put(certAlias, (X509Certificate) ks.getCertificate(certAlias));
            }

            return certs;
        } catch (GeneralSecurityException gse) {
            throw new WrappedException(gse);
        }
    }

    public String getIdentifier() throws InvalidParameterException {
        String identifier = getParameter(PARAM_IDENTIFIER, false);
        if (identifier == null) {
            // For backwards compatibility, no identifier means the original as2_certs factory
            identifier = CertificateFactory.COMPID_AS2_CERTIFICATE_FACTORY;
        }
        return identifier;
    }

    public String getFilename() throws InvalidParameterException {
        return getParameter(PARAM_FILENAME, true);
    }

    public void setFilename(String filename) {
        getParameters().put(PARAM_FILENAME, filename);
    }

    public KeyStore getKeyStore() {
        return keyStore;
    }

    public void setKeyStore(KeyStore keyStore) {
        this.keyStore = keyStore;
    }

    public char[] getPassword() throws InvalidParameterException {
        return getParameter(PARAM_PASSWORD, true).toCharArray();
    }

    public void setPassword(char[] password) {
        getParameters().put(PARAM_PASSWORD, new String(password));
    }

    public PrivateKey getPrivateKey(String alias) throws OpenAS2Exception {
        if (alias == null) {
            throw new OpenAS2Exception("Keystore alias not set in call to method getPrivateKey(alias). Check that the x509_alias attribute is set correctly in the partnership.");
        }
        KeyStore ks = getKeyStore();
        try {
            PrivateKey key = (PrivateKey) ks.getKey(alias, getPassword());
            if (key == null) {
                throw new OpenAS2Exception("The private key was not found for alias: " + alias + " -- Check that the private key has been added to the keystore for the alias: " + alias);
            }
            return key;
        } catch (GeneralSecurityException e) {
            throw new OpenAS2Exception("Unexpected error occured fetching private key for alias: " + alias + " -- " + e.getMessage(), e);
        }
    }

    @SuppressWarnings("unused")
    private PrivateKey getPrivateKey(X509Certificate cert) throws OpenAS2Exception {
        KeyStore ks = getKeyStore();
        String alias = null;

        try {
            alias = ks.getCertificateAlias(cert);

            if (alias == null) {
                throw new KeyNotFoundException(cert, "-- certificate lookup for matching certificate failed. Make sure the correct matching certificate has been installed in the keystore.");
            }

            PrivateKey key = (PrivateKey) ks.getKey(alias, getPassword());

            if (key == null) {
                throw new KeyNotFoundException(cert, "-- private key not found in certificate. Check that the private key has been added to the keystore for the alias: " + alias);
            }

            return key;
        } catch (GeneralSecurityException e) {
            throw new KeyNotFoundException(cert, alias, e);
        }
    }

    public void addCertificate(String alias, X509Certificate cert, boolean overwrite) throws OpenAS2Exception {
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
            if (certChain == null) {
                X509Certificate x509cert = (X509Certificate) ks.getCertificate(alias);
                if (x509cert.getSubjectX500Principal().equals(x509cert.getIssuerX500Principal())) {
                    // Trust chain is to itself
                    certChain = new X509Certificate[]{x509cert, x509cert};
                    if (logger.isInfoEnabled()) {
                        logger.info("Detected self-signed certificate and allowed import. Alias: " + alias);
                    }
                }
            }
            ks.setKeyEntry(alias, key, password.toCharArray(), certChain);

            save(getFilename(), getPassword());
        } catch (GeneralSecurityException gse) {
            throw new WrappedException(gse);
        }
    }

    public boolean importCert(String alias, InputStream encodedCertStream) throws IOException, CertificateException, OpenAS2Exception {
        java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");
        while (encodedCertStream.available() > 0) {
            Certificate cert = cf.generateCertificate(encodedCertStream);
            if (cert instanceof X509Certificate) {
                addCertificate(alias, (X509Certificate) cert, true);
                return true;
            }
        }
        // Could not convert stream to a certificate
        return false;
    }

    public boolean importPrivateKey(String alias, KeyStore ks, String password) throws KeyStoreException, OpenAS2Exception, UnrecoverableKeyException, NoSuchAlgorithmException {
        Enumeration<String> aliases = ks.aliases();
        while (aliases.hasMoreElements()) {
            String certAlias = aliases.nextElement();
            Certificate cert = ks.getCertificate(certAlias);
            if (cert instanceof X509Certificate) {
                addCertificate(alias, (X509Certificate) cert, true);
                Key certKey = ks.getKey(certAlias, password.toCharArray());
                addPrivateKey(alias, certKey, password);
                return true;
            }
        }
        return false;
    }

    public void clearCertificates() throws OpenAS2Exception {
        KeyStore ks = getKeyStore();

        try {
            Enumeration<String> aliases = ks.aliases();

            while (aliases.hasMoreElements()) {
                ks.deleteEntry(aliases.nextElement());
            }

            save(getFilename(), getPassword());
        } catch (GeneralSecurityException gse) {
            throw new WrappedException(gse);
        }
    }

    /**
     * @param keyAlg - key algorithm eg RSA, DSA, EC
     * @param keySize - normally a binary multiple eg 2048
     * @return - the key pair
     * @throws OpenAS2Exception 
     * @throws NoSuchAlgorithmException
     */
    public KeyPair generateKeyPair(String keyAlg, int keySize) throws OpenAS2Exception {
        KeyPairGenerator keyPairGenerator;
        try {
            keyPairGenerator = KeyPairGenerator.getInstance(keyAlg);
        } catch (NoSuchAlgorithmException e) {
            throw new OpenAS2Exception("Failed to create key pair genertor.", e);
        }
        keyPairGenerator.initialize(keySize, new SecureRandom());
        return keyPairGenerator.generateKeyPair();
    }   
    
    /**
     * @param alias
     * @param distinguishedName - provide in this format:  "CN=test.openas2.org,O=OpenAS2 Foundation,L=London,C=UK"
     * @param hashAlg -  hashing algorithm for the certificate;; eg "SHA256"
     * @param keyAlg - key algorithm for the certificate. eg. RSA, EC
     * @param keySizec - typically at least 2048 for security
     * @param validDays - how many days from current time will the certificate be valid for
     * @throws OpenAS2Exception
     */
    public void genSelfSignedCertificate(
            String alias,
            String distinguishedName,
            String hashAlg,
            String keyAlg,
            int keySize,
            int validDays) throws OpenAS2Exception {

        KeyPair kp = generateKeyPair(keyAlg, keySize);
        ASN1Sequence seq = null;
        try (ASN1InputStream asn1InputStream = new ASN1InputStream(kp.getPublic().getEncoded())) {
            seq= (ASN1Sequence) asn1InputStream.readObject();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        //SubjectPublicKeyInfo subPubKeyInfo = SubjectPublicKeyInfo.getInstance(kp.getPublic().getEncoded());
        SubjectPublicKeyInfo subPubKeyInfo = SubjectPublicKeyInfo.getInstance(seq);
        String signatureAlg = hashAlg + "With" + keyAlg;
        if (keyAlg=="EC") {
            signatureAlg += "DSA";
        }
        X500Name x500Name = new X500Name(distinguishedName);
        long currentTime = System.currentTimeMillis();
        Date notBefore = new Date(currentTime);
        Date notAfter = new Date(currentTime + (1000L * 3600L * 24 * validDays));
        X509v3CertificateBuilder certBuilder = new X509v3CertificateBuilder(
                    x500Name,
                    new BigInteger(64, new Random()),
                    notBefore,
                    notAfter,
                    x500Name,
                    subPubKeyInfo
                );
        try {
            SubjectKeyIdentifier ski = new SubjectKeyIdentifier(subPubKeyInfo.getEncoded());
            certBuilder.addExtension(Extension.subjectKeyIdentifier, false, ski);
        } catch (IOException e) {
            throw new OpenAS2Exception("Failed to add SubjectKeyIdentifier extension when generating certificate.", e);
        }
        ContentSigner signer;
        try {
            signer = new JcaContentSignerBuilder(signatureAlg)
                        .setProvider(new BouncyCastleProvider())
                        .build(kp.getPrivate());
        } catch (OperatorCreationException e) {
            throw new OpenAS2Exception("Failed to create signer when generating certificate.", e);
        }
        X509CertificateHolder certificateHolder = certBuilder.build(signer);
        try {
            X509Certificate cert = new JcaX509CertificateConverter().getCertificate(certificateHolder);
            addCertificate(alias, cert, false);
            addPrivateKey(alias, kp.getPrivate(), new String(getPassword()));
        } catch (CertificateException e) {
            throw new OpenAS2Exception("Failed to generate new certificate.", e);
        }
    }

    public void init(Session session, Map<String, String> options) throws OpenAS2Exception {
        super.init(session, options);

        // Override the password if it was passed as a system property
        String pwd = System.getProperty("org.openas2.cert.Password");
        if (pwd != null) {
            setPassword(pwd.toCharArray());
        }
        try {
            this.keyStore = AS2Util.getCryptoHelper().getKeyStore();
        } catch (Exception e) {
            throw new WrappedException(e);
        }
        load();
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

    /**
     * Exports the public key to a PKCS12 keystore file.
     * 
     * @param filename - name of the Keystore file to export the certificate to
     * @param password - password for the keystore
     * @throws Exception
     */
    public void exportPublicKey(String filename, String srcAlias, String tgtAlias, char[] password) throws Exception {
        KeyStore ks = AS2Util.getCryptoHelper().getKeyStore();
        ks.load(null, null); // Inialises keystore
        // Get the certificate entry containing public key and insert to keystore
        ks.setCertificateEntry(tgtAlias, getCertificate(srcAlias));
        try (FileOutputStream fOut = new FileOutputStream(filename, false)) {
            ks.store(fOut, password);
        } catch (IOException ioe) {
            throw new WrappedException(ioe);
        }
    }

    /**
     * Exports public key in PEM or DER encoding to a file.
     * @param filename - name of the file to store the encoded certificate to
     * @param srcAlias - the alis of the public key in the factory object
     * @param outputFormat - supports "DER" or "PEM
     * @throws Exception
     */
    public void exportPublicKey(String filename, String srcAlias, String outputFormat) throws Exception {
        File certFile = new File(filename);
        if ("DER".equalsIgnoreCase(outputFormat)) {
            byte[] derEncodedCert = getCertificate(srcAlias).getEncoded();
            FileUtils.writeByteArrayToFile(certFile, derEncodedCert);
        } else if ("PEM".equalsIgnoreCase(outputFormat)) {
            try (FileWriter fw = new FileWriter(certFile); JcaPEMWriter pemWriter = new JcaPEMWriter(fw)) {
                //String pemStr = Base64.getEncoder().encodeToString(derEncodedCert);
                pemWriter.writeObject(getCertificate(srcAlias));
                pemWriter.flush();
            }
        } else {
            throw new OpenAS2Exception("Unsupported certificate encoding format: " + outputFormat);
        }
    }
}
