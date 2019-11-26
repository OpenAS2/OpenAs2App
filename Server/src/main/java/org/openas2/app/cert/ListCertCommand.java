package org.openas2.app.cert;

import org.openas2.OpenAS2Exception;
import org.openas2.cert.AliasedCertificateFactory;
import org.openas2.cmd.CommandResult;

import java.security.cert.X509Certificate;
import java.util.Iterator;
import java.util.Map;

public class ListCertCommand extends AliasedCertCommand {
    public String getDefaultDescription() {
        return "List all certificate aliases in the current certificate store";
    }

    public String getDefaultName() {
        return "list";
    }

    public String getDefaultUsage() {
        return "list";
    }

    public CommandResult execute(AliasedCertificateFactory certFx, Object[] params) throws OpenAS2Exception {
        synchronized (certFx) {
            Map<String, X509Certificate> certs = certFx.getCertificates();
            Iterator<Map.Entry<String, X509Certificate>> certIt = certs.entrySet().iterator();
            Map.Entry<String, X509Certificate> currentCert;
            CommandResult cmdRes = new CommandResult(CommandResult.TYPE_OK);

            while (certIt.hasNext()) {
                currentCert = certIt.next();
                cmdRes.getResults().add(currentCert.getKey());
            }

            if (cmdRes.getResults().size() == 0) {
                cmdRes.getResults().add("No certificates available");
            }

            return cmdRes;

        }
    }
}
