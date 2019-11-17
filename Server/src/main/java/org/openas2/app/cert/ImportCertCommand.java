package org.openas2.app.cert;

import org.openas2.OpenAS2Exception;
import org.openas2.WrappedException;
import org.openas2.cert.AliasedCertificateFactory;
import org.openas2.cmd.CommandResult;
import org.openas2.util.AS2Util;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;

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

        java.security.cert.CertificateFactory cf = java.security.cert.CertificateFactory.getInstance("X.509");

        CommandResult cmdRes = new CommandResult(CommandResult.TYPE_OK, "Certificate(s) imported successfully");

        while (bis.available() > 0) {
            Certificate cert = cf.generateCertificate(bis);

            if (cert instanceof X509Certificate) {
                certFx.addCertificate(alias, (X509Certificate) cert, true);
                cmdRes.getResults().add("Imported certificate: " + cert.toString());

                return cmdRes;
            }
        }

        return new CommandResult(CommandResult.TYPE_ERROR, "No valid X509 certificates found");
    }

    protected CommandResult importPrivateKey(AliasedCertificateFactory certFx, String alias, String filename, String password) throws Exception {
        KeyStore ks = AS2Util.getCryptoHelper().getKeyStore();
        ks.load(new FileInputStream(filename), password.toCharArray());

        Enumeration<String> aliases = ks.aliases();

        while (aliases.hasMoreElements()) {
            String certAlias = aliases.nextElement();
            Certificate cert = ks.getCertificate(certAlias);

            if (cert instanceof X509Certificate) {
                certFx.addCertificate(alias, (X509Certificate) cert, true);

                Key certKey = ks.getKey(certAlias, password.toCharArray());
                certFx.addPrivateKey(alias, certKey, password);

                return new CommandResult(CommandResult.TYPE_OK, "Imported certificate and key: " + cert.toString());
            }
        }

        return new CommandResult(CommandResult.TYPE_ERROR, "No valid X509 certificates found");

    }
}
