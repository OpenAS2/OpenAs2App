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
 * adds a new partnership entry in partneship store
 *
 * @author joseph mcverry
 */
public class AddPartnershipCommand extends AliasedPartnershipsCommand {
    public String getDefaultDescription() {
        return "Add a new partnership definition to partnership store.";
    }

    public String getDefaultName() {
        return "add";
    }

    public String getDefaultUsage() {
        return "add name senderId receiverId <attribute 1=value 1> <attribute 2=value 2> ... <attribute n=value n>";
    }

    public CommandResult execute(PartnershipFactory partFx, Object[] params) throws OpenAS2Exception {
        if (params.length < 3) {
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

            Element root = doc.createElement("partnership");
            doc.appendChild(root);

            for (int i = 0; i < params.length; i++) {
                String param = (String) params[i];
                int pos = param.indexOf('=');
                if (i == 0) {
                    root.setAttribute("name", param);
                } else if (i == 1) {
                    Element elem = doc.createElement("sender");
                    elem.setAttribute("name", param);
                    root.appendChild(elem);
                } else if (i == 2) {
                    Element elem = doc.createElement("receiver");
                    elem.setAttribute("name", param);
                    root.appendChild(elem);
                } else if (pos == 0) {
                    return new CommandResult(CommandResult.TYPE_ERROR, "incoming parameter missing name");
                } else if (pos > 0) {
                    Element elem = doc.createElement("attribute");
                    elem.setAttribute("name", param.substring(0, pos));
                    elem.setAttribute("value", param.substring(pos + 1));
                    root.appendChild(elem);

                } else {
                    return new CommandResult(CommandResult.TYPE_ERROR, "incoming parameter missing value");
                }

            }

            ((XMLPartnershipFactory) partFx).loadPartnership(partFx.getPartners(), partFx.getPartnerships(), root);

            return new CommandResult(CommandResult.TYPE_OK);
        }
    }
}
