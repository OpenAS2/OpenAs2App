package org.openas2.app;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.io.TempDir;
import org.openas2.TestResource;
import org.openas2.XMLSession;
import org.openas2.message.AS2Message;
import org.openas2.message.FileAttribute;
import org.openas2.message.Message;
import org.openas2.partner.Partnership;
import org.openas2.processor.receiver.api.AS2FileReceiverModule;
import org.openas2.util.Properties;


public class BaseServerSetup {
    static String resourcePathPrefix = Paths.get("src","test","resources").toAbsolutePath().toString();
    static Path srcConfigDirPath = Paths.get(resourcePathPrefix + File.separator + "config");

    static String myCompanyOid = "MyCompany_OID";
    static String myPartnerOid = "PartnerA_OID";
    private boolean startActiveModules = false;

    protected static XMLSession session;
    protected static Message simpleTestMsg;

    @TempDir
    public static File configDir;
    public File openAS2PropertiesFile;

    public void refresh() throws Exception {
        session.stop();
        setup();
    }

    public AS2Message  getSimpleTestMsg() throws Exception {
        AS2Message msg = new AS2Message();
        Partnership myPartnership = msg.getPartnership();
        myPartnership.setSenderID(Partnership.PID_AS2, myCompanyOid);
        myPartnership.setReceiverID(Partnership.PID_AS2, myPartnerOid);
        myPartnership.setSenderID(Partnership.PID_AS2, myCompanyOid);
        session.getPartnershipFactory().updatePartnership(msg, true);
        return msg;
    }

    public void addSendPayloadStuffToMsg(String fileName, Message msg) throws Exception {
        AS2FileReceiverModule frm = new AS2FileReceiverModule();
        msg.setAttribute(FileAttribute.MA_FILENAME, fileName);
        msg.setHeader("AS2-To", msg.getPartnership().getReceiverID(Partnership.PID_AS2));
        msg.setHeader("AS2-From", msg.getPartnership().getSenderID(Partnership.PID_AS2));
        msg.updateMessageID();
        File testFile = new File(configDir, fileName);
        FileUtils.writeStringToFile(testFile, "Show me the money!", Charset.forName("UTF-8"));
        frm.buildMessageData(msg, testFile, null);
    }

    public void setStartActiveModules(boolean startActiveModules) {
        this.startActiveModules = startActiveModules;
    }

    public void createFileSystemResources(String configDirName) throws Exception {
        Path destConfigDirPath = Files.createTempDirectory(configDirName);
        configDir = destConfigDirPath.toFile();
        openAS2PropertiesFile = new File(configDir, "test.openas2.properties");
        // Just in case there is an existing file...
        if (openAS2PropertiesFile.exists()) openAS2PropertiesFile.delete();
        // Copy standard config resources to this folder
        this.copyConfig(destConfigDirPath.toString());
    }

    public void setup() throws Exception {
        try {
            //System.setProperty("OPENAS2_LOG_LEVEL", "trace");
            if (openAS2PropertiesFile.exists()) {
                System.setProperty(Properties.OPENAS2_PROPERTIES_FILE_PROP, openAS2PropertiesFile.getAbsolutePath());
            }
            session = new XMLSession(configDir.getAbsolutePath() + "/config.xml");
            simpleTestMsg = getSimpleTestMsg();
            if (startActiveModules) {
                session.start();
            }
        } catch (Throwable e) {
            // aid for debugging JUnit tests
            System.err.println("ERROR occurred: " + ExceptionUtils.getStackTrace(e));
            throw new Exception(e);
        }
    }

    /**
     * Copy standard config files from {@link #resourcePathPrefix} to the target config folder
     * @throws IOException 
     *
     */
    public void copyConfig(String destDir) throws IOException {
        if (!srcConfigDirPath.toFile().exists()) {
            throw new FileNotFoundException("The source config was not found here: " + srcConfigDirPath.getFileName());
        }
        for (String f : srcConfigDirPath.toFile().list()) {
            Files.copy(Paths.get(srcConfigDirPath.toString(), f), Paths.get(destDir, f), StandardCopyOption.REPLACE_EXISTING);
        }
    }


    @AfterAll
    public void tearDown() throws Exception {
        session.stop();
        openAS2PropertiesFile.delete();
        System.clearProperty(Properties.OPENAS2_PROPERTIES_FILE_PROP);
    }

}
