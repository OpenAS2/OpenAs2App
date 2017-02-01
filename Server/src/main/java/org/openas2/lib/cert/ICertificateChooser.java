package org.openas2.lib.cert;

import java.security.Key;
import java.security.cert.Certificate;

import org.openas2.lib.message.EDIINTMessage;


public interface ICertificateChooser {
    ICertificateStore getCertificateStore();
    
    Certificate getSenderCertificate(EDIINTMessage msg) throws CertificateException;
    
    Certificate getReceiverCertificate(EDIINTMessage msg) throws CertificateException;

    Key getSenderKey(EDIINTMessage msg) throws CertificateException;
    
    Key getReceiverKey(EDIINTMessage msg) throws CertificateException;
}
