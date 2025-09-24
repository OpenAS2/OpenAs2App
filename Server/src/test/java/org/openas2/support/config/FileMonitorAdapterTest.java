package org.openas2.support.config;

import org.apache.commons.lang3.RandomUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openas2.OpenAS2Exception;
import org.openas2.TestUtils;
import org.openas2.support.FileMonitorAdapter;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Created by nick on 08.04.17.
 */
@ExtendWith(MockitoExtension.class)
public class FileMonitorAdapterTest {

    @TempDir
    public static File tmp;
    @Captor
    ArgumentCaptor<Runnable> monitorJob;
    @Mock
    private ScheduledExecutorService executorService;
    private File configFile;

    private FileMonitorAdapter adapter;

    @BeforeEach
    public void setUp() throws Exception {
    	tmp = Files.createTempDirectory("testResources").toFile();
        configFile = spy(Files.createFile(Paths.get(tmp.toString(), "config.xml")).toFile());
        adapter = spy(new FileMonitorAdapter() {
            @Override
            public void onConfigFileChanged() throws OpenAS2Exception {

            }
        });

    }

    @AfterAll
    public static void tearDown() throws Exception {
        TestUtils.deleteDirectory(tmp);
    }

    @Test
    public void shouldNotScheduleRefreshWhenIntervalNotConfigured() throws Exception {
        adapter.scheduleIfNeed(executorService, configFile, 0, TimeUnit.SECONDS);

        verifyNoInteractions(executorService);
    }

    @Test
    public void shouldScheduleConfigRefresh() throws Exception {

        int refreshInterval = RandomUtils.secure().randomInt(1, 10);

        doReturn(true).when(configFile).exists();
        doReturn(true).when(configFile).isFile();
        doReturn(new Date().getTime()).when(configFile).lastModified();
        adapter.scheduleIfNeed(executorService, configFile, refreshInterval, TimeUnit.SECONDS);
        doReturn(new Date().getTime() + 10).when(configFile).lastModified();

        verify(executorService).scheduleAtFixedRate(monitorJob.capture(), eq((long) refreshInterval), eq((long) refreshInterval), eq(TimeUnit.SECONDS));

        monitorJob.getValue().run();

        verify(adapter).onConfigFileChanged();
    }
}
