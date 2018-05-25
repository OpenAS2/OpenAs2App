package org.openas2.message;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.openas2.partner.AS2Partnership;
import org.openas2.partner.Partnership;
import org.openas2.util.Properties;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AS2MessageMDNTest {

    @Mock
    private AS2Message message;
    @Mock
    private Partnership partnership;

    @Before
    public void setUp() throws Exception
    {
        when(message.getPartnership()).thenReturn(partnership);
        when(partnership.getReceiverID(eq(AS2Partnership.PID_AS2))).thenReturn("receiverId");
        when(partnership.getSenderID(eq(AS2Partnership.PID_AS2))).thenReturn("senderId");
        when(message.getPartnership().getAttribute(eq(Properties.AS2_MESSAGE_ID_FORMAT)))
        .thenReturn("OPENAS2-$date.ddMMyyyyHHmmssZ$-$rand.1234$@$msg.sender.as2_id$_$msg.receiver.as2_id$");
    }

    @Test
    public void shouldGenerateMessageId() throws Exception
    {
        String messageId = new AS2MessageMDN(message, false).generateMessageID();
        assertThat("Check " + messageId, messageId.matches("^OPENAS2-[0-9]{14}[-+][0-9]{4}-[0-9]{4}@senderId_receiverId"), is(true));
    }
}
