package org.openas2.message;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.exceptions.misusing.InvalidUseOfMatchersException;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.openas2.partner.Partnership;
import org.openas2.util.Properties;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AS2MessageMDNTest {

    @Mock
    private AS2Message message;
    @Mock
    private Partnership partnership;

    private static final String msgIdFmt = "OPENAS2-$date.ddMMyyyyHHmmssZ$-$rand.1234$@$msg.sender.as2_id$_$msg.receiver.as2_id$";
    private static final String msgIdMatcher = "^<OPENAS2-[0-9]{14}[-+][0-9]{4}-[0-9]{4}@senderId_receiverId>";
    private static final String msgIdMdnFmt = "MDN-$date.ddMMyyyyHHmmssZ$-$rand.1234$@$msg.sender.as2_id$_$msg.receiver.as2_id$";
    private static final String msgIdMdnMatcher = "^<MDN-[0-9]{14}[-+][0-9]{4}-[0-9]{4}@senderId_receiverId>";

    private boolean returnMdnFmt = false;

    @Before
    public void setUp() throws Exception {
        when(message.getPartnership()).thenReturn(partnership);
        when(partnership.getReceiverID(matches(Partnership.PID_AS2))).thenReturn("receiverId");
        when(partnership.getSenderID(matches(Partnership.PID_AS2))).thenReturn("senderId");
        when(message.getPartnership().getAttributeOrProperty(anyString(), any())).thenAnswer(new Answer<Object>() {
            @Override
            public Object answer(InvocationOnMock invocation) throws Throwable {
                Object argument = invocation.getArguments()[0];
                if (argument.toString().equals(Properties.AS2_MDN_MESSAGE_ID_FORMAT)) {
                    if (returnMdnFmt) {
                        return msgIdMdnFmt;
                    } else {
                        return null;
                    }
                } else if (argument.toString().equals(Properties.AS2_MESSAGE_ID_FORMAT)) {
                    return msgIdFmt;
                } else if (argument.toString().equals(Properties.AS2_MESSAGE_ID_ENCLOSE_IN_BRACKETS)) {
                    return "true";
                }
                throw new InvalidUseOfMatchersException(String.format("Argument %s does not match", argument));
            }
        });
    }

    @Test
    public void shouldGenerateMessageId() throws Exception {
        returnMdnFmt = false;
        String messageId = new AS2MessageMDN(message, false).generateMessageID();
        assertThat("Check " + messageId, messageId.matches(msgIdMatcher), is(true));
    }


    @Test
    public void shouldGenerateMdnMessageId() throws Exception {
        returnMdnFmt = true;
        String messageId = new AS2MessageMDN(message, false).generateMessageID();
        assertThat("Check " + messageId, messageId.matches(msgIdMdnMatcher), is(true));
    }
}
