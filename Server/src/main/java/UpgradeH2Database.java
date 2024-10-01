

import java.util.Properties;

public class UpgradeH2Database {
    public static void main(String[] args) throws Exception {
        try {
            if (args == null || args.length != 4) {
                System.out.println("Requires 4 arguments: <source H2 version number> <user ID> <password> <path to H2 DB file>");
                System.exit(1);
            }
            System.out.println("\n\nSTARTING UPGRADE: ...........");
            Properties props = new Properties();
            props.put("user", args[1]);
            props.put("password", args[2]);
            org.h2.tools.Upgrade.upgrade("jdbc:h2:" + args[3], props, Integer.parseInt(args[0]));
        } catch (Exception e) {
            System.out.println("\n\nUPGRADE ERROR OCCURED: " + e);
            System.exit(1);
        }
        System.out.println("\n\nUPGRADE COMPLETE.\n");
        System.exit(0);
    }

}