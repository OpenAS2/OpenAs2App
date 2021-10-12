package org.openas2.util;

import org.junit.Test;
import org.junit.Rule;
import org.junit.Assert;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.apache.commons.lang3.SystemUtils;
import org.openas2.OpenAS2Exception;
import org.openas2.message.InvalidMessageException;
    
import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.hamcrest.MatcherAssert.assertThat;

//@RunWith(MockitoJUnitRunner.class)
public class FilenameSafetyTest {

    @Test
    public void pathTraversal1() throws Exception {
        String testStr = SystemUtils.IS_OS_WINDOWS?"\\USERS\\NAME\\Desktop\\file.docx":"/etc/file.cfg";
    	Assert.assertThrows(OpenAS2Exception.class, () -> {IOUtil.getSafeFilename(testStr);});
    }

    @Test
    public void pathTraversal2() throws Exception {
        String testStr = SystemUtils.IS_OS_WINDOWS?"..\\..\\..\\bin\\startup.bat":"../../../bin/startup.sh";
    	Assert.assertThrows(OpenAS2Exception.class, () -> {IOUtil.getSafeFilename(testStr);});
    }

    @Test
    public void pathTraversal3() throws Exception {
        if (SystemUtils.IS_OS_WINDOWS) {
            String testStr = "F:start.exe";
        	Assert.assertThrows(OpenAS2Exception.class, () -> {IOUtil.getSafeFilename(testStr);});
        } 
    }

    @Test
    public void pathTraversal4() throws Exception {
        if (SystemUtils.IS_OS_WINDOWS) {
            String testStr = "\\\\host\\c$\\windows\\system32\\cmd.exe";
        	Assert.assertThrows(OpenAS2Exception.class, () -> {IOUtil.getSafeFilename(testStr);});
        } 
    }

    @Test
    public void pathTraversal5() throws Exception {
        if (SystemUtils.IS_OS_WINDOWS) {
            String testStr = "D:\\USERS\\NAME\\Desktop\\file.docx";
        	Assert.assertThrows(OpenAS2Exception.class, () -> {IOUtil.getSafeFilename(testStr);});
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
        String testStr = "";
    	Assert.assertThrows(InvalidMessageException.class, () -> {IOUtil.getSafeFilename(testStr);});
    }

    @Test
    public void badName2() throws Exception {
        String testStr = ".";
    	Assert.assertThrows(InvalidMessageException.class, () -> {IOUtil.getSafeFilename(testStr);});
    }

    @Test
    public void badName3() throws Exception {
        String testStr = "..";
    	Assert.assertThrows(InvalidMessageException.class, () -> {IOUtil.getSafeFilename(testStr);});
    }

    @Test
    public void badName4() throws Exception {
        String testStr = ":::";
    	Assert.assertThrows(InvalidMessageException.class, () -> {IOUtil.getSafeFilename(testStr);});
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
