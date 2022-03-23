package org.openas2.upgrades;

import java.io.File;
import java.io.FileInputStream;

import org.openas2.util.XMLUtil;
import org.w3c.dom.Document;
import org.xml.sax.helpers.XMLFilterImpl;


public class MigratePollingModuleConfig {

    public static void main(String[] args) throws Exception {
        FileInputStream configAsStream = null;
        FileInputStream partnershipAsStream = null;
        try {
            if (args == null || args.length != 2) {
                System.out.println("Requires 2 arguments: <source config.xml file> <source partnership.xml file>");
                System.exit(1);
            }
            File configInFile = new File(args[0]);
            if (!configInFile.exists()) {
                System.out.println("No config file found: " + args[0]);
                System.exit(1);
            }
            File partnershipInFile = new File(args[1]);
            if (!partnershipInFile.exists()) {
                System.out.println("No partnership file found: " + args[1]);
                System.exit(1);
            }
            XMLFilterImpl xmlFilter = new XMLFilterImpl();
            configAsStream = new FileInputStream(configInFile);
            Document cfgDoc = XMLUtil.parseXML(configAsStream, xmlFilter);
            partnershipAsStream = new FileInputStream(partnershipInFile);
            Document partnershipDoc = XMLUtil.parseXML(partnershipAsStream, xmlFilter);
            
        } finally {
            if (configAsStream != null) {
                configAsStream.close();
            }
            if (partnershipAsStream != null) {
                partnershipAsStream.close();
            }
        }
    }


}
