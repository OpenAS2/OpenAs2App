package org.openas2.lib.helper;

import java.security.Key;
import java.security.cert.Certificate;

import javax.mail.internet.MimeBodyPart;

import org.openas2.Session;
import org.openas2.lib.CryptoException;
import org.openas2.lib.message.EDIINTMessage;
import org.openas2.message.Message;


public class EDIINTHelper {
    private ICryptoHelper cryptoHelper;

    public EDIINTHelper(ICryptoHelper crypto) {
        super();
        setCryptoHelper(crypto);
    }

    public void encrypt(EDIINTMessage msg, Certificate cert, String algorithm)
        throws CryptoException {
        try {
            // get the data that should be encrypted    	
            MimeBodyPart data = msg.getData();

            // encrypt the data using CryptoHelper 
            MimeBodyPart encryptedData = getCryptoHelper().encrypt(data, cert,
                    algorithm, ((Message)msg).getPartnership().isNoSetTransferEncodingForEncryption());

            // update the message's data and content type
            msg.setData(encryptedData);
            msg.setContentType(encryptedData.getContentType());
            msg.setHeader("Content-Transfer-Encoding", encryptedData.getEncoding());
        } catch (Exception e) {
            throw new CryptoException("Encryption failed", e);
        }
    }

    public void decrypt(EDIINTMessage msg, Certificate cert, Key key)
        throws CryptoException {
        try {
            ICryptoHelper crypto = getCryptoHelper();

            // get the data to decrypt
            MimeBodyPart data = msg.getData();

            // make sure the data is encrypted
            if (!crypto.isEncrypted(data)) {
                throw new CryptoException("Data is not encrypted");
            }

            // decrypt the data
            MimeBodyPart decryptedData = crypto.decrypt(data, cert, key);

            // update the message's data and content type
            msg.setData(decryptedData);
            msg.setContentType(decryptedData.getContentType());
            msg.setHeader("Content-Transfer-Encoding", decryptedData.getEncoding());
        } catch (Exception e) {
            throw new CryptoException("Decryption failed", e);
        }
    }

    public void sign(EDIINTMessage msg, Certificate cert, Key key, String digest)
        throws CryptoException {
        try {
            // get the data to sign
            MimeBodyPart data = msg.getData();

            // sign the data using CryptoHelper
            MimeBodyPart signedData = getCryptoHelper().sign(data, cert, key,
                    digest, ((Message)msg).getPartnership().isNoSetTransferEncodingForSigning(), false);

            // update the message's data and content type
            msg.setData(signedData);
            msg.setContentType(signedData.getContentType());
            String contentTxfrEnc = signedData.getEncoding();
            if (contentTxfrEnc == null) contentTxfrEnc = Session.DEFAULT_CONTENT_TRANSFER_ENCODING;
            msg.setHeader("Content-Transfer-Encoding", contentTxfrEnc);
        } catch (Exception e) {
            throw new CryptoException("Sign failed", e);
        }
    }

    public void verify(EDIINTMessage msg, Certificate cert)
        throws CryptoException {
        try {
            ICryptoHelper crypto = getCryptoHelper();

            // get the data to verify
            MimeBodyPart data = msg.getData();

            // make sure the data is signed
            if (!crypto.isSigned(data)) {
                throw new CryptoException("Data is not signed");
            }

            // verify the data
            MimeBodyPart verifiedData = crypto.verifySignature(data, cert);

            // update the message's data and content type
            msg.setData(verifiedData);
            msg.setContentType(verifiedData.getContentType());
        } catch (Exception e) {
            throw new CryptoException("Verify failed", e);
        }
    }

    public boolean isEncrypted(EDIINTMessage msg) throws CryptoException {
        try {
            return getCryptoHelper().isEncrypted(msg.getData());
        } catch (Exception e) {
            throw new CryptoException("Error detecting encryption", e);
        }
    }

    public boolean isSigned(EDIINTMessage msg) throws CryptoException {
        try {
            return getCryptoHelper().isSigned(msg.getData());
        } catch (Exception e) {
            throw new CryptoException("Error detecting signature", e);
        }
    }

    public ICryptoHelper getCryptoHelper() {
        return cryptoHelper;
    }

    public void setCryptoHelper(ICryptoHelper cryptoHelper) {
        this.cryptoHelper = cryptoHelper;
    }
}
