package org.openas2.app.partner;

import org.openas2.OpenAS2Exception;
import org.openas2.cmd.CommandResult;
import org.openas2.partner.Partnership;
import org.openas2.partner.PartnershipFactory;

import java.util.Iterator;
import java.util.List;

/**
 * list partnerships in partnership store by names
 *
 * @author joseph mcverry
 */
public class ListPartnershipsCommand extends AliasedPartnershipsCommand {
    public String getDefaultDescription() {
        return "List all partnerships in the current partnership store";
    }

    public String getDefaultName() {
        return "list";
    }

    public String getDefaultUsage() {
        return "list";
    }

    public CommandResult execute(PartnershipFactory partFx, Object[] params) throws OpenAS2Exception {
        synchronized (partFx) {

            List<Partnership> parts = partFx.getPartnerships();
            Iterator<Partnership> partIt = parts.iterator();

            CommandResult cmdRes = new CommandResult(CommandResult.TYPE_OK);

            while (partIt.hasNext()) {
                Partnership part = partIt.next();
                cmdRes.getResults().add(part.getName());
            }

            if (cmdRes.getResults().size() == 0) {
                cmdRes.getResults().add("No partnerships available");
            }

            return cmdRes;
        }
    }
}
