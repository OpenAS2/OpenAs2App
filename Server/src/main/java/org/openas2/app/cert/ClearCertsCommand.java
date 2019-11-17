package org.openas2.app.cert;

import org.openas2.OpenAS2Exception;
import org.openas2.cert.AliasedCertificateFactory;
import org.openas2.cmd.CommandResult;

public class ClearCertsCommand extends AliasedCertCommand {
    public String getDefaultDescription() {
        return "Deletes all certificates from the store";
    }

    public String getDefaultName() {
        return "clear";
    }

    public String getDefaultUsage() {
        return "clear";
    }

    public CommandResult execute(AliasedCertificateFactory certFx, Object[] params) throws OpenAS2Exception {
        synchronized (certFx) {
            certFx.clearCertificates();

            return new CommandResult(CommandResult.TYPE_OK, "cleared");

        }
    }
}
