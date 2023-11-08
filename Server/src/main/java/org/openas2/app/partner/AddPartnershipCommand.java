package org.openas2.app.partner;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.OpenAS2Exception;
import org.openas2.cmd.CommandResult;
import org.openas2.partner.Partnership;
import org.openas2.partner.PartnershipFactory;
import org.openas2.partner.XMLPartnershipFactory;
import org.openas2.processor.sender.AS2SenderModule;
import org.openas2.util.XMLUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * adds a new partnership entry in partnership store
 *
 * @author joseph mcverry
 */
public class AddPartnershipCommand extends AliasedPartnershipsCommand {
    private Log logger = LogFactory.getLog(AS2SenderModule.class.getSimpleName());

    public String getDefaultDescription() {
        return "Add a new partnership definition to partnership store.";
    }

    public String getDefaultName() {
        return "add";
    }

    public String getDefaultUsage() {
        return "add <name> <senderId> <receiverId> [attribute-1=value-1] [attribute-2=value-2] ... [attribute-n=value-n] [pollerConfig.attr1=value1 ... pollerConfig.attrn=valuen]\n"
                + "\t- subject=$receiver.name$ result entries like <attribute name=\"subject\" value=\"File $attributes.filename$ sent from $sender.name$ to $receiver.name$\"/>\n"
                + "\t- pollerConfig.enabled=true polllerConfig.filename=$properties.storageBaseDir$/outbox results in entries like <pollerConfig enabled=\"true\" filename=\"$properties.storageBaseDir$/outbox\"";
    }

    public CommandResult execute(PartnershipFactory partFx, Object[] params) throws OpenAS2Exception {
        if (params.length < 3) {
            return new CommandResult(CommandResult.TYPE_INVALID_PARAM_COUNT, getUsage());
        }

        synchronized (partFx) {
            Document doc;
            try {
                doc = XMLUtil.createDoc(null);
            } catch (Exception e1) {
                throw new OpenAS2Exception(e1);
            }

            Element partnershipRoot = doc.createElement("partnership");
            doc.appendChild(partnershipRoot);
            Element pollerConfigElem = null;

            for (int i = 0; i < params.length; i++) {
                String param = (String) params[i];
                int equalsPos = param.indexOf('=');
                if (i == 0) {
                    partnershipRoot.setAttribute("name", param);
                } else if (i == 1) {
                    Element elem = doc.createElement(Partnership.PCFG_SENDER);
                    elem.setAttribute("name", param);
                    partnershipRoot.appendChild(elem);
                } else if (i == 2) {
                    Element elem = doc.createElement(Partnership.PCFG_RECEIVER);
                    elem.setAttribute("name", param);
                    partnershipRoot.appendChild(elem);
                } else if (equalsPos == 0) {
                    return new CommandResult(CommandResult.TYPE_ERROR, "incoming parameter missing name");
                } else if (equalsPos > 0) {
                    if (param.startsWith("pollerConfig.")) {
                        // Add a pollerConfig element
                        String regex = "^pollerConfig.([^=]*)=((?:[^\"']+)|'(?:[^']*)'|\"(?:[^\"]*)\")";
                        Pattern p = Pattern.compile(regex);
                        Matcher m = p.matcher(param);
                        if (!m.find()) {
                            throw new OpenAS2Exception("Failed to parse the command string: " + param);
                        }
                        String name = m.group(1);
                        String val = m.group(2);
                        if (pollerConfigElem == null) {
                            pollerConfigElem = doc.createElement("pollerConfig");
                        }
                        pollerConfigElem.setAttribute(name, val);
                    } else {
                        Element elem = doc.createElement("attribute");
                        elem.setAttribute("name", param.substring(0, equalsPos));
                        elem.setAttribute("value", param.substring(equalsPos + 1));
                        partnershipRoot.appendChild(elem);
                    }
                } else {
                    return new CommandResult(CommandResult.TYPE_ERROR, "incoming parameter missing value");
                }

            }
            if (pollerConfigElem != null) {
                partnershipRoot.appendChild(pollerConfigElem);
            }

            // Load the partnership into the cached list of partnerships
            try {
                ((XMLPartnershipFactory) partFx).loadPartnership(partFx.getPartners(), partFx.getPartnerships(), partnershipRoot);
            } catch (OpenAS2Exception e) {
                logger.error(e.getMessage(), e);
                return new CommandResult(CommandResult.TYPE_ERROR, "Failed to load new partnership: " + e.getMessage());
            }
            // Add the element to the already loaded partnership XML doc
            ((XMLPartnershipFactory) partFx).addElement(partnershipRoot);
            return new CommandResult(CommandResult.TYPE_OK);
        }
    }
}
