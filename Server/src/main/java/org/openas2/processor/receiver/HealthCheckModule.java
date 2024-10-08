/* Copyright Uhuru Technology 2016 https://www.uhurutechnology.com
 * Distributed under the GPLv3 license or a commercial license must be acquired.
 */
package org.openas2.processor.receiver;

import java.util.List;

public class HealthCheckModule extends NetModule {


    protected NetModuleHandler getHandler() {
        return new HealthCheckHandler(this);
    }

    @Override
    public boolean healthcheck(List<String> failures) {
        return true;
    }

}
