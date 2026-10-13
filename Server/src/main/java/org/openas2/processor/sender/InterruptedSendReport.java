package org.openas2.processor.sender;

import org.openas2.Session;
import org.openas2.message.AS2Message;
import org.openas2.message.FileAttribute;
import org.openas2.message.Message;
import org.openas2.processor.Processor;
import org.openas2.processor.ProcessorModule;
import org.openas2.processor.resender.DirectoryResenderModule;
import org.openas2.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Reports, when the server starts, files left in the pending folder by a send that was cut off, such
 * as by the process being killed part way through.
 * <p>
 * A file is moved from the outbox into the pending folder before it is sent, and the pending
 * information file is written immediately before it is transmitted. When the server starts nothing is
 * in progress yet, so any file still there falls into one of these cases:
 * <ul>
 * <li>it is queued in the resend directory, and will be retried: not reported</li>
 * <li>it has pending information and asked for an asynchronous MDN, which may still arrive: not reported</li>
 * <li>it has pending information but asked for a synchronous MDN, so the send was interrupted after it
 * started and the partner may have received it. The scheduled check in the sender moves it to the error
 * folder once the MDN wait has passed; this reports it straight away and says why.</li>
 * <li>it has no pending information, so it was never transmitted. Nothing else ever picks these up, so
 * without this report the file sat in the pending folder unnoticed.</li>
 * </ul>
 * Nothing is moved or resent: whether an interrupted send reached the partner cannot be known here, and
 * resending it could deliver it twice.
 */
public class InterruptedSendReport {

    private static final Logger LOGGER = LoggerFactory.getLogger(InterruptedSendReport.class);

    /** What a left over pending file is known to be. */
    public enum Status {
        NEVER_SENT, MAY_HAVE_BEEN_SENT
    }

    /** A file in the pending folder that nothing is going to process. */
    public static class Finding {
        private final File file;
        private final Status status;
        private final String messageId;

        Finding(File file, Status status, String messageId) {
            this.file = file;
            this.status = status;
            this.messageId = messageId;
        }

        public File getFile() {
            return file;
        }

        public Status getStatus() {
            return status;
        }

        /** @return the pending information file name, which is the cleaned message ID, or null if there is none */
        public String getMessageId() {
            return messageId;
        }
    }

    /**
     * Logs a warning for each file left in the pending folder. Never throws: a problem reading the
     * folders must not stop the server starting.
     *
     * @param session - the session about to be started
     */
    public static void logInterruptedSends(Session session) {
        try {
            Processor processor = session.getProcessor();
            String pendingDir = processor.getParameters().get(Processor.PENDING_MDN_MSG_DIRECTORY_IDENTIFIER);
            String pendingInfoDir = processor.getParameters().get(Processor.PENDING_MDN_INFO_DIRECTORY_IDENTIFIER);
            if (pendingDir == null || pendingInfoDir == null) {
                return;
            }
            List<File> resendDirs = new ArrayList<File>();
            for (ProcessorModule module : processor.getModules()) {
                if (module instanceof DirectoryResenderModule) {
                    String resendDir = ((DirectoryResenderModule) module).getResendDirectory();
                    if (resendDir != null) {
                        resendDirs.add(new File(resendDir));
                    }
                }
            }
            List<Finding> findings = findInterruptedSends(new File(pendingDir), new File(pendingInfoDir), resendDirs);
            if (findings.isEmpty()) {
                return;
            }
            LOGGER.warn(findings.size() + " file(s) in the pending folder " + pendingDir + " were left by sends that"
                    + " did not finish, for example because the server was stopped part way through. If another"
                    + " OpenAS2 server shares this folder they may still be in progress there.");
            String mdnWait = Properties.getProperty(Properties.AS2_MDN_RESP_MAX_WAIT_SECS, "4560");
            for (Finding finding : findings) {
                long ageMinutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - finding.getFile().lastModified());
                if (finding.getStatus() == Status.NEVER_SENT) {
                    LOGGER.warn("Never sent: " + finding.getFile().getAbsolutePath() + " (last changed " + ageMinutes
                            + " minutes ago). It was not transmitted, so it can be moved back to the outbox it came from"
                            + " to be sent.");
                } else {
                    LOGGER.warn("Interrupted after sending started: " + finding.getFile().getAbsolutePath()
                            + " (" + (finding.getMessageId() == null ? "" : "message " + finding.getMessageId() + ", ")
                            + "last changed " + ageMinutes + " minutes ago)."
                            + " The partner may have received it, so check with them or the message tracking before"
                            + " sending it again or it may be delivered twice. It will be moved to the error folder"
                            + " once " + Properties.AS2_MDN_RESP_MAX_WAIT_SECS + " (" + mdnWait + " seconds) has passed.");
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Could not check the pending folder for files left by interrupted sends: " + e.getMessage(), e);
        }
    }

    /**
     * Finds the files in the pending folder that nothing is going to process.
     *
     * @param pendingDir - the folder files are moved into while they are sent
     * @param pendingInfoDir - the folder holding a pending information file for each send in progress
     * @param resendDirs - the folders holding messages queued to be resent
     * @return the files found, in no particular order
     */
    public static List<Finding> findInterruptedSends(File pendingDir, File pendingInfoDir, List<File> resendDirs) {
        List<Finding> findings = new ArrayList<Finding>();
        File[] pendingFiles = pendingDir.listFiles();
        if (pendingFiles == null) {
            return findings;
        }
        List<File> unreadableInfo = new ArrayList<File>();
        Map<Path, File> pendingInfoByFile = pendingInfoByPendingFile(pendingInfoDir, unreadableInfo);
        Set<Path> queuedForResend = pendingFilesQueuedForResend(resendDirs);
        for (File file : pendingFiles) {
            // The ".object" file beside a pending file is the stored message, not something to send
            if (!file.isFile() || file.getName().endsWith(".object")) {
                continue;
            }
            Path key = key(file.getPath());
            if (queuedForResend.contains(key)) {
                continue;
            }
            File pendingInfo = pendingInfoByFile.get(key);
            if (pendingInfo == null) {
                /*
                 * Never sent is only certain when every pending information file could be read: one that
                 * could not might belong to this file, and calling a delivered file unsent invites
                 * sending it twice.
                 */
                findings.add(new Finding(file, unreadableInfo.isEmpty() ? Status.NEVER_SENT : Status.MAY_HAVE_BEEN_SENT, null));
            } else if (!isAwaitingAsyncMdn(new File(file.getPath() + ".object"))) {
                findings.add(new Finding(file, Status.MAY_HAVE_BEEN_SENT, pendingInfo.getName()));
            }
        }
        return findings;
    }

    private static Map<Path, File> pendingInfoByPendingFile(File pendingInfoDir, List<File> unreadable) {
        Map<Path, File> byFile = new HashMap<Path, File>();
        File[] infoFiles = pendingInfoDir.listFiles();
        if (infoFiles == null) {
            return byFile;
        }
        for (File infoFile : infoFiles) {
            if (!infoFile.isFile()) {
                continue;
            }
            /*
             * Written by AS2SenderModule.storePendingInfo: the pending file is the fifth entry. The file
             * stream is a resource of its own because the object stream's constructor reads a header and
             * throws on a file that is not one, which would otherwise leave the file open.
             */
            try (FileInputStream file = new FileInputStream(infoFile); ObjectInputStream in = new ObjectInputStream(file)) {
                for (int i = 0; i < 4; i++) {
                    in.readObject();
                }
                String pendingFile = (String) in.readObject();
                if (pendingFile != null) {
                    byFile.put(key(pendingFile), infoFile);
                }
            } catch (IOException | ClassNotFoundException | ClassCastException e) {
                LOGGER.warn("Could not read the pending information file " + infoFile.getAbsolutePath() + ": " + e.getMessage());
                unreadable.add(infoFile);
            }
        }
        return byFile;
    }

    private static Set<Path> pendingFilesQueuedForResend(List<File> resendDirs) {
        Set<Path> queued = new HashSet<Path>();
        for (File resendDir : resendDirs) {
            File[] entries = resendDir.listFiles();
            if (entries == null) {
                continue;
            }
            for (File entry : entries) {
                if (!entry.isFile()) {
                    continue;
                }
                // Written by DirectoryResenderModule.handle: the method, the retry count, then the message
                try (FileInputStream file = new FileInputStream(entry); ObjectInputStream in = new ObjectInputStream(file)) {
                    in.readObject();
                    in.readObject();
                    Message msg = (Message) in.readObject();
                    String pendingFile = msg.getAttribute(FileAttribute.MA_PENDINGFILE);
                    if (pendingFile != null) {
                        queued.add(key(pendingFile));
                    }
                } catch (IOException | ClassNotFoundException | ClassCastException e) {
                    LOGGER.debug("Skipping unreadable resend queue entry " + entry.getAbsolutePath() + ": " + e.getMessage());
                }
            }
        }
        return queued;
    }

    /*
     * An asynchronous MDN can legitimately arrive after a restart, so a send that asked for one is not
     * reported. When the stored message cannot be read the send is reported, since saying nothing about
     * a file that may have been delivered is the worse mistake.
     */
    private static boolean isAwaitingAsyncMdn(File storedMessage) {
        if (!storedMessage.isFile()) {
            return false;
        }
        try (FileInputStream file = new FileInputStream(storedMessage); ObjectInputStream in = new ObjectInputStream(file)) {
            Object msg = in.readObject();
            return msg instanceof AS2Message && ((AS2Message) msg).isConfiguredForAsynchMDN();
        } catch (IOException | ClassNotFoundException e) {
            return false;
        }
    }

    private static Path key(String path) {
        return new File(path).getAbsoluteFile().toPath().normalize();
    }
}
