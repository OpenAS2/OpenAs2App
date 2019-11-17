package org.openas2.app.partner;

import org.openas2.OpenAS2Exception;
import org.openas2.cmd.CommandResult;
import org.openas2.partner.Partnership;
import org.openas2.partner.PartnershipFactory;

import java.util.Iterator;

/**
 * removes a partner entry in partnership store
 *
 * @author joseph mcverry
 */
public class DeletePartnerCommand extends AliasedPartnershipsCommand {
    public String getDefaultDescription() {
        return "Delete the partnership associated with an name.";
    }

    public String getDefaultName() {
        return "delete";
    }

    public String getDefaultUsage() {
        return "delete <name>";
    }

    public CommandResult execute(PartnershipFactory partFx, Object[] params) throws OpenAS2Exception {
        if (params.length < 1) {
            return new CommandResult(CommandResult.TYPE_INVALID_PARAM_COUNT, getUsage());
        }

        synchronized (partFx) {

            String name = params[0].toString();
            Iterator<String> parts = partFx.getPartners().keySet().iterator();

            boolean found = false;

            while (parts.hasNext()) {
                String partName = parts.next();
                if (partName.equals(name)) {
                    found = true;
                }
            }

            if (found == false) {
                return new CommandResult(CommandResult.TYPE_ERROR, "Unknown partner name");
            }

            Iterator<Partnership> partnerships = partFx.getPartnerships().iterator();
            boolean partnershipFound = false;
            while (partnerships.hasNext() && partnershipFound == false) {
                Partnership part = partnerships.next();
                partnershipFound = part.getReceiverIDs().containsValue(name) || part.getSenderIDs().containsValue(name);
            }

            if (partnershipFound) {
                return new CommandResult(CommandResult.TYPE_ERROR, "Can not delete partner; it is tied to some partnerships");
            }

            partFx.getPartners().remove(name);

            return new CommandResult(CommandResult.TYPE_OK);
        }
    }
}
