package org.openas2.app.logging;

import org.apache.commons.logging.Log;
import org.openas2.OpenAS2Exception;
import org.openas2.Session;
import org.openas2.cmd.CommandResult;

/**
 * adds a new partner entry in partnership store
 *
 * @author christopher broderick
 */
public class SetLogLevelCommand extends AliasedLoggingCommand {
    public String getDefaultDescription() {
        return "Set the log level to one of OFF, TRACE, DEBUG, INFO, WARN, ERROR or FATAL." + "\n\tTo disable the override and revert to the configured startup logging levels use RESET." + "\n\tThe level can be set for a specific class by including the class name." + "\n\tNOTE: This command does NOT persist the log level change through a restart of the application.";
    }

    public String getDefaultName() {
        return "setlevel";
    }

    public String getDefaultUsage() {
        return "setlevel <LEVEL> [target class name]\n\t eg. setlevel TRACE\n\t     setlevel INFO AS2SenderModule";
    }

    public CommandResult execute(Log logger, Object[] params) throws OpenAS2Exception {
        if (params == null) {
            return new CommandResult(CommandResult.TYPE_ERROR, "no parameters found");
        }
        int parmCnt = params.length;
        if (parmCnt < 1 || parmCnt > 2) {
            return new CommandResult(CommandResult.TYPE_INVALID_PARAM_COUNT, getUsage());
        }
        String logOverridePropName = Session.LOG_LEVEL_OVERRIDE_KEY;
        if (parmCnt == 2) {
            logOverridePropName += "." + params[1];
        }
        if ("RESET".equalsIgnoreCase((String) params[0])) {
            System.clearProperty(logOverridePropName);
        } else {
            System.setProperty(logOverridePropName, (String) params[0]);
        }

        return new CommandResult(CommandResult.TYPE_OK);

    }
}
