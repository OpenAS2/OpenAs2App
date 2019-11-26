package org.openas2.app;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.openas2.message.AS2Message;
import org.openas2.message.Message;
import org.openas2.partner.Partnership;
import org.openas2.util.HTTPUtil;

import javax.mail.internet.InternetHeaders;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

@RunWith(MockitoJUnitRunner.class)

public class HTTPUtilTest {

    @Test
    public void handlesMultiEntryAS2IdWithSpacesAndQuotes() {
        String fromIdKey = "AS2-From";
        String toIdKey = "AS2-To";
        String fromId = "Thats You";
        String toId = "Thats Me";

        Message msg = new AS2Message();
        InternetHeaders hdrs = new InternetHeaders();
        msg.setHeaders(hdrs);
        hdrs.addHeader(fromIdKey, fromId);
        hdrs.addHeader(fromIdKey, "\"" + fromId + "\"");
        hdrs.addHeader(fromIdKey, fromId);
        hdrs.addHeader(toIdKey, toId);
        hdrs.addHeader(toIdKey, "\"" + toId + "\"");
        HTTPUtil.cleanIdHeaders(msg.getHeaders());
        msg.getPartnership().setSenderID(Partnership.PID_AS2, msg.getHeader(fromIdKey));
        msg.getPartnership().setReceiverID(Partnership.PID_AS2, msg.getHeader(toIdKey));
        assertThat("Duplicate FROM headers have been removed", msg.getPartnership().getSenderID(Partnership.PID_AS2), equalTo(fromId));
        assertThat("Duplicate TO headers have been removed", msg.getPartnership().getReceiverID(Partnership.PID_AS2), equalTo(toId));
    }
}
