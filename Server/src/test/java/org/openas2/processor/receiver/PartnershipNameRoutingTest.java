package org.openas2.processor.receiver;

import org.junit.jupiter.api.Test;
import org.openas2.Session;
import org.openas2.message.Message;
import org.openas2.partner.Partnership;
import org.openas2.partner.XMLPartnershipFactory;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies that a partnership poller resolves the partnership it was declared in by name, so that
 * several partnerships between the same sender and receiver AS2 IDs can each send with their own
 * configuration instead of all using the first partnership that matches the ID pair.
 */
public class PartnershipNameRoutingTest {

    private static final String DEFAULTS = "sender.as2_id=MyCompany_OID, receiver.as2_id=PartnerA_OID";

    private static Partnership partnership(String name, String subject) {
        Partnership p = new Partnership();
        p.setName(name);
        p.setSenderID(Partnership.PID_AS2, "MyCompany_OID");
        p.setReceiverID(Partnership.PID_AS2, "PartnerA_OID");
        p.setAttribute(Partnership.PA_SUBJECT, subject);
        return p;
    }

    private static AS2DirectoryPollingModule poller(String partnershipName) {
        XMLPartnershipFactory factory = new XMLPartnershipFactory();
        List<Partnership> partnerships = new ArrayList<Partnership>();
        partnerships.add(partnership("MyCompany-to-PartnerA", "first config"));
        partnerships.add(partnership("MyCompany-to-PartnerA-alt", "second config"));
        factory.setPartnerships(partnerships);

        Session session = (Session) Proxy.newProxyInstance(Session.class.getClassLoader(), new Class<?>[]{Session.class},
                (proxy, method, args) -> "getPartnershipFactory".equals(method.getName()) ? factory : null);

        AS2DirectoryPollingModule module = new AS2DirectoryPollingModule() {
            @Override
            public Session getSession() {
                return session;
            }
        };
        module.setParameter(MessageBuilderModule.PARAM_DEFAULTS, DEFAULTS);
        if (partnershipName != null) {
            module.setParameter(MessageBuilderModule.PARAM_PARTNERSHIP_NAME, partnershipName);
        }
        return module;
    }

    @Test
    public void pollerUsesItsOwnPartnershipWhenAnotherSharesTheSameIds() throws Exception {
        Message msg = poller("MyCompany-to-PartnerA-alt").buildBaseMessage("test.edi");

        assertEquals("MyCompany-to-PartnerA-alt", msg.getPartnership().getName());
        assertEquals("second config", msg.getSubject());
    }

    @Test
    public void pollerWithoutPartnershipNameStillResolvesByIds() throws Exception {
        Message msg = poller(null).buildBaseMessage("test.edi");

        assertEquals("MyCompany-to-PartnerA", msg.getPartnership().getName());
        assertEquals("first config", msg.getSubject());
    }

    @Test
    public void unknownPartnershipNameFallsBackToIdLookup() throws Exception {
        Message msg = poller("no-such-partnership").buildBaseMessage("test.edi");

        assertEquals("MyCompany-to-PartnerA", msg.getPartnership().getName());
    }
}
