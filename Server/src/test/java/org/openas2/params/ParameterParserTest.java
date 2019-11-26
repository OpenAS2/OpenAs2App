package org.openas2.params;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.openas2.message.AS2Message;
import org.openas2.message.MessageMDN;
import org.openas2.partner.Partnership;
import org.openas2.util.Properties;
import org.openas2.util.StringUtil;

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ParameterParserTest {

    private static final String senderId = "MySenderId";
    private static final String receiverId = "MyReceiverId";
    private static final String prefix = "PARSER@";
    private static final String homeParam = "%home%";
    private static final String homeValue = "/My/Home/Dir";
    // Parameter string and regex test for various params
    private String[][] positiveTests = {{prefix + "$msg.sender." + Partnership.PID_AS2 + "$_$msg.receiver." + Partnership.PID_AS2 + "$", prefix + senderId + "_" + receiverId}, {"$mdn.msg.sender." + Partnership.PID_AS2 + "$", senderId}, {"$mdn.sender." + Partnership.PID_AS2 + "$", senderId}, {"$date.yyyyMMddHHmmssZ$", "[0-9]{14}[-+][0-9]{4}"}, {"$rand.1234$-$rand.UUID$-$rand.shortUUID$", "[0-9]{4}-[a-fA-F0-9-]{36}-[a-zA-Z0-9]*"}, {prefix + "$$$rand.12345$", prefix + "\\$[0-9]{5}"}, {homeParam + "/$rand.12345$", homeValue + "/[0-9]{5}"}};
    private String[][] negativeTests = {{prefix + "$sender$", prefix + senderId}, {"$dates.yyyyMMddHHmmssZ$", "[0-9]{14}[-+][0-9]{4}"}, {"$rand.12345$", "[0-9]{4}"}};
    @Mock
    private AS2Message message;
    @Mock
    private MessageMDN mdn;
    @Mock
    private Partnership partnership;

    @Before
    public void setUp() throws Exception {
        when(message.getMDN()).thenReturn(mdn);
        when(mdn.getMessage()).thenReturn(message);
        when(message.getPartnership()).thenReturn(partnership);
        when(mdn.getPartnership()).thenReturn(partnership);
        when(partnership.getReceiverID(eq(Partnership.PID_AS2))).thenReturn(receiverId);
        when(partnership.getSenderID(eq(Partnership.PID_AS2))).thenReturn(senderId);
        Properties.setProperty(Properties.APP_BASE_DIR_PROP, homeValue);
    }

    @Test
    public void shouldParseParameterString() throws Exception {
        for (String[] strings : positiveTests) {
            String parsedString = StringUtil.parseParameterisedString(strings[0], message);
            assertThat("Check " + strings[1] + " against " + parsedString, parsedString.matches(strings[1]), is(true));
        }
        for (String[] strings : negativeTests) {
            String parsedString;
            try {
                parsedString = StringUtil.parseParameterisedString(strings[0], message);
            } catch (InvalidParameterException e) {
                parsedString = strings[0];
            }
            assertThat("Check " + strings[0] + " against " + parsedString, parsedString.matches(strings[1]), is(false));
        }
    }
}
