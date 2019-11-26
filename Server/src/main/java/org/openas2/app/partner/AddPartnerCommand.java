package org.openas2.app.partner;

import org.openas2.OpenAS2Exception;
import org.openas2.cmd.CommandResult;
import org.openas2.partner.PartnershipFactory;
import org.openas2.partner.XMLPartnershipFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

/**
 * adds a new partner entry in partnership store
 *
 * @author joseph mcverry
 */
public class AddPartnerCommand extends AliasedPartnershipsCommand {
    public String getDefaultDescription() {
        return "Add a new partner to partnership store.";
    }

    public String getDefaultName() {
        return "add";
    }

    public String getDefaultUsage() {
        return "add name <attribute 1=value 1> <attribute 2=value 2> ... <attribute n=value n>";
    }

    public CommandResult execute(PartnershipFactory partFx, Object[] params) throws OpenAS2Exception {
        if (params.length < 1) {
            return new CommandResult(CommandResult.TYPE_INVALID_PARAM_COUNT, getUsage());
        }

        synchronized (partFx) {

            DocumentBuilder db = null;
            try {
                db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            } catch (ParserConfigurationException e) {
                throw new OpenAS2Exception(e);
            } catch (FactoryConfigurationError e) {
                throw new OpenAS2Exception(e);
            }

            Document doc = db.newDocument();

            Element root = doc.createElement("partner");
            doc.appendChild(root);

            for (int i = 0; i < params.length; i++) {
                String param = (String) params[i];
                int pos = param.indexOf('=');
                if (i == 0) {
                    root.setAttribute("name", param);
                } else if (pos == 0) {
                    return new CommandResult(CommandResult.TYPE_ERROR, "incoming parameter missing name");
                } else if (pos > 0) {
                    root.setAttribute(param.substring(0, pos), param.substring(pos + 1));

                } else {
                    return new CommandResult(CommandResult.TYPE_ERROR, "incoming parameter missing value");
                }

            }

            ((XMLPartnershipFactory) partFx).loadPartner(partFx.getPartners(), root);

            return new CommandResult(CommandResult.TYPE_OK);
        }

    }
}
