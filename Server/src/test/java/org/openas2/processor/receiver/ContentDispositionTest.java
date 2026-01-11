package org.openas2.processor.receiver;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import jakarta.mail.internet.MimeBodyPart;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.junit.jupiter.api.TestMethodOrder;
import org.openas2.OpenAS2Exception;
import org.openas2.app.BaseServerSetup;
import org.openas2.message.FileAttribute;
import org.openas2.partner.Partnership;

@TestInstance(Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.MethodName.class)
public class ContentDispositionTest extends BaseServerSetup {
    private DirectoryPollingModule poller;
    
    @BeforeAll
    public void setUp() throws Exception {
        super.createFileSystemResources();
        super.setup();
        this.poller = session.getPartnershipPoller(simpleTestMsg.getPartnership().getName());
    }

    @AfterAll
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void shouldUseSettingFromPartnership() throws Exception {

        // Set the partnership to disable quotes for the filename
        assertThat(getContentDispositionForQuoteConfiguration("false"), is("Attachment; filename=filename.ext"));
        assertThat(getContentDispositionForQuoteConfiguration("FALSE"), is("Attachment; filename=filename.ext"));
        assertThat(getContentDispositionForQuoteConfiguration("False"), is("Attachment; filename=filename.ext"));

        // Set the partnership to enable quotes for the filename
        assertThat(getContentDispositionForQuoteConfiguration("true"), is("Attachment; filename=\"filename.ext\""));
        assertThat(getContentDispositionForQuoteConfiguration("TRUE"), is("Attachment; filename=\"filename.ext\""));
        assertThat(getContentDispositionForQuoteConfiguration("tRuE"), is("Attachment; filename=\"filename.ext\""));
    }

    @Test
    public void shouldUseDefaultSetting() throws Exception {
        
        //Check backward compatible configuration
        assertThat(getContentDispositionForQuoteConfiguration(""), is("Attachment; filename=\"filename.ext\""));
        assertThat(getContentDispositionForQuoteConfiguration(null), is("Attachment; filename=\"filename.ext\""));
        assertThat(getContentDispositionForQuoteConfiguration("other"), is("Attachment; filename=\"filename.ext\""));
    }

    private String getContentDispositionForQuoteConfiguration(String valueOfQuoteSendFileName) throws OpenAS2Exception {
        
        if (valueOfQuoteSendFileName != null) {
            simpleTestMsg.getPartnership().setAttribute(Partnership.PA_QUOTE_SEND_FILE_NAME, valueOfQuoteSendFileName);
        }

        simpleTestMsg.setAttribute(FileAttribute.MA_FILENAME, "filename.ext");
        this.poller.setAdditionalMetaData(simpleTestMsg, new MimeBodyPart());

        return simpleTestMsg.getContentDisposition();
    }


}
