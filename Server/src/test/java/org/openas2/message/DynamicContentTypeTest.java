package org.openas2.message;

import org.mockito.junit.jupiter.MockitoExtension;
import org.openas2.app.BaserServerSetup;
import org.openas2.partner.Partnership;
import org.openas2.processor.receiver.DirectoryPollingModule;
import org.openas2.util.Properties;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.hamcrest.MatcherAssert.assertThat;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class DynamicContentTypeTest extends BaserServerSetup {
    private DirectoryPollingModule poller;
    
    public static File systemContentTypesMappingFile;
    public static File partnershipContentTypesMappingFile;

    private final static String unmappedFileExtension = "data";
    private final static String xmlFileExtension = "xml";
    private final static String ediFileExtension = "edi";
    java.util.Properties contentTypeMap = Properties.getContentTypeMap();
    private Map<String, String> systemMappedContentTypes = new HashMap<String, String>();
    private Map<String, String> partnershipMappedContentTypes = new HashMap<String, String>();


    @BeforeAll
    public void setUp() throws Exception {
        super.createFileSystemResources();
        // Set up the system level mappings
        systemContentTypesMappingFile = new File(tmpDir, "content_type_map.properties");
        systemMappedContentTypes.put(xmlFileExtension,  "application/xml");
        systemMappedContentTypes.put(ediFileExtension,  "application/edifact");
        systemMappedContentTypes.put("txt",  "text/plain");
        BufferedWriter writer = new BufferedWriter(new FileWriter(systemContentTypesMappingFile));
        for (Map.Entry<String, String> entry : systemMappedContentTypes.entrySet()) {
            writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
        }  
        writer.close();
        // Set up the partnership override mappings
        partnershipContentTypesMappingFile = new File(tmpDir, "override_content_type_map.properties");
        partnershipMappedContentTypes.put(xmlFileExtension,  "application/xml-custom");
        BufferedWriter writer2 = new BufferedWriter(new FileWriter(partnershipContentTypesMappingFile));
        for (Map.Entry<String, String> entry : partnershipMappedContentTypes.entrySet()) {
            writer2.write(entry.getKey() + "=" + entry.getValue() + "\n");
        }  
        writer2.close();
        super.setup();
        this.poller = session.getPartnershipPoller(msg.getPartnership().getName());
    }

    @AfterAll
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void a1_shouldHaveNoMappingEnabled() throws Exception {
        // The default is to not have dynamic mapping so check
        Partnership myPartnership = msg.getPartnership();
        assertFalse(myPartnership.isUseDynamicContentTypeLookup(), "Check default is mapping off.");
    }


    @Test
    public void a2_shouldFailNoMapping() throws Exception {
        // Make sure that there is an error if no mapping file defined but trying to use mapping
        Partnership myPartnership = msg.getPartnership();
        assertThrows(Exception.class, () -> { myPartnership.setUseDynamicContentTypeLookup(true);}, "No config for Content-Type mapping should throw exception.");
    }


    @Test
    public void b_shouldGetDefaultContentType() throws Exception {
        // Partnership not set for dynamic mapping so should return system poller default
        String testFilename = "random." + ediFileExtension;
        poller.addMessageMetadata(msg, testFilename);
        assertThat("Check default Content-Type returned when no mapping.", poller.getMessageContentType(msg).matches(Properties.getProperty("pollerConfigBase.mimetype", "FakeValue")), is(true));
    }

    @Test
    public void c_shouldGetPartnershipMappedContentTypeWhenNoSystemMapping() throws Exception {
        // Set the partnership to have dynamic mapping file
        msg.getPartnership().setAttribute(Partnership.PA_CONTENT_TYPE_MAPPING_FILE, partnershipContentTypesMappingFile.getAbsolutePath());
        // Force load the partnership mapping
        msg.getPartnership().setUseDynamicContentTypeLookup(true);
        // Now check that we get the system property when no override and override when set
        String testFilename = "random." + ediFileExtension;
        poller.addMessageMetadata(msg, testFilename);
        assertThat("Check system default Content-Type returned when not overridden.", poller.getMessageContentType(msg).matches(Properties.getProperty("pollerConfigBase.mimetype", "FakeValue")), is(true));
        testFilename = "random." + xmlFileExtension;
        poller.addMessageMetadata(msg, testFilename);
        assertThat("Check partnership mapped Content-Type returned when partnership mapping setup.", poller.getMessageContentType(msg).matches(partnershipMappedContentTypes.get(xmlFileExtension)), is(true));
        testFilename = "random." + unmappedFileExtension;
        poller.addMessageMetadata(msg, testFilename);
        assertThat("Check system mapped default Content-Type returned when no system or partnership mapping defined.", poller.getMessageContentType(msg).matches(Properties.getProperty("pollerConfigBase.mimetype", "FakeValue")), is(true));
    }

    @Test
    public void d_shouldGetSystemMappedContentType() throws Exception {
        // Append the mapping file property to custom load properties
        BufferedWriter propsWriter = new BufferedWriter(new FileWriter(super.openAS2PropertiesFile, true));
        String propVal = systemContentTypesMappingFile.getAbsolutePath().replace("\\", "\\\\");
        propsWriter.write("\n" + Partnership.PA_CONTENT_TYPE_MAPPING_FILE + "=" + propVal);
        propsWriter.close();
        // Now reload the session to get new properties file that then loads system mapping
        super.refresh();
        // Force the partnership to have dynamic mapping enabled
        msg.getPartnership().setUseDynamicContentTypeLookup(true);
        String testFilename = "random." + ediFileExtension;
        poller.addMessageMetadata(msg, testFilename);
        assertThat("Check system mapped Content-Type returned when system mapping setup.", poller.getMessageContentType(msg).matches(systemMappedContentTypes.get(ediFileExtension)), is(true));
        testFilename = "random." + xmlFileExtension;
        poller.addMessageMetadata(msg, testFilename);
        assertThat("Check system mapped Content-Type returned when system mapping setup.", poller.getMessageContentType(msg).matches(systemMappedContentTypes.get(xmlFileExtension)), is(true));
        testFilename = "random." + unmappedFileExtension;
        poller.addMessageMetadata(msg, testFilename);
        assertThat("Check system mapped Content-Type returned when system mapping setup.", poller.getMessageContentType(msg).matches(Properties.getProperty("pollerConfigBase.mimetype", "FakeValue")), is(true));
    }

    @Test
    public void e_shouldGetPartnershipOverrideMappedContentType() throws Exception {
        // Append the property to globally enable dynamic mapping in custom load properties
        BufferedWriter propsWriter = new BufferedWriter(new FileWriter(super.openAS2PropertiesFile, true));
        propsWriter.write("\n" + Partnership.PA_USE_DYNAMIC_CONTENT_TYPE_MAPPING + "=true");
        propsWriter.close();
        // Now reload the session to get new properties file that then loads system mapping
        super.refresh();
        // Set the partnership to have dynamic mapping file
        msg.getPartnership().setAttribute(Partnership.PA_CONTENT_TYPE_MAPPING_FILE, partnershipContentTypesMappingFile.getAbsolutePath());
        // Force load the override
        msg.getPartnership().setUseDynamicContentTypeLookup(true);
        // Now check that we get the system property when no override and override when set
        String testFilename = "random." + ediFileExtension;
        poller.addMessageMetadata(msg, testFilename);
        assertThat("Check system mapped Content-Type returned when not overridden.", poller.getMessageContentType(msg).matches(systemMappedContentTypes.get(ediFileExtension)), is(true));
        testFilename = "random." + xmlFileExtension;
        poller.addMessageMetadata(msg, testFilename);
        assertThat("Check partnership mapped Content-Type returned when partnership mapping setup.", poller.getMessageContentType(msg).matches(partnershipMappedContentTypes.get(xmlFileExtension)), is(true));
        testFilename = "random." + unmappedFileExtension;
        poller.addMessageMetadata(msg, testFilename);
        assertThat("Check system mapped default Content-Type returned when no system or partnership mapping defined.", poller.getMessageContentType(msg).matches(Properties.getProperty("pollerConfigBase.mimetype", "FakeValue")), is(true));
    }
}
