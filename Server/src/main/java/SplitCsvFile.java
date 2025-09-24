import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.help.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.openas2.util.FileUtil;
import java.io.File;
import java.io.IOException;

    /**
     * Class used to add the server's certificate to the KeyStore with your trusted
     * certificates.
     */
    public class SplitCsvFile {

        public static final String SOURCE_FILE = "s";
        public static final String OUT_FILENAME_PREFIX = "p";
        public static final String OUTPUT_DIR = "o";
        public static final String MAX_FILE_SIZE = "m";
        public static final String HAS_HEADER_ROW = "h";
        public static final String DEBUG = "d";

        /*
         * Options in this format: short-opt, long-opt, has-argument, required,
         * description
         */
        public String[][] opts = {
                {SOURCE_FILE, "source", "true", "true", "the source file name including path"},
                {MAX_FILE_SIZE, "max_size", "true", "true", "the maximum size of the files"},
                {HAS_HEADER_ROW, "has_header", "false", "false", "if set, the file has a header row that wil be replicated into every file"},
                {OUT_FILENAME_PREFIX, "out_file_prefix", "true", "false", "the prefix for the split file names"},
                {OUTPUT_DIR, "out_dir", "true", "false", "output directory for the split files - defaults to current dir"},
                {DEBUG, "debug", "true", "false", "Enabling debug logging"}
        };

        private void usage(Options options) {
            String header = "Splits  CSV file." + "\nReads the file as a line based file creating new files that will not exceed the specified maximum size.";
            String footer = "NOTE: The file is expected to contain lines separated by newline characters.";

            HelpFormatter formatter = HelpFormatter.builder().get();
            try {
                formatter.printHelp(this.getClass().getName(), header, options, footer, true);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }

        private CommandLine parseCommandLine(String[] args) {
            // create the command line parser
            CommandLineParser parser = new DefaultParser();

            // create the Options
            Options options = new Options();
            for (String[] opt : opts) {
                Option option = Option.builder(opt[0]).longOpt(opt[1]).hasArg("true".equalsIgnoreCase(opt[2])).desc(opt[4]).get();
                option.setRequired("true".equalsIgnoreCase(opt[3]));
                options.addOption(option);
            }

            // parse the command line arguments
            CommandLine line = null;
            try {
                line = parser.parse(options, args);
            } catch (ParseException e) {
                System.out.println("Command line parsing error: " + e.getMessage());
                usage(options);
                System.exit(-1);
            }
            return line;
        }

        private void process(String[] args) throws Exception {
            CommandLine options = parseCommandLine(args);
            if (options == null) {
                return;
            }
            String sourceFileName = options.getOptionValue(SOURCE_FILE);
            File sourceFile = new File(sourceFileName);
            if (!sourceFile.exists()) {
                throw new Exception("File does not exist: " + sourceFileName);
            }
            long maxFileSize = Long.parseLong(options.getOptionValue(MAX_FILE_SIZE));
            String outputDirName = null;
            if (options.hasOption(OUTPUT_DIR)) {
                outputDirName = options.getOptionValue(OUTPUT_DIR);
            } else {
                outputDirName = System.getProperty("user.dir");
            }
            String prefix = (options.hasOption(OUT_FILENAME_PREFIX)) ? options.getOptionValue(OUT_FILENAME_PREFIX) : "";
            boolean hasHeaderRow = options.hasOption(HAS_HEADER_ROW);

            if (options.hasOption(DEBUG) && "true".equalsIgnoreCase(options.getOptionValue(DEBUG))) {
                System.setProperty("org.apache.commons.logging.Log", "org.apache.commons.logging.impl.SimpleLog");
                System.setProperty("org.apache.commons.logging.simplelog.showdatetime", "true");
                System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http", "DEBUG");
                System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.wire", "ERROR");
            }
            try {
                FileUtil.splitLineBasedFile(sourceFile, outputDirName, maxFileSize, hasHeaderRow, sourceFile.getName(), prefix);
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Processing error occurred: " + e.getMessage());
                System.exit(-1);
            }
        }

        public static void main(String[] args) {
            try {
                SplitCsvFile mgr = new SplitCsvFile();
                mgr.process(args);
                System.exit(0);
            } catch (Exception e) {
                System.out.println("Processing error occurred: " + e.getMessage());
                System.exit(-1);
            }
        }
    }
