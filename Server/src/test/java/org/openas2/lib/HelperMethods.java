package org.openas2.lib;

import org.bouncycastle.mail.smime.SMIMEEnvelopedGenerator;
import org.bouncycastle.mail.smime.SMIMESignedGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openas2.lib.helper.BCCryptoHelper;
import org.openas2.lib.helper.ICryptoHelper;

import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

@ExtendWith(MockitoExtension.class)

public class HelperMethods {

    @Test
    public void convertAlgorithmString() throws NoSuchAlgorithmException {
        Map<String, String> algorithmChecks = new HashMap<String, String>();
        algorithmChecks.put(ICryptoHelper.AES128_CBC, SMIMEEnvelopedGenerator.AES128_CBC);
        algorithmChecks.put(ICryptoHelper.DIGEST_SHA1.replaceAll("-", ""), SMIMESignedGenerator.DIGEST_SHA1);
        algorithmChecks.put(ICryptoHelper.DIGEST_SHA256.replaceAll("-", ""), SMIMESignedGenerator.DIGEST_SHA256);
        algorithmChecks.put(ICryptoHelper.DIGEST_SHA256.replaceAll("-", "2_"), SMIMESignedGenerator.DIGEST_SHA256);
        algorithmChecks.put(ICryptoHelper.DIGEST_SHA256.replaceAll("-", "2-"), SMIMESignedGenerator.DIGEST_SHA256);
        algorithmChecks.put(ICryptoHelper.DIGEST_SHA384.replaceAll("-", "2-"), SMIMESignedGenerator.DIGEST_SHA384);

        BCCryptoHelper bch = new BCCryptoHelper();
        for (Map.Entry<String, String> entry : algorithmChecks.entrySet()) {
            String convertedAlgo = bch.convertAlgorithm(entry.getKey(), true);
            assertThat("Algorithm matches expected", convertedAlgo, equalTo(entry.getValue()));
        }
    }
}
