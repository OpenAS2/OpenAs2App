package org.openas2.support.config;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.openas2.support.FileMonitor;
import org.openas2.support.FileMonitorListener;

import java.io.File;
import java.util.Date;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * Created by nick on 08.04.17.
 */
@RunWith(MockitoJUnitRunner.class)
public class FileMonitorTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();
    @Mock
    private FileMonitorListener listener;

    @Test
    public void shouldTriggerListenersWhenFileChanged() throws Exception {
        File fileToObserve = Mockito.spy(temp.newFile());

        FileMonitor fileMonitor = new FileMonitor(fileToObserve, listener);
        verifyNoMoreInteractions(listener);

        fileMonitor.run();
        verifyNoMoreInteractions(listener);

        FileUtils.write(fileToObserve, RandomStringUtils.randomAlphanumeric(1024), "UTF-8");
        doReturn(new Date().getTime() + 3).when(fileToObserve).lastModified();
        fileMonitor.run();

        verify(listener).onFileEvent(eq(fileToObserve), eq(FileMonitorListener.EVENT_MODIFIED));
        reset(listener);

        fileMonitor.run();
        verifyNoMoreInteractions(listener);

    }
}
