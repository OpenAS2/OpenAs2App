package org.openas2.app;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.ComponentNotFoundException;
import org.openas2.processor.ProcessorModule;
import org.openas2.processor.receiver.HealthCheckHandler;

import java.util.ArrayList;
import java.util.List;

public class HealthCheck {

    private Log logger = LogFactory.getLog(HealthCheckHandler.class.getSimpleName());

    /*
     * Runs a check of application status to try to determine if ther is any issue to be dealt with
     * @param module the module that invoked the health check
     * @return a string array of issues identified in executing the health check. the array will be empty if no errors are identified
     */
    public List<String> runCheck(ProcessorModule module) {
        // Invoke each configured modules healthcheck method with any results returned in the failures array
        List<String> failures = new ArrayList<String>();
        try {
            if (module == null) {
                logger.warn("Module passed in to helathcheck is NULL so not module check was performed.");
            } else {
                module.getSession().getProcessor().checkActiveModules(failures);
            }
        } catch (ComponentNotFoundException e) {
            failures.add("Error executing module check: " + e.getMessage());
            e.printStackTrace();
        }
        // TODO : Add other (non-module) checks

        return failures;
    }

}
