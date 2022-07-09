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
public class FilterCertPartnerCommand extends AliasedPartnershipsCommand {
    public String getDefaultDescription() {
        return "filter partner for cert (x509_alias) entry in the partnership store.";
    }

    public String getDefaultName() {
        return "filter";
    }

    public String getDefaultUsage() {
        return "filter <x509_alias>";
    }

    protected CommandResult execute(PartnershipFactory partFx, Object[] params) throws OpenAS2Exception {
        if (params.length < 1) {
            return new CommandResult(CommandResult.TYPE_INVALID_PARAM_COUNT, getUsage());
        }
        synchronized (partFx) {

            String x509_alias = params[0].toString();

            Iterator<String> parts = partFx.getPartners().keySet().iterator();
            // OrDefault("x509_alias", x509_alias);

            while (parts.hasNext()) {
                String partName = parts.next();
                System.out.println("partName" + "------------------------------" + x509_alias + " " + partName);
                // if (partName.equals(x509_alias)) {
                @SuppressWarnings("unchecked")
                Map<Object, Object> partDefs = (Map<Object, Object>) partFx.getPartners().get(partName);
                if(partDefs.getOrDefault("x509_alias", x509_alias).equals(x509_alias)){
                    System.out.println("%%%%%%%% " + partDefs);
                    // String out = name + "\n" + partDefs.toString();
                    return new CommandResult(CommandResult.TYPE_OK, partDefs);
                }
                // }
            }

            return new CommandResult(CommandResult.TYPE_ERROR, "Unknown partner x509_alias");
        }
    }
}
