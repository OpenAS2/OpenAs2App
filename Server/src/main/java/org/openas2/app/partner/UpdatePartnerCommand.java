package org.openas2.app.partner;

import org.openas2.OpenAS2Exception;
import org.openas2.cmd.CommandResult;
import org.openas2.partner.DbPartnershipFactory;
import org.openas2.partner.Partnership;
import org.openas2.partner.PartnershipFactory;
import org.openas2.partner.XMLPartnershipFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Merges attributes into an existing partner without deleting and recreating it.
 * <p>
 * Only the attributes supplied are changed, so this is a partial update. It exists because "add"
 * refuses to overwrite an existing entry, which previously left callers no way to change a partner
 * except to delete it first and lose the definition if the recreate then failed.
 */
public class UpdatePartnerCommand extends AliasedPartnershipsCommand {
    public String getDefaultDescription() {
        return "Update attributes of an existing partner, leaving the ones not supplied unchanged.";
    }

    public String getDefaultName() {
        return "update";
    }

    public String getDefaultUsage() {
        return "update <name> <attribute-1=value-1> [attribute-2=value-2] ... [attribute-n=value-n]";
    }

    public CommandResult execute(PartnershipFactory partFx, Object[] params) throws OpenAS2Exception {
        if (params.length < 2) {
            return new CommandResult(CommandResult.TYPE_INVALID_PARAM_COUNT, getUsage());
        }

        synchronized (partFx) {
            String name = params[0].toString();
            Map<String, String> attributes = new LinkedHashMap<String, String>();
            for (int i = 1; i < params.length; i++) {
                String param = params[i].toString();
                int equalsPos = param.indexOf('=');
                if (equalsPos == 0) {
                    return new CommandResult(CommandResult.TYPE_ERROR, "incoming parameter missing name");
                } else if (equalsPos < 0) {
                    return new CommandResult(CommandResult.TYPE_ERROR, "incoming parameter missing value");
                }
                String attrName = param.substring(0, equalsPos);
                if (Partnership.PID_NAME.equals(attrName)) {
                    // The name identifies the partner and the partnerships reference it, so changing
                    // it here would silently orphan them
                    return new CommandResult(CommandResult.TYPE_ERROR,
                            "A partner cannot be renamed by an update. Add the new partner, repoint the partnerships, then delete the old one.");
                }
                attributes.put(attrName, param.substring(equalsPos + 1));
            }

            if (!(partFx instanceof DbPartnershipFactory) && !(partFx instanceof XMLPartnershipFactory)) {
                return new CommandResult(CommandResult.TYPE_COMMAND_NOT_SUPPORTED, "Not supported by current partnership store");
            }
            try {
                if (partFx instanceof DbPartnershipFactory) {
                    ((DbPartnershipFactory) partFx).updatePartner(name, attributes);
                } else {
                    ((XMLPartnershipFactory) partFx).updatePartner(name, attributes);
                }
            } catch (OpenAS2Exception e) {
                // Report an unknown partner as a command error so an API caller gets the reason back
                // rather than a server error, matching how the delete command behaves
                return new CommandResult(CommandResult.TYPE_ERROR, e.getMessage());
            }
            return new CommandResult(CommandResult.TYPE_OK, "Updated partner: " + name);
        }
    }
}
