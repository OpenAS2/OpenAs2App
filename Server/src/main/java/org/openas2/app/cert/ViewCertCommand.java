package org.openas2.app.cert;

import org.openas2.OpenAS2Exception;
import org.openas2.cert.AliasedCertificateFactory;
import org.openas2.cmd.CommandResult;

import java.security.cert.Certificate;

/**
 * view certs by alias
 *
 * @author Don Hillsberry
 */
public class ViewCertCommand extends AliasedCertCommand {
    public String getDefaultDescription() {
        return "View the certificate associated with an alias.";
    }

    public String getDefaultName() {
        return "view";
    }

    public String getDefaultUsage() {
        return "view <alias>";
    }

    protected CommandResult execute(AliasedCertificateFactory certFx, Object[] params) throws OpenAS2Exception {
        if (params.length < 1) {
            return new CommandResult(CommandResult.TYPE_INVALID_PARAM_COUNT, getUsage());
        }

        synchronized (certFx) {

            String alias = params[0].toString();

            Certificate cert = certFx.getCertificate(alias);

            return new CommandResult(CommandResult.TYPE_OK, cert);

        }
    }
}
