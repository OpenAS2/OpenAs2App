package org.openas2.lib.cert;

import java.security.Key;
import java.security.cert.Certificate;

import org.openas2.lib.message.AS2Message;
import org.openas2.lib.message.AS2MessageMDN;
import org.openas2.lib.message.EDIINTMessage;

public class BasicCertificateChooser implements ICertificateChooser {
    private ICertificateStore certificateStore;

    public BasicCertificateChooser(ICertificateStore certificateStore) {
        super();
        this.certificateStore = certificateStore;
    }

    public Certificate getReceiverCertificate(EDIINTMessage msg) throws CertificateException {
        String alias = getReceiverAlias(msg);

        return getCertificateStore().getCertificate(alias);
    }

    public Key getReceiverKey(EDIINTMessage msg) throws CertificateException {
        String alias = getReceiverAlias(msg);
        return getCertificateStore().getKey(alias, getReceiverKeyPassword(msg, alias));
    }

    public Certificate getSenderCertificate(EDIINTMessage msg) throws CertificateException {
        String alias = getSenderAlias(msg);

        return getCertificateStore().getCertificate(alias);
    }

    public Key getSenderKey(EDIINTMessage msg) throws CertificateException {
        String alias = getSenderAlias(msg);

        return getCertificateStore().getKey(alias, getReceiverKeyPassword(msg, alias));
    }

    public String getReceiverAlias(EDIINTMessage msg) {
        if (msg instanceof AS2Message) {
            return ((AS2Message) msg).getAS2To();
        } else if (msg instanceof AS2MessageMDN) {
            return ((AS2MessageMDN) msg).getAS2To();
        }

        return null;
    }

    public String getSenderAlias(EDIINTMessage msg) {
        if (msg instanceof AS2Message) {
            return ((AS2Message) msg).getAS2From();
        } else if (msg instanceof AS2MessageMDN) {
            return ((AS2MessageMDN) msg).getAS2From();
        }

        return null;
    }
    
    public char[] getReceiverKeyPassword(EDIINTMessage msg, String alias) {
        return alias.toCharArray();
    }
    
    public char[] getSenderKeyPassword(EDIINTMessage msg, String alias) {
        return alias.toCharArray();
    }
    
    public ICertificateStore getCertificateStore() {
        return certificateStore;
    }
}