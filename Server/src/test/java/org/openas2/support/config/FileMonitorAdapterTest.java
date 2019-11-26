package org.openas2.support.config;

import org.apache.commons.lang3.RandomUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openas2.OpenAS2Exception;
import org.openas2.support.FileMonitorAdapter;

import java.io.File;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

/**
 * Created by nick on 08.04.17.
 */
@RunWith(MockitoJUnitRunner.class)
public class FileMonitorAdapterTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    @Captor
    ArgumentCaptor<Runnable> monitorJob;
    @Mock
    private ScheduledExecutorService executorService;
    private File configFile;

    private FileMonitorAdapter adapter;

    @Before
    public void setUp() throws Exception {
        configFile = spy(tmp.newFile());
        adapter = spy(new FileMonitorAdapter() {
            @Override
            public void onConfigFileChanged() throws OpenAS2Exception {

            }
        });

    }

    @Test
    public void shouldNotScheduleRefreshWhenIntervalNotConfigured() throws Exception {
        adapter.scheduleIfNeed(executorService, configFile, 0, TimeUnit.SECONDS);

        verifyZeroInteractions(executorService);
    }

    @Test
    public void shouldScheduleConfigRefresh() throws Exception {

        int refreshInterval = RandomUtils.nextInt(1, 10);

        adapter.scheduleIfNeed(executorService, configFile, refreshInterval, TimeUnit.SECONDS);

        doReturn(new Date().getTime() + 10).when(configFile).lastModified();
        verify(executorService).scheduleAtFixedRate(monitorJob.capture(), eq((long) refreshInterval), eq((long) refreshInterval), eq(TimeUnit.SECONDS));

        monitorJob.getValue().run();

        verify(adapter).onConfigFileChanged();
    }
}
