package org.openas2.app.cert;

import org.openas2.OpenAS2Exception;
import org.openas2.cert.AliasedCertificateFactory;
import org.openas2.cmd.CommandResult;

public class DeleteCertCommand extends AliasedCertCommand {
    public String getDefaultDescription() {
        return "Delete the certificate and private key associated with an alias.";
    }

    public String getDefaultName() {
        return "delete";
    }

    public String getDefaultUsage() {
        return "delete <alias>";
    }

    public CommandResult execute(AliasedCertificateFactory certFx, Object[] params) throws OpenAS2Exception {
        if (params.length < 1) {
            return new CommandResult(CommandResult.TYPE_INVALID_PARAM_COUNT, getUsage());
        }

        synchronized (certFx) {
            String alias = params[0].toString();

            certFx.removeCertificate(alias);

            return new CommandResult(CommandResult.TYPE_OK, "deleted " + alias);

        }
    }
}
