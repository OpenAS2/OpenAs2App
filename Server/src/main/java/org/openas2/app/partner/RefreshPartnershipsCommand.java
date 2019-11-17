package org.openas2.app.partner;

import org.openas2.OpenAS2Exception;
import org.openas2.cmd.BaseCommand;
import org.openas2.cmd.CommandResult;
import org.openas2.partner.PartnershipFactory;
import org.openas2.partner.RefreshablePartnershipFactory;

/**
 * reloads the partnership store
 */
public class RefreshPartnershipsCommand extends BaseCommand {
    public String getDefaultDescription() {
        return "Refresh the current partnerships from storage";
    }

    public String getDefaultName() {
        return "refresh";
    }

    public String getDefaultUsage() {
        return "refresh";
    }

    public CommandResult execute(Object[] params) {
        try {
            PartnershipFactory partnerFx = getSession().getPartnershipFactory();

            synchronized (partnerFx) {

                if (partnerFx instanceof RefreshablePartnershipFactory) {
                    ((RefreshablePartnershipFactory) partnerFx).refresh();

                    return new CommandResult(CommandResult.TYPE_OK, "Refreshed partnerships");
                }
                return new CommandResult(CommandResult.TYPE_COMMAND_NOT_SUPPORTED, "Not supported by current certificate store");
            }

        } catch (OpenAS2Exception oae) {
            oae.terminate();

            return new CommandResult(oae);
        }
    }
}
