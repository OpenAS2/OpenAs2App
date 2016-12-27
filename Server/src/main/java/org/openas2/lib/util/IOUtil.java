package org.openas2.lib.util;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;

public class IOUtil {
    public static final String MSG_WAIT_FOR_KEYPRESS = "Waiting for keypress...";
    public static final String MSG_PROMPT = "> ";

    public static int copy(InputStream in, OutputStream out) throws IOException {
        int totalCount = 0;
        byte[] buf = new byte[4096];
        int count = 0;

        while (count >= 0) {
            count = in.read(buf);
            totalCount += count;

            if (count > 0) {
                out.write(buf, 0, count);
            }
        }

        return totalCount;
    }

    public static byte[] toBytes(InputStream in) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        copy(in, baos);

        return baos.toByteArray();
    }

    public static void waitForKeypress(String message, PrintStream out, InputStream in)
            throws IOException {
        out.println();
        if (message == null) {
            message = MSG_WAIT_FOR_KEYPRESS;
        }
        out.println(message);

        in.read();
        int num = in.available();
        for (int i = 0; i < num; i++) {
            in.read();
        }
    }

    public static void waitForKeypress(String message) throws IOException {
        waitForKeypress(message, System.out, System.in);
    }

    public static String prompt(String message, String defaultValue, PrintStream out, InputStream in)
            throws IOException {
        // show the prompt
        if (message == null) {
            message = MSG_PROMPT;
        }
        out.print(message);

        // read user input
        String answer = new BufferedReader(new InputStreamReader(in)).readLine();

        // return the user input, or the default value if no user input was
        // given
        if (answer != null && !answer.toString().equals("")) {
            return answer.toString();
        }
        return defaultValue;
    }

    public static String prompt(String message, String defaultValue) throws IOException {
        return prompt(message, defaultValue, System.out, System.in);
    }

    public static int prompt(String message, int defaultValue, PrintStream out, InputStream in)
            throws IOException {
        String value = prompt(message, Integer.toString(defaultValue), out, in);
        return Integer.parseInt(value);
    }

    public static int prompt(String message, int defaultValue) throws IOException {
        return prompt(message, defaultValue, System.out, System.in);
    }
}