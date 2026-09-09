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
 * Merges attributes, poller configuration and partner references into an existing partnership
 * without deleting and recreating it.
 * <p>
 * Only what is supplied is changed, so this is a partial update. It exists because "add" refuses to
 * overwrite an existing entry, which previously left callers no way to change a partnership except
 * to delete it first and lose the definition if the recreate then failed.
 */
public class UpdatePartnershipCommand extends AliasedPartnershipsCommand {

    private static final String POLLER_CONFIG_PREFIX = Partnership.PCFG_POLLER + ".";
    private static final String SENDER_NAME_PARAM = Partnership.PTYPE_SENDER + "." + Partnership.PID_NAME;
    private static final String RECEIVER_NAME_PARAM = Partnership.PTYPE_RECEIVER + "." + Partnership.PID_NAME;

    public String getDefaultDescription() {
        return "Update an existing partnership, leaving anything not supplied unchanged.";
    }

    public String getDefaultName() {
        return "update";
    }

    public String getDefaultUsage() {
        return "update <name> [attribute-1=value-1] ... [attribute-n=value-n] [pollerConfig.attr=value ...]"
                + " [" + SENDER_NAME_PARAM + "=<partner name>] [" + RECEIVER_NAME_PARAM + "=<partner name>]\n"
                + "\t- subject=\"New subject\" replaces the value of the existing subject attribute or adds it\n"
                + "\t- pollerConfig.interval=30 replaces that attribute of the pollerConfig element or adds it\n"
                + "\t- " + SENDER_NAME_PARAM + "=PartnerB points the partnership at a different partner";
    }

    public CommandResult execute(PartnershipFactory partFx, Object[] params) throws OpenAS2Exception {
        if (params.length < 2) {
            return new CommandResult(CommandResult.TYPE_INVALID_PARAM_COUNT, getUsage());
        }

        synchronized (partFx) {
            String name = params[0].toString();
            Map<String, String> attributes = new LinkedHashMap<String, String>();
            Map<String, String> pollerConfig = new LinkedHashMap<String, String>();
            String senderName = null;
            String receiverName = null;

            for (int i = 1; i < params.length; i++) {
                String param = params[i].toString();
                int equalsPos = param.indexOf('=');
                if (equalsPos == 0) {
                    return new CommandResult(CommandResult.TYPE_ERROR, "incoming parameter missing name");
                } else if (equalsPos < 0) {
                    return new CommandResult(CommandResult.TYPE_ERROR, "incoming parameter missing value");
                }
                String key = param.substring(0, equalsPos);
                String value = param.substring(equalsPos + 1);
                if (SENDER_NAME_PARAM.equals(key)) {
                    senderName = value;
                } else if (RECEIVER_NAME_PARAM.equals(key)) {
                    receiverName = value;
                } else if (key.startsWith(POLLER_CONFIG_PREFIX)) {
                    pollerConfig.put(key.substring(POLLER_CONFIG_PREFIX.length()), value);
                } else if (Partnership.PID_NAME.equals(key)) {
                    // The name identifies the partnership, so changing it here would leave callers
                    // unable to tell which definition they are now looking at
                    return new CommandResult(CommandResult.TYPE_ERROR,
                            "A partnership cannot be renamed by an update. Add the new partnership then delete the old one.");
                } else {
                    attributes.put(key, value);
                }
            }

            if (attributes.isEmpty() && pollerConfig.isEmpty() && senderName == null && receiverName == null) {
                return new CommandResult(CommandResult.TYPE_ERROR, "Nothing to update.\n" + getUsage());
            }

            if (!(partFx instanceof DbPartnershipFactory) && !(partFx instanceof XMLPartnershipFactory)) {
                return new CommandResult(CommandResult.TYPE_COMMAND_NOT_SUPPORTED, "Not supported by current partnership store");
            }
            try {
                if (partFx instanceof DbPartnershipFactory) {
                    ((DbPartnershipFactory) partFx).updatePartnership(name, attributes, pollerConfig, senderName, receiverName);
                } else {
                    ((XMLPartnershipFactory) partFx).updatePartnership(name, attributes, pollerConfig, senderName, receiverName);
                }
            } catch (OpenAS2Exception e) {
                // Report an unknown partnership or partner as a command error so an API caller gets
                // the reason back rather than a server error, matching how the delete command behaves
                return new CommandResult(CommandResult.TYPE_ERROR, e.getMessage());
            }
            return new CommandResult(CommandResult.TYPE_OK, "Updated partnership: " + name);
        }
    }
}
