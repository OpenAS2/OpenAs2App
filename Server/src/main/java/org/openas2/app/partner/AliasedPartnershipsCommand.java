package org.openas2.app.partner;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.openas2.OpenAS2Exception;
import org.openas2.cmd.BaseCommand;
import org.openas2.cmd.CommandResult;
import org.openas2.partner.PartnershipFactory;

public abstract class AliasedPartnershipsCommand extends BaseCommand {
    private Log logger = LogFactory.getLog(getClass().getName());
    public CommandResult execute(Object[] params) {

        for (Object param : params) {
            logger.debug(param.toString());
        }

        try {
            PartnershipFactory partFx = getSession().getPartnershipFactory();

            if (partFx instanceof PartnershipFactory) {
                return execute(partFx, params);
            }
            return new CommandResult(CommandResult.TYPE_COMMAND_NOT_SUPPORTED, "Not supported by current partnership store");
        } catch (OpenAS2Exception oae) {
            oae.log();

            return new CommandResult(oae);
        }
    }

    protected abstract CommandResult execute(PartnershipFactory partFx, Object[] params) throws OpenAS2Exception;

}
