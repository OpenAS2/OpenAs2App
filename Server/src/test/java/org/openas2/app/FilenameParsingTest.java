package org.openas2.app;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.openas2.TestResource;
import org.openas2.XMLSession;
import org.openas2.message.AS2Message;
import org.openas2.message.FileAttribute;
import org.openas2.message.Message;
import org.openas2.partner.Partnership;
import org.openas2.partner.PartnershipFactory;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertThat;

@RunWith(MockitoJUnitRunner.class)

public class FilenameParsingTest {
    private static final TestResource RESOURCE = TestResource.forGroup("SingleServerTest");
    private static String myCompanyOid = "MyCompany_OID";
    private static String myPartnerOid = "PartnerA_OID";
    private static String testFileNamePart1 = "abc";
    private static String testFileNamePart2 = "123";
    private static String testFileName = testFileNamePart1 + "-" + testFileNamePart2 + ".txt";
    private static String fileNameRegex = "([^-]*)-([^.]*).txt";
    private static String attribNamesFromFileName1 = "X-attribute1";
    private static String attribNamesFromFileName2 = "Y-attribute2";
    private static String attribNamesFromFileName = attribNamesFromFileName1 + "," + attribNamesFromFileName2;
    private static String subjectAttrib = "First part filename: $attributes." + attribNamesFromFileName1 + "$  Second part filename: $attributes." + attribNamesFromFileName2 + "$";
    private static String expectedSubject = "First part filename: " + testFileNamePart1 + "  Second part filename: " + testFileNamePart2;

    // private static File openAS2AHome;
    private static XMLSession session;
    private static Message msg;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @BeforeClass
    public static void setup() throws Exception {
        try {
            System.setProperty("org.apache.commons.logging.Log", "org.openas2.logging.Log");
            //System.setProperty("org.openas2.logging.defaultlog", "TRACE");
            FilenameParsingTest.session = new XMLSession(RESOURCE.get("MyCompany", "config", "config.xml").getAbsolutePath());
            msg = new AS2Message();
            msg.setAttribute(FileAttribute.MA_FILENAME, testFileName);
            PartnershipFactory pf = session.getPartnershipFactory();
            Partnership myPartnership = msg.getPartnership();
            myPartnership.setSenderID(Partnership.PID_AS2, myCompanyOid);
            myPartnership.setReceiverID(Partnership.PID_AS2, myPartnerOid);
            myPartnership.setSenderID(Partnership.PID_AS2, myCompanyOid);


            Partnership configuredPartnership = pf.getPartnership(myPartnership, false);
            configuredPartnership.setAttribute(Partnership.PAIB_VALUES_REGEX_ON_FILENAME, fileNameRegex);
            configuredPartnership.setAttribute(Partnership.PAIB_NAMES_FROM_FILENAME, attribNamesFromFileName);
            configuredPartnership.setAttribute(Partnership.PA_SUBJECT, subjectAttrib);
            // update the message's partnership with any stored information
            pf.updatePartnership(msg, true);

        } catch (Throwable e) {
            // aid for debugging JUnit tests
            System.err.println("ERROR occurred: " + ExceptionUtils.getStackTrace(e));
            throw new Exception(e);
        }
    }

    @AfterClass
    public static void tearDown() throws Exception {
        session = null;
    }

    @Test
    public void shouldHaveAddedAttributes() throws Exception {
        assertThat("Verify 1st attribute added from filename", msg.getAttribute(attribNamesFromFileName1), equalTo(testFileNamePart1));
        assertThat("Verify 2nd attribute added from filename", msg.getAttribute(attribNamesFromFileName2), equalTo(testFileNamePart2));
        assertThat("Verify message subject", msg.getSubject(), equalTo(expectedSubject));
    }

}
