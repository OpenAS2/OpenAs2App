package org.openas2.cert;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openas2.OpenAS2Exception;
import org.openas2.util.AS2Util;

/**
 * Verifies that loading or saving the keystore does not leak a file descriptor when it fails.
 * <p>
 * Both methods opened the stream, called through to the overload that does the work and closed the
 * stream afterwards. That overload throws OpenAS2Exception, which the catch around it did not cover,
 * so the close was skipped whenever the work failed. The keystore is reloaded on a schedule, five
 * minutes apart by default, so a keystore that consistently failed to load leaked a descriptor every
 * refresh until the process ran out of them.
 * <p>
 * Descriptors are counted through /proc, so these tests only run where that is available.
 */
public class KeyStoreStreamLeakTest {

    private static final String WRONG_PASSWORD = "not-the-password";
    private static final int ATTEMPTS = 40;

    private File keystore;

    @BeforeEach
    public void setUp() throws Exception {
        assumeTrue(new File("/proc/self/fd").isDirectory(), "descriptor counting needs /proc");
        File dir = Files.createTempDirectory("keystore-leak").toFile();
        keystore = new File(dir, "as2_certs.p12");
        Files.copy(Paths.get("src", "config", "as2_certs.p12"), keystore.toPath());
    }

    @Test
    public void aFailedLoadDoesNotLeakADescriptor() throws Exception {
        X509CertificateFactory factory = factory();

        long before = openDescriptors();
        for (int i = 0; i < ATTEMPTS; i++) {
            assertThrows(OpenAS2Exception.class,
                    () -> factory.load(keystore.getAbsolutePath(), WRONG_PASSWORD.toCharArray()),
                    "the wrong password should fail the load");
        }
        long after = openDescriptors();

        assertTrue(after - before < ATTEMPTS / 2,
                ATTEMPTS + " failed loads should not each hold a descriptor open, went from "
                        + before + " to " + after);
    }

    @Test
    public void aSuccessfulLoadDoesNotLeakADescriptorEither() throws Exception {
        X509CertificateFactory factory = factory();

        long before = openDescriptors();
        for (int i = 0; i < ATTEMPTS; i++) {
            factory.load(keystore.getAbsolutePath(), "testas2".toCharArray());
        }
        long after = openDescriptors();

        assertTrue(after - before < ATTEMPTS / 2,
                "repeated successful loads should not accumulate descriptors, went from "
                        + before + " to " + after);
    }

    private X509CertificateFactory factory() throws Exception {
        X509CertificateFactory factory = new X509CertificateFactory();
        factory.setKeyStore(AS2Util.getCryptoHelper().getKeyStore());
        return factory;
    }

    /** Counts the descriptors this process currently holds open. */
    private long openDescriptors() throws Exception {
        try (java.util.stream.Stream<java.nio.file.Path> fds = Files.list(Paths.get("/proc/self/fd"))) {
            return fds.count();
        }
    }
}
