package org.openas2.pgp;

import org.bouncycastle.bcpg.ArmoredOutputStream;
import org.bouncycastle.openpgp.PGPCompressedData;
import org.bouncycastle.openpgp.PGPCompressedDataGenerator;
import org.bouncycastle.openpgp.PGPEncryptedData;
import org.bouncycastle.openpgp.PGPEncryptedDataGenerator;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPLiteralDataGenerator;
import org.bouncycastle.openpgp.PGPPublicKey;
import org.bouncycastle.openpgp.operator.bc.BcPGPDataEncryptorBuilder;
import org.bouncycastle.openpgp.operator.bc.BcPublicKeyKeyEncryptionMethodGenerator;

import java.io.IOException;
import java.io.OutputStream;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.Date;

public class PGPEncryptionUtil {

    // pick some sensible encryption buffer size
    private static final int BUFFER_SIZE = 16364;

    // encrypt the payload data using AES-256,
    private int payloadEncryptAlg = PGPEncryptedData.AES_256; // default
    // encryption
    // algorithm on
    // payload

    // various streams we're taking care of
    private final ArmoredOutputStream armoredOutputStream = null;
    private OutputStream encryptedOut = null;
    private OutputStream compressedOut = null;
    private OutputStream literalOut;
    private boolean supportPGP2_6 = false;
    private boolean isCompressData = true;
    private boolean isArmor = true;

    // PGP uses a symmetric key to encrypt data and uses the public key to
    // encrypt the symmetric key used on the payload.

    public PGPEncryptionUtil(PGPPublicKey key, String payloadFilename, OutputStream out) throws PGPException, NoSuchProviderException, IOException {
        BcPGPDataEncryptorBuilder builder = new BcPGPDataEncryptorBuilder(payloadEncryptAlg);
        builder.setSecureRandom(new SecureRandom());
        // create an encrypted payload and set the public key on the data
        // generator
        PGPEncryptedDataGenerator encryptGen = new PGPEncryptedDataGenerator(builder, supportPGP2_6);

        encryptGen.addMethod(new BcPublicKeyKeyEncryptionMethodGenerator(key));

        // open an output stream connected to the encrypted data generator
        // and have the generator write its data out to the ascii-encoding
        // stream
        byte[] buffer = new byte[BUFFER_SIZE];
        // write data out using "ascii-armor" encoding if enabled - this is the normal PGP text output.
        encryptedOut = encryptGen.open(isArmor ? new ArmoredOutputStream(out) : out, buffer);

        // add a data compressor if compression is enabled else just write the encrypted stream to the literal
        PGPLiteralDataGenerator literalGen = new PGPLiteralDataGenerator();
        if (isCompressData) {
            // compress data. before encryption ... far better compression on unencrypted data.
            PGPCompressedDataGenerator compressor = new PGPCompressedDataGenerator(PGPCompressedData.ZIP);
            compressedOut = compressor.open(encryptedOut);
            literalOut = literalGen.open(compressedOut, PGPLiteralDataGenerator.UTF8, payloadFilename, new Date(), new byte[BUFFER_SIZE]);
        } else {
            literalOut = literalGen.open(encryptedOut, PGPLiteralDataGenerator.UTF8, payloadFilename, new Date(), new byte[BUFFER_SIZE]);
        }
    }

    /**
     * Get an output stream connected to the encrypted file payload.
     *
     * @return The output stream for the payload to be sent
     */
    public OutputStream getPayloadOutputStream() {
        return this.literalOut;
    }

    /**
     * Close the encrypted output writers.
     *
     * @throws IOException - stream handling had a problem
     */
    public void close() throws IOException {
        // close the literal output
        if (literalOut != null) {
            literalOut.close();
        }

        // close the compressor
        if (compressedOut != null) {
            compressedOut.close();
        }

        // close the encrypted output
        if (encryptedOut != null) {
            encryptedOut.close();
        }

        // close the armored output
        if (armoredOutputStream != null) {
            armoredOutputStream.close();
        }
    }

    public boolean isCompressData() {
        return isCompressData;
    }

    public void setCompressData(boolean isCompressData) {
        this.isCompressData = isCompressData;
    }

    public boolean isSupportPGP2_6() {
        return supportPGP2_6;
    }

    public void setSupportPGP2_6(boolean supportPGP2_6) {
        this.supportPGP2_6 = supportPGP2_6;
    }

    public int getPayloadEncryptAlg() {
        return payloadEncryptAlg;
    }

    public void setPayloadEncryptAlg(int payloadEncryptAlg) {
        this.payloadEncryptAlg = payloadEncryptAlg;
    }

    public boolean isArmor() {
        return isArmor;
    }

    public void setArmor(boolean isArmor) {
        this.isArmor = isArmor;
    }

}
