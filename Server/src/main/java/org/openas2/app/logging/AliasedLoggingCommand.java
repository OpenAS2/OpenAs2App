package org.openas2.app.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.cmd.BaseCommand;
import org.openas2.cmd.CommandResult;

public abstract class AliasedLoggingCommand extends BaseCommand {

    public CommandResult execute(Object[] params) {

        try {
            Logger logger = LoggerFactory.getLogger(this.getClass().getName());
            if (logger instanceof org.openas2.util.Logging) {
                return execute(logger, params);
            }
            // Add support for other logging packages here as necessary
            return new CommandResult(CommandResult.TYPE_COMMAND_NOT_SUPPORTED, "Not supported by current logging factory");
        } catch (OpenAS2Exception oae) {
            oae.log();

            return new CommandResult(oae);
        }
    }

    protected abstract CommandResult execute(Logger logger, Object[] params) throws OpenAS2Exception;

}
