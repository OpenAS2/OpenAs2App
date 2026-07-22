package org.openas2.params;

import jakarta.mail.internet.InternetHeaders;
import jakarta.mail.internet.MimeBodyPart;
import org.junit.jupiter.api.Test;
import org.openas2.message.AS2Message;
import org.openas2.message.Message;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies the $msg.hash.<algorithm>[_<length>]$ filename parameter used for content-based dedup:
 * it hashes the message payload, supports truncation, and gives the same hash for identical content
 * and different hashes for different content.
 */
public class MessageParametersHashTest {

    private Message messageWithContent(byte[] content) throws Exception {
        InternetHeaders ih = new InternetHeaders();
        ih.setHeader("Content-Type", "application/octet-stream");
        AS2Message msg = new AS2Message();
        msg.setData(new MimeBodyPart(ih, content));
        return msg;
    }

    private String expectedHex(byte[] content, String jdkAlgorithm) throws Exception {
        byte[] hash = MessageDigest.getInstance(jdkAlgorithm).digest(content);
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }

    @Test
    public void fullSha256Hash() throws Exception {
        byte[] content = "the quick brown fox".getBytes(StandardCharsets.UTF_8);
        MessageParameters params = new MessageParameters(messageWithContent(content));

        assertEquals(expectedHex(content, "SHA-256"), params.getParameter("hash.sha256"));
    }

    @Test
    public void truncatedTo16Characters() throws Exception {
        byte[] content = "invoice payload 12345".getBytes(StandardCharsets.UTF_8);
        MessageParameters params = new MessageParameters(messageWithContent(content));

        String result = params.getParameter("hash.sha256_16");
        assertEquals(16, result.length(), "hash should be truncated to 16 characters");
        assertEquals(expectedHex(content, "SHA-256").substring(0, 16), result);
    }

    @Test
    public void identicalContentGivesSameHashAndDifferentContentDiffers() throws Exception {
        MessageParameters a = new MessageParameters(messageWithContent("same bytes".getBytes(StandardCharsets.UTF_8)));
        MessageParameters b = new MessageParameters(messageWithContent("same bytes".getBytes(StandardCharsets.UTF_8)));
        MessageParameters c = new MessageParameters(messageWithContent("other bytes".getBytes(StandardCharsets.UTF_8)));

        assertEquals(a.getParameter("hash.sha256_16"), b.getParameter("hash.sha256_16"),
                "identical content must produce the same hash (dedup)");
        assertNotEquals(a.getParameter("hash.sha256_16"), c.getParameter("hash.sha256_16"),
                "different content must produce a different hash");
    }

    @Test
    public void otherAlgorithmsAreSupported() throws Exception {
        byte[] content = "algo test".getBytes(StandardCharsets.UTF_8);
        MessageParameters params = new MessageParameters(messageWithContent(content));

        assertEquals(expectedHex(content, "MD5"), params.getParameter("hash.md5"));
        assertEquals(expectedHex(content, "SHA-512"), params.getParameter("hash.sha512"));
    }

    @Test
    public void unsupportedAlgorithmThrows() throws Exception {
        MessageParameters params = new MessageParameters(messageWithContent("x".getBytes(StandardCharsets.UTF_8)));

        assertThrows(InvalidParameterException.class, () -> params.getParameter("hash.crc32"));
    }
}
