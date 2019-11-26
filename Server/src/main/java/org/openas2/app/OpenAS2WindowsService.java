package org.openas2.app;

import java.util.Arrays;

public class OpenAS2WindowsService {
    private static OpenAS2Server server;

    public static boolean stop = false;

    public static void main(String[] args) throws Exception {
        String parm = "start";
        String[] params = {};
        if (args.length > 0) {
            parm = args[0];
            params = Arrays.copyOfRange(args, 1, args.length);
        }
        if ("start".equals(parm)) {
            start(params);
        } else if ("stop".equals(args[0])) {
            stop(args);
        }
    }

    public static void stop(String[] params) {
        System.exit(0);
    }

    public static void start(String[] params) throws Exception {
        server = new OpenAS2Server.Builder().registerShutdownHook().run(params);
    }
}
