package org.openas2.lib;

import java.security.Key;
import java.security.cert.Certificate;

import org.openas2.lib.cert.ICertificateChooser;
import org.openas2.lib.helper.EDIINTHelper;
import org.openas2.lib.message.EDIINTMessage;
import org.openas2.lib.message.EDIINTMessageMDN;
import org.openas2.lib.partner.IPartnership;
import org.openas2.lib.partner.IPartnershipChooser;
import org.openas2.lib.partner.PartnerException;

public class EDIINTEngine {
    private EDIINTHelper ediintHelper;
    private ICertificateChooser certificateChooser;
    private IPartnershipChooser partnershipChooser;
    private MDNEngine mdnEngine;

    public EDIINTEngine(EDIINTHelper ediintHelper, ICertificateChooser certificateChooser,
            IPartnershipChooser partnershipChooser) {
        super();
        this.ediintHelper = ediintHelper;
        this.certificateChooser = certificateChooser;
        this.partnershipChooser = partnershipChooser;
        mdnEngine = new MDNEngine(ediintHelper, certificateChooser, partnershipChooser);
    }

    public EDIINTEngine(EDIINTHelper ediintHelper, ICertificateChooser certificateChooser,
            IPartnershipChooser partnershipChooser, MDNEngine mdnEngine) {
        super();
        this.ediintHelper = ediintHelper;
        this.certificateChooser = certificateChooser;
        this.partnershipChooser = partnershipChooser;
        this.mdnEngine = mdnEngine;
    }

    public EngineResults encode(EDIINTMessage msg) {
        // the current state and results are stored in this object
        EngineResults results = new EngineResults();

        // get the needed stores and helpers
        EDIINTHelper ediintHelper = getEDIINTHelper();
        ICertificateChooser certChooser = getCertificateChooser();

        // find a matching partnership for the message
        IPartnership partnership = null;
        try {
            partnership = getPartnershipChooser().getPartnership(msg);
            results.setPartnership(partnership);
        } catch (PartnerException pe) {
            results.setException(pe);
            return results;
        }

        try {
            // if a signature digest algorithm is specified, sign the message
            String signAlg = partnership.getSignatureAlgorithm();

            if (signAlg != null) {
                // get the certificate and private key for signing
                Certificate senderCert = certChooser.getSenderCertificate(msg);
                Key senderKey = certChooser.getSenderKey(msg);

                if (senderCert == null) {
                    throw new CryptoException("Sign failed: Certificate not found");
                }

                if (senderKey == null) {
                    throw new CryptoException("Sign failed: Private key not found");
                }

                // sign the message
                ediintHelper.sign(msg, senderCert, senderKey, signAlg);

                // if the signature was successful, update the results
                results.setSignature(EngineResults.STATUS_OK);
            }
        } catch (OpenAS2Exception oae) {
            results.setSignature(EngineResults.STATUS_ERROR);
            results.setException(oae);

            return results;
        }

        try {
            // if an encryption algorithm is specified, encrypt the message
            String encryptionAlg = partnership.getEncryptionAlgorithm();

            if (encryptionAlg != null) {
                // get the certificate for encryption
                Certificate receiverCert = certChooser.getReceiverCertificate(msg);

                if (receiverCert == null) {
                    throw new CryptoException("Encryption failed: Certificate not found");
                }

                // encrypt the message
                ediintHelper.encrypt(msg, receiverCert, encryptionAlg);

                // if the encryption was successful, update the results
                results.setEncryption(EngineResults.STATUS_OK);
            }
        } catch (OpenAS2Exception oae) {
            results.setEncryption(EngineResults.STATUS_ERROR);
            results.setException(oae);

            return results;
        }

        // return the encode results
        return results;
    }

    public EngineResults decode(EDIINTMessage msg) {
        // the current state and results are stored in this object
        EngineResults results = new EngineResults();

        // get the needed stores and helpers
        EDIINTHelper ediintHelper = getEDIINTHelper();
        ICertificateChooser certChooser = getCertificateChooser();

        // find a matching partnership for the message
        IPartnership partnership = null;
        try {
            partnership = getPartnershipChooser().getPartnership(msg);
            results.setPartnership(partnership);
        } catch (PartnerException pe) {
            results.setException(pe);
            return results;
        }

        try {
            // if the message is encrypted, decrypt it
            if (ediintHelper.isEncrypted(msg)) {
                // get the certificate and key for decryption
                Certificate receiverCert = certChooser.getReceiverCertificate(msg);
                Key receiverKey = certChooser.getReceiverKey(msg);

                if (receiverCert == null) {
                    throw new CryptoException("Decryption failed: Certificate not found");
                }

                if (receiverKey == null) {
                    throw new CryptoException("Decryption failed: Private key not found");
                }

                // decrypt the message
                ediintHelper.decrypt(msg, receiverCert, receiverKey);

                // if the decryption was successful, update the results
                results.setEncryption(EngineResults.STATUS_OK);
            }
        } catch (OpenAS2Exception oae) {
            results.setEncryption(EngineResults.STATUS_ERROR);
            results.setException(oae);

            return results;
        }

        try {
            // if the message is signed, verify the signature and extract the
            // data
            if (ediintHelper.isSigned(msg)) {
                // get the certificate to test verification
                Certificate senderCert = certChooser.getSenderCertificate(msg);

                if (senderCert == null) {
                    throw new CryptoException("Signature verification failed: Certificate not found");
                }

                // verify the message signature
                ediintHelper.verify(msg, senderCert);

                // if the verification was successful, update the results
                results.setSignature(EngineResults.STATUS_OK);
            }
        } catch (OpenAS2Exception oae) {
            results.setSignature(EngineResults.STATUS_ERROR);
            results.setException(oae);

            return results;
        }

        return results;
    }

    public EDIINTMessageMDN generateMDN(EDIINTMessage msg, EngineResults results) throws MDNException {
        // get the MDN engine instance to generate the message
        MDNEngine mdnEngine = getMDNEngine();
        if (mdnEngine == null) {
            throw new MDNException("MDN Engine not set");
        }

        return mdnEngine.generateMDN(msg, results);
    }

    public EDIINTHelper getEDIINTHelper() {
        return ediintHelper;
    }

    public void setEDIINTHelper(EDIINTHelper ediintHelper) {
        this.ediintHelper = ediintHelper;
    }

    public ICertificateChooser getCertificateChooser() {
        return certificateChooser;
    }

    public void setCertificateChooser(ICertificateChooser certificateChooser) {
        this.certificateChooser = certificateChooser;
    }

    public IPartnershipChooser getPartnershipChooser() {
        return partnershipChooser;
    }

    public void setPartnershipChooser(IPartnershipChooser partnershipChooser) {
        this.partnershipChooser = partnershipChooser;
    }

    public MDNEngine getMDNEngine() {
        return mdnEngine;
    }

    public void setMDNEngine(MDNEngine mdnEngine) {
        this.mdnEngine = mdnEngine;
    }

}