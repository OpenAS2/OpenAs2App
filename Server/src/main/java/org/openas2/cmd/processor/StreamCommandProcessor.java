package org.openas2.cmd.processor;

import org.openas2.WrappedException;
import org.openas2.cmd.Command;
import org.openas2.cmd.CommandResult;
import org.openas2.util.CommandTokenizer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * original author unknown
 * <p>
 * in this release made the process a thread so it can be shared with other command processors like
 * the SocketCommandProcessor
 * created innerclass CommandTokenizer so it could handle quotes and spaces within quotes
 *
 * @author joseph mcverry
 */
public class StreamCommandProcessor extends BaseCommandProcessor {
    public static final String COMMAND_NOT_FOUND = "Error: command not found";
    public static final String COMMAND_ERROR = "Error executing command";
    public static final String SERVER_EXIT_COMMAND = "exit";
    public static final String PROMPT = "#>";
    private BufferedReader reader = null;
    private BufferedWriter writer = null;

    public StreamCommandProcessor() {
        reader = new BufferedReader(new InputStreamReader(System.in));
        writer = new BufferedWriter(new OutputStreamWriter(System.out));
    }

    public BufferedReader getReader() {
        return reader;
    }

    public BufferedWriter getWriter() {
        return writer;
    }

    public void processCommand() throws Exception {
        try {

            String str = readLine();

            if (str != null) {
                CommandTokenizer strTkn = new CommandTokenizer(str);

                if (strTkn.hasMoreTokens()) {
                    String commandName = strTkn.nextToken().toLowerCase();

                    if (commandName.equals(SERVER_EXIT_COMMAND)) {
                        terminate();
                    } else {
                        List<String> params = new ArrayList<String>();

                        while (strTkn.hasMoreTokens()) {

                            params.add(strTkn.nextToken());
                        }

                        Command cmd = getCommand(commandName);

                        if (cmd != null) {
                            CommandResult result = cmd.execute(params.toArray());

                            if (result.getType() == CommandResult.TYPE_OK) {
                                writeLine(result.toString());
                            } else {
                                writeLine(COMMAND_ERROR);
                                writeLine(result.getResult());
                            }
                        } else {
                            writeLine(COMMAND_NOT_FOUND + "> " + commandName);
                            List<Command> l = getCommands();
                            writeLine("List of commands:");
                            writeLine(SERVER_EXIT_COMMAND);
                            for (int i = 0; i < l.size(); i++) {
                                cmd = l.get(i);
                                writeLine(cmd.getName());
                            }
                        }
                    }
                }

                write(PROMPT);
            } else {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            }

        } catch (IOException ioe) {
            throw new WrappedException(ioe);
        }
    }

    public String readLine() throws java.io.IOException {
        BufferedReader rd = getReader();

        return rd.readLine().trim();
    }

    public void write(String text) throws java.io.IOException {
        BufferedWriter wr = getWriter();
        wr.write(text);
        wr.flush();
    }

    public void writeLine(String line) throws java.io.IOException {
        BufferedWriter wr = getWriter();
        wr.write(line + "\r\n");
        wr.flush();
    }

    @Override
    public void destroy() throws Exception {
        reader = null; // Cannot close System.in
        super.destroy();
    }
}
