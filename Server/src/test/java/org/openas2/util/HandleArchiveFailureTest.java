package org.openas2.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openas2.OpenAS2Exception;

/**
 * Verifies that failing to archive a file reports why, rather than failing again on the way out.
 * <p>
 * Archiving is what runs after a message has already failed, to move its file into the error
 * directory. When the error directory itself could not be created, which is what happens on a read
 * only or unavailable mount, the handler built its message out of a destination file it had not
 * managed to work out yet. The resulting NullPointerException replaced the real cause and, being
 * unchecked, travelled straight past the handlers that expect an OpenAS2Exception.
 */
public class HandleArchiveFailureTest {

    private File dir;
    private File payload;

    @BeforeEach
    public void setUp() throws Exception {
        dir = Files.createTempDirectory("handle-archive").toFile();
        payload = new File(dir, "invoice-001.edi");
        Files.write(payload.toPath(), "payload".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void anUnusableArchiveDirectoryIsReportedRatherThanCausingANullPointer() throws Exception {
        /*
         * A regular file as a parent path component: the archive directory cannot be created beneath
         * it, so creating it fails before a destination file has been worked out. This reproduces the
         * null case on any platform and as any user, including root.
         */
        File blocker = new File(dir, "blocked");
        Files.write(blocker.toPath(), "not a directory".getBytes(StandardCharsets.UTF_8));
        String unusableArchiveDir = blocker.getAbsolutePath() + File.separator + "errors";

        OpenAS2Exception thrown = assertThrows(OpenAS2Exception.class,
                () -> IOUtil.handleArchive(payload, unusableArchiveDir, false));

        assertNotNull(thrown.getMessage());
        assertTrue(thrown.getMessage().contains(payload.getName()),
                "the message should name the file that could not be archived: " + thrown.getMessage());
        assertTrue(thrown.getMessage().contains("errors"),
                "and the directory it could not be archived into: " + thrown.getMessage());
        assertNotNull(thrown.getCause(), "the underlying IO failure must still be attached");
    }

    @Test
    public void theRealCauseIsNotReplacedByAFailureInsideTheHandler() throws Exception {
        File blocker = new File(dir, "blocked2");
        Files.write(blocker.toPath(), "not a directory".getBytes(StandardCharsets.UTF_8));
        String unusableArchiveDir = blocker.getAbsolutePath() + File.separator + "errors";

        try {
            IOUtil.handleArchive(payload, unusableArchiveDir, false);
            throw new AssertionError("archiving into an unusable directory should fail");
        } catch (NullPointerException npe) {
            throw new AssertionError("the handler failed on its own error path instead of reporting", npe);
        } catch (OpenAS2Exception expected) {
            assertTrue(expected.getCause() instanceof java.io.IOException,
                    "the cause should be the IO failure, was " + expected.getCause());
        }
    }

    @Test
    public void archivingStillWorksWhenTheDirectoryCanBeCreated() throws Exception {
        File archive = new File(dir, "error-dir");
        assertFalse(archive.exists(), "the directory should be created by the archive call");

        IOUtil.handleArchive(payload, archive.getAbsolutePath(), false);

        assertTrue(new File(archive, payload.getName()).isFile(), "the file should have been moved into it");
        assertFalse(payload.exists(), "and moved rather than copied");
    }

    @Test
    public void aReadOnlyArchiveDirectoryIsReportedRatherThanCausingANullPointer() throws Exception {
        File archive = new File(dir, "readonly");
        assumeTrue(archive.mkdirs() && archive.setWritable(false), "needs a directory that can be made read only");
        assumeTrue(!archive.canWrite(), "running as a user that ignores the read only bit");

        OpenAS2Exception thrown = assertThrows(OpenAS2Exception.class,
                () -> IOUtil.handleArchive(payload, archive.getAbsolutePath() + File.separator + "sub", false));

        assertTrue(thrown.getMessage().contains(payload.getName()), thrown.getMessage());
        archive.setWritable(true);
    }
}
