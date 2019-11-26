package org.openas2.app.partner;

import org.openas2.OpenAS2Exception;
import org.openas2.cmd.CommandResult;
import org.openas2.partner.PartnershipFactory;

import java.util.Iterator;
import java.util.Map;

/**
 * view the partner entries in the partnership store
 *
 * @author Joe McVerry
 */
public class ViewPartnerCommand extends AliasedPartnershipsCommand {
    public String getDefaultDescription() {
        return "View the partner entry in the partnership store.";
    }

    public String getDefaultName() {
        return "view";
    }

    public String getDefaultUsage() {
        return "view <name>";
    }

    protected CommandResult execute(PartnershipFactory partFx, Object[] params) throws OpenAS2Exception {
        if (params.length < 1) {
            return new CommandResult(CommandResult.TYPE_INVALID_PARAM_COUNT, getUsage());
        }
        synchronized (partFx) {

            String name = params[0].toString();

            Iterator<String> parts = partFx.getPartners().keySet().iterator();

            while (parts.hasNext()) {
                String partName = parts.next();
                if (partName.equals(name)) {
                    Map<Object, Object> partDefs = (Map<Object, Object>) partFx.getPartners().get(name);
                    //String out = name + "\n" + partDefs.toString();
                    return new CommandResult(CommandResult.TYPE_OK, partDefs);
                }
            }

            return new CommandResult(CommandResult.TYPE_ERROR, "Unknown partner name");
        }
    }
}
