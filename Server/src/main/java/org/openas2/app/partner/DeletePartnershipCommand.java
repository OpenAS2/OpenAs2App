package org.openas2.app.partner;

import org.openas2.OpenAS2Exception;
import org.openas2.cmd.CommandResult;
import org.openas2.partner.Partnership;
import org.openas2.partner.PartnershipFactory;
import org.openas2.partner.XMLPartnershipFactory;

import java.util.Iterator;

/**
 * removes a partnership entry in partnership store
 *
 * @author joseph mcverry
 */
public class DeletePartnershipCommand extends AliasedPartnershipsCommand {
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
            Iterator<Partnership> parts = partFx.getPartnerships().iterator();

            while (parts.hasNext()) {
                Partnership part = parts.next();
                if (part.getName().equals(name)) {
                    partFx.getPartnerships().remove(part);
                    if (!((XMLPartnershipFactory) partFx).deleteElement("/partnerships/partnership[@name='" + name + "']")) {
                        return new CommandResult(CommandResult.TYPE_ERROR, "Partnership delete failed in XML document for partnership name: " + name);
                    }
                    return new CommandResult(CommandResult.TYPE_OK);
                }
            }
            return new CommandResult(CommandResult.TYPE_ERROR, "Partnership not found: " + name);
        }
    }
}
