package org.openas2.app.partner;

import org.openas2.OpenAS2Exception;
import org.openas2.cmd.BaseCommand;
import org.openas2.cmd.CommandResult;
import org.openas2.partner.PartnershipFactory;

public abstract class AliasedPartnershipsCommand extends BaseCommand {

    public CommandResult execute(Object[] params) {

        try {
            PartnershipFactory partFx = getSession().getPartnershipFactory();

            if (partFx instanceof PartnershipFactory) {
                return execute(partFx, params);
            }
            return new CommandResult(CommandResult.TYPE_COMMAND_NOT_SUPPORTED, "Not supported by current partnership store");
        } catch (OpenAS2Exception oae) {
            oae.terminate();

            return new CommandResult(oae);
        }
    }

    protected abstract CommandResult execute(PartnershipFactory partFx, Object[] params) throws OpenAS2Exception;

}
