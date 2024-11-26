package org.openas2.app.cert;

import org.openas2.OpenAS2Exception;
import org.openas2.WrappedException;
import org.openas2.cert.AliasedCertificateFactory;
import org.openas2.cmd.CommandResult;
import org.openas2.util.AS2Util;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.cert.CertificateException;

public class ImportCertCommand extends AliasedCertCommand {
    public String getDefaultDescription() {
        return "Import a certificate into the current certificate store";
    }

    public String getDefaultName() {
        return "import";
    }

    public String getDefaultUsage() {
        return "import <alias> <filename> [<password>]";
    }

    public CommandResult execute(AliasedCertificateFactory certFx, Object[] params) throws OpenAS2Exception {
        if (params.length < 2) {
            return new CommandResult(CommandResult.TYPE_INVALID_PARAM_COUNT, getUsage());
        }

        synchronized (certFx) {
            String alias = params[0].toString();
            String filename = params[1].toString();
            String password = null;

            if (params.length > 2) {
                password = params[2].toString();
            }

            try {
                if (filename.endsWith(".p12")) {
                    if (password == null) {
                        return new CommandResult(CommandResult.TYPE_INVALID_PARAM_COUNT, getUsage() + " (Password is required for p12 files)");
                    }

                    return importPrivateKey(certFx, alias, filename, password);
                }
                return importCert(certFx, alias, filename);
            } catch (Exception e) {
                throw new WrappedException(e);
            }
        }
    }

    protected CommandResult importCert(AliasedCertificateFactory certFx, String alias, String filename) throws IOException, CertificateException, OpenAS2Exception {
        FileInputStream fis = new FileInputStream(filename);
        BufferedInputStream bis = new BufferedInputStream(fis);
        if (certFx.importCert(alias, bis)) {
            CommandResult cmdRes = new CommandResult(CommandResult.TYPE_OK, "Certificate(s) imported successfully");
            cmdRes.getResults().add("Imported certificate: " + certFx.getCertificate(alias).toString());
            return cmdRes;
        }
        return new CommandResult(CommandResult.TYPE_ERROR, "No valid X509 certificates found");
    }

    protected CommandResult importPrivateKey(AliasedCertificateFactory certFx, String alias, String filename, String password) throws Exception {
        KeyStore ks = AS2Util.getCryptoHelper().getKeyStore();
        ks.load(new FileInputStream(filename), password.toCharArray());

        if (certFx.importPrivateKey(alias, ks, password)) {
            return new CommandResult(CommandResult.TYPE_OK, "Imported certificate and key: " + certFx.getPrivateKey(alias).toString());
        }
        return new CommandResult(CommandResult.TYPE_ERROR, "No valid X509 certificates found");
    }
}
