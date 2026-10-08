package org.openas2.processor.sender;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.openas2.app.BaseServerSetup;
import org.openas2.processor.Processor;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that starting the server reports a file a cut off send left in the pending folder, rather
 * than the report only existing in isolation. How files are classified is covered by
 * {@link InterruptedSendReportTest}.
 */
@TestInstance(Lifecycle.PER_CLASS)
public class InterruptedSendReportStartupTest extends BaseServerSetup {

    @BeforeAll
    public void setUp() throws Exception {
        super.createFileSystemResources(this.getClass().getName());
        super.setup();
    }

    @AfterAll
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void theServerReportsAFileLeftBehindWhenItStarts() throws Exception {
        File pendingDir = new File(session.getProcessor().getParameters().get(Processor.PENDING_MDN_MSG_DIRECTORY_IDENTIFIER));
        Files.createDirectories(pendingDir.toPath());
        File stranded = new File(pendingDir, "stranded-invoice.edi");
        Files.write(stranded.toPath(), "payload".getBytes(StandardCharsets.UTF_8));

        Logger reportLogger = (Logger) LoggerFactory.getLogger(InterruptedSendReport.class);
        ListAppender<ILoggingEvent> captured = new ListAppender<ILoggingEvent>();
        captured.start();
        reportLogger.addAppender(captured);
        try {
            session.start();
        } finally {
            reportLogger.detachAppender(captured);
        }

        assertTrue(captured.list.stream().anyMatch(e -> e.getFormattedMessage().startsWith("Never sent: " + stranded.getAbsolutePath())),
                "starting the server should report the stranded file: " + captured.list);
    }
}
