package org.openas2.util;

import org.junit.Before;
import org.junit.Test;
import org.junit.Rule;
import org.junit.runner.RunWith;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.mockito.Mock;
import org.mockito.exceptions.misusing.InvalidUseOfMatchersException;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.openas2.partner.Partnership;
import org.openas2.util.Properties;
import org.openas2.util.IOUtil;
import org.apache.commons.lang3.SystemUtils;
import org.openas2.OpenAS2Exception;
import org.openas2.message.InvalidMessageException;
import org.junit.rules.ExpectedException;
    
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.matches;
import static org.mockito.Mockito.when;

//@RunWith(MockitoJUnitRunner.class)
public class FilenameSafetyTest {

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void pathTraversal1() throws Exception {
        if (SystemUtils.IS_OS_WINDOWS) {
            thrown.expect(OpenAS2Exception.class);
            IOUtil.getSafeFilename("\\USERS\\NAME\\Desktop\\file.docx");
        } else if (
            SystemUtils.IS_OS_LINUX ||
            SystemUtils.IS_OS_MAC
        ) {
            thrown.expect(OpenAS2Exception.class);
            IOUtil.getSafeFilename("/etc/file.cfg");
        } 
    }

    @Test
    public void pathTraversal2() throws Exception {
        if (SystemUtils.IS_OS_WINDOWS) {
            thrown.expect(OpenAS2Exception.class);
            IOUtil.getSafeFilename("..\\..\\..\\bin\\startup.bat");
        } else if (
            SystemUtils.IS_OS_LINUX ||
            SystemUtils.IS_OS_MAC
        ) {
            thrown.expect(OpenAS2Exception.class);
            IOUtil.getSafeFilename("../../../bin/startup.sh");
        } 
    }

    @Test
    public void pathTraversal3() throws Exception {
        if (SystemUtils.IS_OS_WINDOWS) {
            thrown.expect(OpenAS2Exception.class);
            IOUtil.getSafeFilename("F:start.exe");
        } 
    }

    @Test
    public void pathTraversal4() throws Exception {
        if (SystemUtils.IS_OS_WINDOWS) {
            thrown.expect(OpenAS2Exception.class);
            IOUtil.getSafeFilename("\\\\host\\c$\\windows\\system32\\cmd.exe");
        } 
    }

    @Test
    public void pathTraversal5() throws Exception {
        if (SystemUtils.IS_OS_WINDOWS) {
            thrown.expect(OpenAS2Exception.class);
            IOUtil.getSafeFilename("D:\\USERS\\NAME\\Desktop\\file.docx");
        } 
    }

    @Test
    public void cleanName() throws Exception {
        if ((SystemUtils.IS_OS_WINDOWS) ||
            (SystemUtils.IS_OS_LINUX) ||
            (SystemUtils.IS_OS_MAC)
        ) { 
            assertThat(IOUtil.getSafeFilename("<TEST>-1>"), equalToIgnoringCase("TEST-1"));
            assertThat(IOUtil.getSafeFilename(">>file.bin"), equalToIgnoringCase("file.bin"));
            assertThat(IOUtil.getSafeFilename("\u0000TEST\u0000.TXT"), equalToIgnoringCase("TEST.TXT"));
        }      
    }

    @Test
    public void noChangeName() throws Exception {
        if ((SystemUtils.IS_OS_WINDOWS) ||
            (SystemUtils.IS_OS_LINUX) ||
            (SystemUtils.IS_OS_MAC)
        ) { 
            assertThat(IOUtil.getSafeFilename("ORDRSP_20201022142233_007889.EDI"), equalToIgnoringCase("ORDRSP_20201022142233_007889.EDI"));
            assertThat(IOUtil.getSafeFilename("INVOIC.cVb33ZwK6gd2.xml"), equalToIgnoringCase("INVOIC.cVb33ZwK6gd2.xml"));
            assertThat(IOUtil.getSafeFilename("{013a9433-c8ff-4630-9958-e214b5acd13e}"), equalToIgnoringCase("{013a9433-c8ff-4630-9958-e214b5acd13e}"));
            assertThat(IOUtil.getSafeFilename("ORDER 1.CSV"), equalToIgnoringCase("ORDER 1.CSV"));
            assertThat(IOUtil.getSafeFilename("PRICAT-a7kwH83Mq.json"), equalToIgnoringCase("PRICAT-a7kwH83Mq.json"));
            assertThat(IOUtil.getSafeFilename("DOC1"), equalToIgnoringCase("DOC1"));
            assertThat(IOUtil.getSafeFilename("A"), equalToIgnoringCase("A"));
            assertThat(IOUtil.getSafeFilename("1"), equalToIgnoringCase("1"));
        }      
    }

    @Test
    public void badName1() throws Exception {
        thrown.expect(InvalidMessageException.class);
        IOUtil.getSafeFilename("");
    }

    @Test
    public void badName2() throws Exception {
        thrown.expect(InvalidMessageException.class);
        IOUtil.getSafeFilename(".");
    }

    @Test
    public void badName3() throws Exception {
        thrown.expect(InvalidMessageException.class);
        IOUtil.getSafeFilename("..");
    }

    @Test
    public void badName4() throws Exception {
        thrown.expect(InvalidMessageException.class);
        IOUtil.getSafeFilename(":::");
    }

    /* quick test */
    public static void main(String[] args) {
        Result result = JUnitCore.runClasses(FilenameSafetyTest.class);      
        for (Failure failure : result.getFailures()) {
            System.out.println(failure.toString());
        }      
        System.out.println(result.wasSuccessful());
    }
} 
