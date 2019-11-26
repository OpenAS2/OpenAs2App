package org.openas2.app.partner;

import org.openas2.OpenAS2Exception;
import org.openas2.cmd.BaseCommand;
import org.openas2.cmd.CommandResult;
import org.openas2.partner.PartnershipFactory;
import org.openas2.partner.XMLPartnershipFactory;

/**
 * replaces the partnership store, backs up the original store
 *
 * @author joseph mcverry
 */
public class StorePartnershipsCommand extends BaseCommand {
    public String getDefaultDescription() {
        return "Stores the current partnerships in storage";
    }

    public String getDefaultName() {
        return "store";
    }

    public String getDefaultUsage() {
        return "store";
    }

    public CommandResult execute(Object[] params) {

        try {
            PartnershipFactory partnerFx = getSession().getPartnershipFactory();
            synchronized (getSession().getPartnershipFactory()) {

                if (partnerFx instanceof XMLPartnershipFactory) {
                    ((XMLPartnershipFactory) partnerFx).storePartnership();

                    return new CommandResult(CommandResult.TYPE_OK, "Stored partnerships");
                }
                return new CommandResult(CommandResult.TYPE_COMMAND_NOT_SUPPORTED, "Not supported by current partnership store, must be XML");
            }
        } catch (OpenAS2Exception oae) {
            oae.terminate();

            return new CommandResult(oae);
        }
    }
}
