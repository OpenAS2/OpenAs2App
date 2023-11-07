package org.openas2.util;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.junit.jupiter.MockitoExtension;
import org.openas2.message.AS2Message;
import org.openas2.params.MessageParameters;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

@ExtendWith(MockitoExtension.class)
public class IOUtilTest {
    private AS2Message message = new AS2Message();

    @TempDir
    public static File tmp;

    private class Record {
        String filename;
        String format;
        String delimiters;
        boolean mergeExtraTokens;

        String expectFrom;
        String expectTo;
        String expectFileName;

        public Record(String filename, String format, String delimiters, boolean mergeExtraTokens, String expectFileName, String expectFrom, String expectTo) {
            this.filename = filename;
            this.format = format;
            this.delimiters = delimiters;
            this.mergeExtraTokens = mergeExtraTokens;
            this.expectFileName = expectFileName;
            this.expectFrom = expectFrom;
            this.expectTo = expectTo;
        }
    }

    @SuppressWarnings("serial")
    private List<Record> testParameterParsingData = new ArrayList<Record>() {
        {
            add(new Record("MyCo-PartnerCo-MyFileNameWithExtension.edi", "sender.as2_id, receiver.as2_id," + " attributes.filename", "-.", false, "MyFileNameWithExtension", "MyCo", "PartnerCo"));
            add(new Record("MyCo-PartnerCo-MyFileNameWithExtension.edi", "sender.as2_id, receiver.as2_id," + " attributes.filename", "-.", true, "MyFileNameWithExtension.edi", "MyCo", "PartnerCo"));
        }
    };

    /* Test records for polling filters
     * Format is <filename>, <allowed file extensions list>, "excluded extensions list>, <count of files to return (as a string)
     */
    private String[][] testFilePollingFilters = {{"File1.txt", "txt", "", "1"}, {"File2.edi", "txt, edi", "", "1"}, {"File3.tmp", "", "temp", "1"},
        // Tests that should not return a file
        {"File4.txt", "edi", "", "0"}, {"File5.edi", "txt, edint", "", "0"}, {"File6.tmp", "", "tmp", "0"}, {"File7.tmp", "", "part , tmp", "0"}};

    @BeforeAll
    public static void setUp() throws Exception {
    	tmp = Files.createTempDirectory("testResources").toFile();
    }

    @Test
    public void parameterParsing() throws Exception {
        for (Record record : testParameterParsingData) {

            MessageParameters params = new MessageParameters(message);
            String filename = record.filename;
            String format = record.format;

            if (format != null) {
                String delimiters = record.delimiters;
                boolean mergeExtra = record.mergeExtraTokens;
                params.setParameters(format, delimiters, filename, mergeExtra);
            }
            String expectFilename = record.expectFileName;
            assertThat("Check " + expectFilename + " against " + params.getParameter(MessageParameters.KEY_ATTRIBUTES + ".filename"), params.getParameter(MessageParameters.KEY_ATTRIBUTES + ".filename").matches(Pattern.quote(expectFilename)), is(true));
            assertThat("Check receiver AS2 ID " + record.expectTo + " against " + params.getParameter(MessageParameters.KEY_RECEIVER + ".as2_id"), params.getParameter(MessageParameters.KEY_RECEIVER + ".as2_id").matches(Pattern.quote(record.expectTo)), is(true));
            assertThat("Check sender AS2 ID " + record.expectFrom + " against " + params.getParameter(MessageParameters.KEY_SENDER + ".as2_id"), params.getParameter(MessageParameters.KEY_SENDER + ".as2_id").matches(Pattern.quote(record.expectFrom)), is(true));
        }
    }

    @Test
    public void filePollingFilters() throws Exception {

        for (String[] strings : testFilePollingFilters) {
            File name = Files.createFile(Paths.get(tmp.toString(), strings[0])).toFile();
            List<String> allow = new ArrayList<String>();
            if (strings[1] != null && strings[1].length() > 0) {
                allow = Arrays.asList(strings[1].split("\\s*,\\s*"));
            }
            List<String> exclude = new ArrayList<String>();
            if (strings[2] != null && strings[2].length() > 0) {
                exclude = Arrays.asList(strings[2].split("\\s*,\\s*"));
            }
            File[] files = IOUtil.getFiles(tmp, allow, exclude);
            assertThat("Check that a file is detected for " + name.getName() + ":" + allow + ":" + exclude, files.length == Integer.parseInt(strings[3]), is(true));
            // Cleanup for next loop
            name.delete();
        }

    }
}
