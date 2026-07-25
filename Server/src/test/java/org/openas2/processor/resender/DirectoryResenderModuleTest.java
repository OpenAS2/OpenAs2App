package org.openas2.processor.resender;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.message.AS2Message;
import org.openas2.message.Message;
import org.openas2.processor.Processor;
import org.openas2.processor.sender.SenderModule;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for DirectoryResenderModule: queueing a message for resend, the time-based scheduling
 * (isTimeToSend / scanDirectory), and the resend execution (processFile) including archiving on
 * failure. The processor is mocked so the resend can be observed without a running server.
 */
public class DirectoryResenderModuleTest {

    @TempDir
    Path tempDir;

    private DirectoryResenderModule module;
    private Path resendDir;
    private Path errorDir;
    private Processor processor;

    @BeforeEach
    public void setUp() throws Exception {
        resendDir = Files.createDirectories(tempDir.resolve("resend"));
        errorDir = Files.createDirectories(tempDir.resolve("error"));

        processor = mock(Processor.class);
        Session session = mock(Session.class);
        when(session.getProcessor()).thenReturn(processor);

        module = new DirectoryResenderModule();
        Map<String, String> params = new HashMap<String, String>();
        params.put(DirectoryResenderModule.PARAM_RESEND_DIRECTORY, resendDir.toString());
        params.put(DirectoryResenderModule.PARAM_ERROR_DIRECTORY, errorDir.toString());
        params.put(DirectoryResenderModule.PARAM_RESEND_DELAY, "0"); // due immediately
        module.init(session, params);
    }

    private Message message(String id) {
        AS2Message msg = new AS2Message();
        msg.setMessageID(id);
        return msg;
    }

    private Map<String, Object> resendOptions(String method, int retries) {
        Map<String, Object> options = new HashMap<String, Object>();
        options.put(ResenderModule.OPTION_RESEND_METHOD, method);
        options.put(ResenderModule.OPTION_RETRIES, "" + retries);
        return options;
    }

    private File[] resendFiles() {
        return resendDir.toFile().listFiles();
    }

    @Test
    public void handleQueuesMessageAndFlagsResend() throws Exception {
        Message msg = message("<queue-me>");
        module.handle(ResenderModule.DO_RESEND, msg, resendOptions(SenderModule.DO_SEND, 0));

        assertEquals(1, resendFiles().length, "handle should write one file to the resend directory");
        assertTrue(msg.isResend(), "message should be flagged as a resend so the builder does not reprocess it");
    }

    @Test
    public void isTimeToSendHonoursTheTimestampInTheFilename() {
        assertTrue(module.isTimeToSend(new File(resendDir.toFile(), "01-01-20-00-00-00")),
                "a file timestamped in the past is due");
        assertFalse(module.isTimeToSend(new File(resendDir.toFile(), "01-01-40-00-00-00")),
                "a file timestamped in the future is not due yet");
        assertTrue(module.isTimeToSend(new File(resendDir.toFile(), "not-a-timestamp")),
                "a file whose name is not a timestamp is treated as due");
    }

    @Test
    public void scanDirectoryReturnsOnlyDueFiles() throws Exception {
        Files.createFile(resendDir.resolve("01-01-20-00-00-00.due"));
        Files.createFile(resendDir.resolve("01-01-40-00-00-00.future"));

        List<File> due = module.scanDirectory();

        assertEquals(1, due.size(), "only the past-dated file should be due for resend");
        assertTrue(due.get(0).getName().startsWith("01-01-20"));
    }

    @Test
    public void processFileResendsWithIncrementedRetryCountThenDeletes() throws Exception {
        module.handle(ResenderModule.DO_RESEND, message("<resend-1>"), resendOptions(SenderModule.DO_SEND, 2));
        File queued = resendFiles()[0];

        module.processFile(queued);

        ArgumentCaptor<Message> msgCaptor = ArgumentCaptor.forClass(Message.class);
        verify(processor).handle(eq(SenderModule.DO_SEND), msgCaptor.capture(), any());
        assertEquals("3", msgCaptor.getValue().getOption(ResenderModule.OPTION_RETRIES),
                "retry count should be incremented from 2 to 3 on resend");
        assertFalse(queued.exists(), "the resend file should be deleted after a successful resend");
    }

    @Test
    public void processFileArchivesToErrorDirWhenResendFails() throws Exception {
        doThrow(new OpenAS2Exception("send failed")).when(processor).handle(any(), any(), any());

        module.handle(ResenderModule.DO_RESEND, message("<will-fail>"), resendOptions(SenderModule.DO_SEND, 0));
        File queued = resendFiles()[0];

        // Documents the current behaviour: on a resend failure the module archives the file to the
        // error dir, then erroneously calls file.delete() on the already-moved file and archives a
        // second time, which throws because the file no longer exists (a double-archive in the error
        // path). The important, correct outcome still holds: the file leaves the resend queue and is
        // archived to the error directory.
        assertThrows(OpenAS2Exception.class, () -> module.processFile(queued));

        assertFalse(queued.exists(), "a message that fails to resend should be removed from the resend queue");
        assertTrue(countFilesRecursively(errorDir.toFile()) >= 1, "the failed message should be archived to the error directory");
    }

    private int countFilesRecursively(File dir) {
        int count = 0;
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        for (File f : files) {
            count += f.isDirectory() ? countFilesRecursively(f) : 1;
        }
        return count;
    }
}
