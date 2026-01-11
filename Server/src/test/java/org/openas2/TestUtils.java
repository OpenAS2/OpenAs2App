package org.openas2;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openas2.app.OpenAS2Server;
import org.openas2.partner.Partnership;
import org.openas2.partner.PartnershipFactory;
import org.openas2.processor.receiver.DirectoryPollingModule;

/**
 * Utilities for tests
 */
public class TestUtils {

    /**
     * Wait till a file will occur on the file system.
     *
     * @param parent     a directory where file will be.
     * @param fileFilter a filter to scan the parent dir.
     * @param timeout    an amount of time units to wait
     * @param unit       a time unit
     * @return a file
     * @throws FileNotFoundException
     */
    public static File waitForFile(File dir, String fileNameSubstr, int timeout, TimeUnit unit) throws FileNotFoundException {
        long finishAt = System.currentTimeMillis() + unit.toMillis(timeout);
        FilenameFilter subStringFilter = (d, s) -> {
            return s.contains(fileNameSubstr);
        };
        while (finishAt - System.currentTimeMillis() > 0) {
            String[] files = dir.list(subStringFilter);
            if (files.length == 1) {
                return new File(dir.getPath() + File.separator + files[0]);
            } else if (files.length > 1) {
                    throw new IllegalStateException("Result is not unique.");
            }
        }
        throw new FileNotFoundException("Directory: " + dir.getAbsolutePath() + " File Name Substring: " + fileNameSubstr);
    }

    public static boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }

    public static int waitTillAllFilesReceived(File dir, int fileCount, int timeout, TimeUnit unit) throws FileNotFoundException {
        long finishAt = System.currentTimeMillis() + unit.toMillis(timeout);
        while (finishAt - System.currentTimeMillis() > 0) {
            File[] files = dir.listFiles();
            if (files.length == fileCount) {
                return fileCount;
            } else if (files.length > fileCount) {
                    return files.length;
            }
        }
        return dir.listFiles().length;
    }

    /**
     * Finds the first partnership in the list for the server instance that has a directory poller and builds a TestPartner object using that
     * @param server - the instance of an OpenAS2 server
     * @return - a TestPartner instance based on the partnership found
     * @throws Exception
     */
    public static TestPartner getFromFirstSendingPartnership(OpenAS2Server server) throws Exception {
        PartnershipFactory pf = server.getSession().getPartnershipFactory();
        List<Partnership> partnerships = pf.getPartnerships();
        for (Iterator<Partnership> iterator = partnerships.iterator(); iterator.hasNext();) {
            Partnership partnership = (Partnership) iterator.next();
            DirectoryPollingModule pollerModule = getPollingModule((XMLSession) server.getSession(), partnership);
            if (pollerModule != null) {
                return new TestPartner(server, partnership, pollerModule);
            }
        }
        return null;
    }

    public static TestPartner getFromPartnerIds(OpenAS2Server server, String senderAs2Id, String receiverAs2Id) throws Exception {
        PartnershipFactory pf = server.getSession().getPartnershipFactory();
        List<Partnership> partnerships = pf.getPartnerships();
        for (Iterator<Partnership> iterator = partnerships.iterator(); iterator.hasNext();) {
            Partnership partnership = (Partnership) iterator.next();
            if (senderAs2Id.equals(partnership.getSenderID(Partnership.PID_AS2)) && receiverAs2Id.equals(partnership.getReceiverID(Partnership.PID_AS2))) {
                DirectoryPollingModule pollerModule = getPollingModule((XMLSession) server.getSession(), partnership);
                return new TestPartner(server, partnership, pollerModule);
            }
        }
        return null;
    }

    public static DirectoryPollingModule getPollingModule(XMLSession session, Partnership partnership) throws ComponentNotFoundException {
        DirectoryPollingModule dirPollMod = session.getPartnershipPoller(partnership.getName());
        if (dirPollMod != null) {
            return dirPollMod;
        }
        // Try to find a module defined poller since there is no matching poller by name. (config.xml defined pollers do not have the correct partnership name in the poller cache)
        return session.getPartnershipPoller(partnership.getSenderID(Partnership.PID_AS2), partnership.getReceiverID(Partnership.PID_AS2));        
    }

    public static void appendLinesToFile(Path filePath, String[] lines) throws IOException {
        for (int i = 0; i < lines.length; i++) {
            Files.write(
                filePath, 
                ("\n" + lines[i]).getBytes(), 
                StandardOpenOption.APPEND);
        }

    }

}
