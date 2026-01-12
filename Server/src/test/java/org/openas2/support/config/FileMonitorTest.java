package org.openas2.support.config;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openas2.TestUtils;
import org.openas2.support.FileMonitor;
import org.openas2.support.FileMonitorListener;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Created by nick on 08.04.17.
 */
@ExtendWith(MockitoExtension.class)
public class FileMonitorTest {

    @TempDir
    public static File tmp;
    @Mock
    private FileMonitorListener listener;

    @Test
    public void shouldTriggerListenersWhenFileChanged() throws Exception {
        tmp = Files.createTempDirectory("testResources").toFile();
        File fileToObserve = spy(Files.createFile(Paths.get(tmp.toString(), "test.txt")).toFile());
        doReturn(true).when(fileToObserve).exists();
        doReturn(true).when(fileToObserve).isFile();
        doReturn(new Date().getTime()).when(fileToObserve).lastModified();

        FileMonitor fileMonitor = new FileMonitor(fileToObserve, listener);
        verifyNoInteractions(listener);

        fileMonitor.run();
        verifyNoInteractions(listener);

        doReturn(new Date().getTime() + 3).when(fileToObserve).lastModified();
        fileMonitor.run();

        verify(listener).onFileEvent(eq(fileToObserve), eq(FileMonitorListener.EVENT_MODIFIED));
        reset(listener);

        fileMonitor.run();
        verifyNoInteractions(listener);
    }

    @AfterAll
    public static void tearDown() throws Exception {
        TestUtils.deleteDirectory(tmp);
    }
}
