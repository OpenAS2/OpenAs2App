package org.openas2.lib.helper;

import org.bouncycastle.cms.CMSException;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationVerifier;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Utility class to check sign with out time signature check
 */
// Claudio Degioanni claudio.degioanni@bmeweb.it 07/12/2021
public class SignerInfoIgnoringExpiredCertificate extends SignerInformation{

    protected SignerInfoIgnoringExpiredCertificate(SignerInformation baseInfo) {
        super(baseInfo);
    }

    /**
     * Verify that the given verifier can successfully verify the signature on
     * this SignerInformation object.
     *
     * @param verifier a suitably configured SignerInformationVerifier.
     * @return true if the signer information is verified, false otherwise.
     * @throws org.bouncycastle.cms.CMSVerifierCertificateNotValidException if the provider has an associated certificate and the certificate is not valid at the time given as the SignerInfo's signing time.
     * @throws org.bouncycastle.cms.CMSException if the verifier is unable to create a ContentVerifiers or DigestCalculators.
     */
    public boolean verify(SignerInformationVerifier verifier) throws CMSException {
        Method m = null;
        try {
            m = getClass().getSuperclass().getDeclaredMethod("doVerify", SignerInformationVerifier.class);
        } catch (NoSuchMethodException | SecurityException e) {
            e.printStackTrace();
        }
        if (m != null) {
            m.setAccessible(true);
            try {
                return (boolean) m.invoke(this, verifier);
            } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        return false;
    }

}
