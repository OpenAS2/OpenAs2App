package org.openas2.app.partner;

import org.openas2.OpenAS2Exception;
import org.openas2.cmd.CommandResult;
import org.openas2.partner.DbPartnershipFactory;
import org.openas2.partner.PartnershipFactory;
import org.openas2.partner.XMLPartnershipFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.FactoryConfigurationError;
import javax.xml.parsers.ParserConfigurationException;

import java.util.LinkedHashMap;
import java.util.Map;

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
        return "add name <attribute 1=value-1> <attribute 2=value-2> ... <attribute n=value-n>";
    }

    public CommandResult execute(PartnershipFactory partFx, Object[] params) throws OpenAS2Exception {
        if (params.length < 1) {
            return new CommandResult(CommandResult.TYPE_INVALID_PARAM_COUNT, getUsage());
        }

        synchronized (partFx) {
            Map<String, String> attributes = new LinkedHashMap<String, String>();
            for (int i = 0; i < params.length; i++) {
                String param = (String) params[i];
                int pos = param.indexOf('=');
                if (i == 0) {
                    attributes.put("name", param);
                } else if (pos == 0) {
                    return new CommandResult(CommandResult.TYPE_ERROR, "incoming parameter missing name");
                } else if (pos > 0) {
                    attributes.put(param.substring(0, pos), param.substring(pos + 1));
                } else {
                    return new CommandResult(CommandResult.TYPE_ERROR, "incoming parameter missing value");
                }
            }

            if (partFx instanceof DbPartnershipFactory) {
                ((DbPartnershipFactory) partFx).addPartner(attributes);
                return new CommandResult(CommandResult.TYPE_OK);
            }
            if (!(partFx instanceof XMLPartnershipFactory)) {
                return new CommandResult(CommandResult.TYPE_COMMAND_NOT_SUPPORTED, "Not supported by current partnership store");
            }

            DocumentBuilder db = null;
            try {
                db = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            } catch (ParserConfigurationException e) {
                throw new OpenAS2Exception(e);
            } catch (FactoryConfigurationError e) {
                throw new OpenAS2Exception(e);
            }

            Document doc = db.newDocument();

            Element partnerRoot = doc.createElement("partner");
            doc.appendChild(partnerRoot);
            for (Map.Entry<String, String> attribute : attributes.entrySet()) {
                partnerRoot.setAttribute(attribute.getKey(), attribute.getValue());
            }

            ((XMLPartnershipFactory) partFx).loadPartner(partFx.getPartners(), partnerRoot);
            // Add the element to the already loaded partnership XML doc
            ((XMLPartnershipFactory) partFx).addElement(partnerRoot);

            return new CommandResult(CommandResult.TYPE_OK);
        }

    }
}
