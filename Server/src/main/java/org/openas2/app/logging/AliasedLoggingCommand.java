package org.openas2.app.logging;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.cmd.BaseCommand;
import org.openas2.cmd.CommandResult;

public abstract class AliasedLoggingCommand extends BaseCommand {

    public CommandResult execute(Object[] params) {

        try {
            Log logger = LogFactory.getLog(this.getClass().getName());
            if (logger instanceof org.openas2.logging.Log) {
                return execute(logger, params);
            }
            // Add support for other logging packages here as necessary
            return new CommandResult(CommandResult.TYPE_COMMAND_NOT_SUPPORTED, "Not supported by current logging factory");
        } catch (OpenAS2Exception oae) {
            oae.terminate();

            return new CommandResult(oae);
        }
    }

    protected abstract CommandResult execute(Log logger, Object[] params) throws OpenAS2Exception;

}
