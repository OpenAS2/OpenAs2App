package org.openas2.app.partner;

import org.openas2.OpenAS2Exception;
import org.openas2.cmd.CommandResult;
import org.openas2.partner.PartnershipFactory;

import java.util.Iterator;
import java.util.Map;

/**
 * list partner entries in partnership store
 *
 * @author joseph mcverry
 */
public class ListPartnersCommand extends AliasedPartnershipsCommand {
    public String getDefaultDescription() {
        return "List all partners in the current partnership store";
    }

    public String getDefaultName() {
        return "list";
    }

    public String getDefaultUsage() {
        return "list";
    }

    public CommandResult execute(PartnershipFactory partFx, Object[] params) throws OpenAS2Exception {

        synchronized (partFx) {

            Map<String, Object> partners = partFx.getPartners();
            Iterator<String> partIt = partners.keySet().iterator();

            CommandResult cmdRes = new CommandResult(CommandResult.TYPE_OK);

            while (partIt.hasNext()) {
                cmdRes.getResults().add(partIt.next());
            }

            if (cmdRes.getResults().size() == 0) {
                cmdRes.getResults().add("No partner definitions available");
            }

            return cmdRes;
        }
    }
}
