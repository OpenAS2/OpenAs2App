package org.openas2.app;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openas2.message.AS2Message;
import org.openas2.message.FileAttribute;
import org.openas2.partner.Partnership;
import org.openas2.partner.PartnershipFactory;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

@ExtendWith(MockitoExtension.class)
@TestInstance(Lifecycle.PER_CLASS)
public class FilenameParsingTest extends BaseServerSetup {
    private static String testFileNamePart1 = "abc";
    private static String testFileNamePart2 = "123";
    private static String testFileName = testFileNamePart1 + "-" + testFileNamePart2 + ".txt";
    private static String fileNameRegex = "([^-]*)-([^.]*).txt";
    private static String attribNamesFromFileName1 = "X-attribute1";
    private static String attribNamesFromFileName2 = "Y-attribute2";
    private static String attribNamesFromFileName = attribNamesFromFileName1 + "," + attribNamesFromFileName2;
    private static String subjectAttrib = "First part filename: $attributes." + attribNamesFromFileName1 + "$  Second part filename: $attributes." + attribNamesFromFileName2 + "$";
    private static String expectedSubject = "First part filename: " + testFileNamePart1 + "  Second part filename: " + testFileNamePart2;


    @BeforeAll
    public void setup() throws Exception {
    	super.createFileSystemResources();
    	super.setup();
        try {
            simpleTestMsg = new AS2Message();
            simpleTestMsg.setAttribute(FileAttribute.MA_FILENAME, testFileName);
            PartnershipFactory pf = session.getPartnershipFactory();
            Partnership myPartnership = simpleTestMsg.getPartnership();
            myPartnership.setSenderID(Partnership.PID_AS2, BaseServerSetup.myCompanyOid);
            myPartnership.setReceiverID(Partnership.PID_AS2, BaseServerSetup.myPartnerOid);
            myPartnership.setSenderID(Partnership.PID_AS2, BaseServerSetup.myCompanyOid);


            Partnership configuredPartnership = pf.getPartnership(myPartnership, false);
            configuredPartnership.setAttribute(Partnership.PAIB_VALUES_REGEX_ON_FILENAME, fileNameRegex);
            configuredPartnership.setAttribute(Partnership.PAIB_NAMES_FROM_FILENAME, attribNamesFromFileName);
            configuredPartnership.setAttribute(Partnership.PA_SUBJECT, subjectAttrib);
            // update the message's partnership with any stored information
            pf.updatePartnership(simpleTestMsg, true);

        } catch (Throwable e) {
            // aid for debugging JUnit tests
            System.err.println("ERROR occurred: " + ExceptionUtils.getStackTrace(e));
            throw new Exception(e);
        }
    }

    @AfterAll
    public void tearDown() throws Exception {
        super.tearDown();;
    }

    @Test
    public void shouldHaveAddedAttributes() throws Exception {
        assertThat("Verify 1st attribute added from filename", simpleTestMsg.getAttribute(attribNamesFromFileName1), equalTo(testFileNamePart1));
        assertThat("Verify 2nd attribute added from filename", simpleTestMsg.getAttribute(attribNamesFromFileName2), equalTo(testFileNamePart2));
        assertThat("Verify message subject", simpleTestMsg.getSubject(), equalTo(expectedSubject));
    }

}
