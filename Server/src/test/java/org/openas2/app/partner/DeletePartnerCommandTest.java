package org.openas2.app.partner;

import org.junit.jupiter.api.Test;
import org.openas2.cmd.CommandResult;
import org.openas2.partner.XMLPartnershipFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies DeletePartnerCommand reports the correct result. In particular, when the partner exists
 * in memory but its removal from the partnerships XML fails, the command must return an error rather
 * than silently reporting success.
 */
public class DeletePartnerCommandTest {

    private XMLPartnershipFactory factoryWithPartner(String partnerName, boolean alsoInXml) throws Exception {
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        Element root = doc.createElement("partnerships");
        doc.appendChild(root);
        if (alsoInXml) {
            Element partner = doc.createElement("partner");
            partner.setAttribute("name", partnerName);
            root.appendChild(partner);
        }
        XMLPartnershipFactory factory = new XMLPartnershipFactory();
        factory.setPartnershipsXml(doc);
        factory.getPartners().put(partnerName, new HashMap<String, Object>());
        return factory;
    }

    @Test
    public void returnsErrorWhenXmlDeleteFails() throws Exception {
        // Partner is in the in-memory map but has no node in the XML document, so deleteElement fails.
        XMLPartnershipFactory factory = factoryWithPartner("ghost", false);

        CommandResult result = new DeletePartnerCommand().execute(factory, new Object[]{"ghost"});

        assertEquals(CommandResult.TYPE_ERROR, result.getType(),
                "a failed XML delete must be reported as an error, not silent success");
    }

    @Test
    public void returnsOkWhenPartnerIsDeleted() throws Exception {
        XMLPartnershipFactory factory = factoryWithPartner("realpartner", true);

        CommandResult result = new DeletePartnerCommand().execute(factory, new Object[]{"realpartner"});

        assertEquals(CommandResult.TYPE_OK, result.getType());
    }

    @Test
    public void returnsErrorForUnknownPartner() throws Exception {
        XMLPartnershipFactory factory = factoryWithPartner("known", true);

        CommandResult result = new DeletePartnerCommand().execute(factory, new Object[]{"does-not-exist"});

        assertEquals(CommandResult.TYPE_ERROR, result.getType());
    }
}
