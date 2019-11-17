package org.openas2.app.cert;

import org.openas2.OpenAS2Exception;
import org.openas2.WrappedException;
import org.openas2.cert.AliasedCertificateFactory;
import org.openas2.cmd.CommandResult;
import org.openas2.util.ByteCoder;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class ImportCertInEncodedStreamCommand extends AliasedCertCommand {
    public String getDefaultDescription() {
        return "Import a certificate into the current certificate store using an encoded byte stream";
    }

    public String getDefaultName() {
        return "importbystream";
    }

    public String getDefaultUsage() {
        return "importbybstream <alias> <encodedCertificateStream>";
    }

    public CommandResult execute(AliasedCertificateFactory certFx, Object[] params) throws OpenAS2Exception {
        if (params.length != 2) {
            return new CommandResult(CommandResult.TYPE_INVALID_PARAM_COUNT, getUsage());
        }

        synchronized (certFx) {
            try {
                return importCert(certFx, params[0].toString(), params[1].toString());
            } catch (Exception e) {
                throw new WrappedException(e);
            }
        }
    }

    protected CommandResult importCert(AliasedCertificateFactory certFx, String alias, String encodedCert) throws IOException, CertificateException, OpenAS2Exception {

        ByteArrayInputStream bais = new ByteArrayInputStream(ByteCoder.decode(encodedCert).getBytes());

        java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");

        CommandResult cmdRes = new CommandResult(CommandResult.TYPE_OK, "Certificate(s) imported successfully");

        while (bais.available() > 0) {
            Certificate cert = cf.generateCertificate(bais);

            if (cert instanceof X509Certificate) {
                certFx.addCertificate(alias, (X509Certificate) cert, true);
                cmdRes.getResults().add("Imported certificate: " + cert.toString());

                return cmdRes;
            }
        }

        return new CommandResult(CommandResult.TYPE_ERROR, "No valid X509 certificates found");
    }


}
