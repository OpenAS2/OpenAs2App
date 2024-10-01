package org.openas2.app;

import java.io.File;
import java.nio.file.Files;

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.openas2.TestResource;
import org.openas2.XMLSession;
import org.openas2.message.AS2Message;
import org.openas2.message.Message;
import org.openas2.partner.Partnership;
import org.openas2.util.Properties;


public class BaserServerSetup {
    private static final TestResource RESOURCE = TestResource.forGroup("SingleServerTest");
    static String myCompanyOid = "MyCompany_OID";
    static String myPartnerOid = "PartnerA_OID";

    // private static File openAS2AHome;
    protected static XMLSession session;
    protected static Message msg;

    @TempDir
    public static File tmpDir;
    public File openAS2PropertiesFile;

    public void refresh() throws Exception {
        session.stop();
        setup();
    }

    public void createFileSystemResources() throws Exception {
        tmpDir = Files.createTempDirectory("testResources").toFile();
        openAS2PropertiesFile = new File(tmpDir, "test.openas2.properties");
    }

    public void setup() throws Exception {
        try {
            System.setProperty("org.apache.commons.logging.Log", "org.openas2.logging.Log");
            //System.setProperty("org.openas2.logging.defaultlog", "TRACE");
            if (openAS2PropertiesFile.exists()) {
                System.setProperty(Properties.OPENAS2_PROPERTIES_FILE_PROP, openAS2PropertiesFile.getAbsolutePath());
            }
            session = new XMLSession(RESOURCE.get("MyCompany", "config", "config.xml").getAbsolutePath());
            msg = new AS2Message();
            Partnership myPartnership = msg.getPartnership();
            myPartnership.setSenderID(Partnership.PID_AS2, myCompanyOid);
            myPartnership.setReceiverID(Partnership.PID_AS2, myPartnerOid);
            myPartnership.setSenderID(Partnership.PID_AS2, myCompanyOid);
            session.getPartnershipFactory().updatePartnership(msg, true);
        } catch (Throwable e) {
            // aid for debugging JUnit tests
            System.err.println("ERROR occurred: " + ExceptionUtils.getStackTrace(e));
            throw new Exception(e);
        }
    }

    @AfterAll
    public void tearDown() throws Exception {
        session.stop();
        openAS2PropertiesFile.delete();
        System.clearProperty(Properties.OPENAS2_PROPERTIES_FILE_PROP);
    }

}
