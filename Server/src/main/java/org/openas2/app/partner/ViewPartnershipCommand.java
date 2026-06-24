package org.openas2.app.partner;

import org.openas2.OpenAS2Exception;
import org.openas2.cmd.CommandResult;
import org.openas2.partner.Partnership;
import org.openas2.partner.PartnershipFactory;
import org.openas2.partner.XMLPartnershipFactory;
import org.openas2.util.XMLUtil;

import java.util.Iterator;
import java.util.Map;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * view the partnership in the partnership stores
 *
 * @author Don Hillsberry
 */
public class ViewPartnershipCommand extends AliasedPartnershipsCommand {
    public String getDefaultDescription() {
        return "View the partnership entry in partnership store.";
    }

    public String getDefaultName() {
        return "view";
    }

    public String getDefaultUsage() {
        return "view <name>";
    }

    protected CommandResult execute(PartnershipFactory partFx, Object[] params) throws OpenAS2Exception {
        if (params.length < 1) {
            return new CommandResult(CommandResult.TYPE_INVALID_PARAM_COUNT, getUsage());
        }

        synchronized (partFx) {

            String name = params[0].toString();

            Iterator<Partnership> parts = partFx.getPartnerships().iterator();

            while (parts.hasNext()) {
                Partnership part = parts.next();
                if (part.getName().equals(name)) {
                    // Try to extract and append pollerConfig from the partnerships XML if available
                    try {
                        XMLPartnershipFactory xpf = (XMLPartnershipFactory) partFx;
                        Document doc = xpf.getPartnershipsXml();
                        if (doc != null) {
                            Node root = doc.getDocumentElement();
                            NodeList nodes = root.getChildNodes();
                            for (int i = 0; i < nodes.getLength(); i++) {
                                Node n = nodes.item(i);
                                if (!"partnership".equals(n.getNodeName())) {
                                    continue;
                                }
                                Node nameAttr = n.getAttributes().getNamedItem("name");
                                if (nameAttr != null && nameAttr.getNodeValue().equals(part.getName())) {
                                    Node poller = XMLUtil.findChildNode(n, Partnership.PCFG_POLLER);
                                    if (poller != null) {
                                        Map<String, String> pollerAttrs = XMLUtil.mapAttributes(poller);
                                        // Append pollerConfig attributes to partnership attributes
                                        pollerAttrs.forEach((key, value) -> {
                                            part.getAttributes().put("pollerConfig." + key, value);
                                        });
                                    }
                                    break;
                                }
                            }
                        }
                    } catch (Exception e) {
                        // ignore extraction errors and proceed without pollerConfig
                    }
                    return new CommandResult(CommandResult.TYPE_OK, part);
                }
            }
            
            return new CommandResult(CommandResult.TYPE_ERROR, "Unknown partnership name");
        }

    }
}
