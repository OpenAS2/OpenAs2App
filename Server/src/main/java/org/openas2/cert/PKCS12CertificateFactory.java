package org.openas2.cert;

import java.io.File;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.openas2.OpenAS2Exception;
import org.openas2.params.InvalidParameterException;
import org.openas2.schedule.HasSchedule;
import org.openas2.support.FileMonitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Supports keystores on the file system that require automatic refresh of the cached certs
 *  when changes to the file occurs whilst OpenAS2 is running.
 */
public class PKCS12CertificateFactory extends X509CertificateFactory implements HasSchedule {

    private Logger logger = LoggerFactory.getLogger(PKCS12CertificateFactory.class);

    private int getRefreshInterval() throws InvalidParameterException {
        return getParameterInt(PARAM_INTERVAL, false);
    }


    @Override
    public void schedule(ScheduledExecutorService executor) throws OpenAS2Exception {
        new FileMonitorAdapter() {
            @Override
            public void onConfigFileChanged() throws OpenAS2Exception {
                load();
                logger.info("- Certificates Reloaded -");
            }
        }.scheduleIfNeed(executor, new File(getFilename()), getRefreshInterval(), TimeUnit.SECONDS);
    }
}
