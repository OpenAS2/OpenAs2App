package org.openas2.app.cert;

import org.openas2.OpenAS2Exception;
import org.openas2.cert.AliasedCertificateFactory;
import org.openas2.cert.CertificateFactory;
import org.openas2.cmd.BaseCommand;
import org.openas2.cmd.CommandResult;

public abstract class AliasedCertCommand extends BaseCommand {

    public CommandResult execute(Object[] params) {

        try {
            CertificateFactory certFx = getSession().getCertificateFactory();

            if (certFx instanceof AliasedCertificateFactory) {
                return execute((AliasedCertificateFactory) certFx, params);
            }
            return new CommandResult(CommandResult.TYPE_COMMAND_NOT_SUPPORTED, "Not supported by current certificate store");
        } catch (OpenAS2Exception oae) {
            oae.terminate();

            return new CommandResult(oae);
        }
    }

    protected abstract CommandResult execute(AliasedCertificateFactory certFx, Object[] params) throws OpenAS2Exception;

}
