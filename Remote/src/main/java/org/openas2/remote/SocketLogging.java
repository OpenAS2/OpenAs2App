package org.openas2.remote;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author joseph mcverry - american coders, ltd.
 * <p>
 * to be used with the web server
 */
public class SocketLogging {

    ServerSocket ss;

    public static void main(String args[]) throws Exception {
        int port = Integer.parseInt(args[0]);
        SocketLogging sl = new SocketLogging(port);
        sl.logToPrintStream(System.out, "\n");
    }

    /**
     * builds the server socket
     *
     * @param port ip port to talk with server
     * @throws IOException - any number of socket errors
     */
    public SocketLogging(int port) throws IOException {
        ss = new ServerSocket(port);
    }

    /**
     * get the logging data from the socket
     * print data to the stream
     * add a new line character if told to
     * repeat
     *
     * @param out   - PrintStream object to log to
     * @param addOn - after each logging event add a new line ?
     * @throws IOException - socket or io errors are possible
     */
    public void logToPrintStream(PrintStream out, String addOn) throws IOException {
        int b;
        while (true) {
            Socket s = ss.accept();
            InputStream is = s.getInputStream();
            while ((b = is.read()) != -1) {
                out.print((char) b);
            }

            out.println(addOn);
            out.flush();
        }

    }

    public void close() {
        try {
            ss.close();
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

    }
}
