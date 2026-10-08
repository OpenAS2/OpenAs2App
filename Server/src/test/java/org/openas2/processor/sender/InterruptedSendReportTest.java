package org.openas2.processor.sender;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.openas2.message.AS2Message;
import org.openas2.message.FileAttribute;
import org.openas2.partner.Partnership;
import org.openas2.processor.sender.InterruptedSendReport.Finding;
import org.openas2.processor.sender.InterruptedSendReport.Status;

import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Verifies how files left in the pending folder at startup are told apart.
 * <p>
 * A file is moved out of the outbox before it is sent, and its pending information is written just
 * before it is transmitted, so when a send is cut off the file is left in the pending folder. One with
 * no pending information was never transmitted, and nothing else ever picks it up. The others have to
 * be told apart from sends that are legitimately still pending, a queued resend or an asynchronous MDN
 * that can still arrive, which must not be reported.
 */
public class InterruptedSendReportTest {

    @TempDir
    Path tmp;

    private File pendingDir;
    private File pendingInfoDir;
    private File resendDir;

    @BeforeEach
    public void createFolders() throws Exception {
        pendingDir = Files.createDirectories(tmp.resolve("pendingMDN")).toFile();
        pendingInfoDir = Files.createDirectories(tmp.resolve("pendinginfoMDN")).toFile();
        resendDir = Files.createDirectories(tmp.resolve("resend")).toFile();
    }

    @Test
    public void aFileWithNoPendingInformationWasNeverSent() throws Exception {
        File payload = payload("invoice-1.edi");

        List<Finding> findings = find();

        assertEquals(1, findings.size());
        assertEquals(payload.getAbsoluteFile(), findings.get(0).getFile().getAbsoluteFile());
        assertEquals(Status.NEVER_SENT, findings.get(0).getStatus());
    }

    @Test
    public void aSynchronousSendThatHadStartedMayHaveBeenDelivered() throws Exception {
        File payload = payload("invoice-2.edi");
        storedMessage(payload, false);
        pendingInfo("MSG-2@openas2test", payload);

        List<Finding> findings = find();

        assertEquals(1, findings.size());
        assertEquals(Status.MAY_HAVE_BEEN_SENT, findings.get(0).getStatus(),
                "the transmission had started, so it must not be called unsent and invite a duplicate");
        assertEquals("MSG-2@openas2test", findings.get(0).getMessageId());
    }

    @Test
    public void aSendWaitingForAnAsynchronousMdnIsNotReported() throws Exception {
        File payload = payload("invoice-3.edi");
        storedMessage(payload, true);
        pendingInfo("MSG-3@openas2test", payload);

        assertEquals(Collections.emptyList(), find(), "an asynchronous MDN can still arrive after a restart");
    }

    @Test
    public void aFileQueuedForResendIsNotReported() throws Exception {
        File payload = payload("invoice-4.edi");
        AS2Message msg = message();
        msg.setAttribute(FileAttribute.MA_PENDINGFILE, payload.getAbsolutePath());
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(new File(resendDir, "resend-1")))) {
            out.writeObject("send");
            out.writeObject("1");
            out.writeObject(msg);
        }

        assertEquals(Collections.emptyList(), find(), "the resender will retry it");
    }

    @Test
    public void theStoredMessageBesideAPendingFileIsNotItselfReported() throws Exception {
        File payload = payload("invoice-5.edi");
        storedMessage(payload, false);

        List<Finding> findings = find();

        assertEquals(1, findings.size(), "only the payload is a file to send: " + findings);
        assertEquals(payload.getName(), findings.get(0).getFile().getName());
    }

    @Test
    public void anUnreadablePendingInformationFileMeansNoFileCanBeCalledUnsent() throws Exception {
        payload("invoice-6.edi");
        // It might have belonged to the payload, so the payload cannot be known to be unsent
        Files.write(new File(pendingInfoDir, "MSG-6@openas2test").toPath(), "not a pending info file".getBytes(StandardCharsets.UTF_8));

        List<Finding> findings = find();

        assertEquals(1, findings.size());
        assertEquals(Status.MAY_HAVE_BEEN_SENT, findings.get(0).getStatus());
    }

    @Test
    public void missingFoldersReportNothing() {
        assertEquals(Collections.emptyList(), InterruptedSendReport.findInterruptedSends(
                new File(tmp.toFile(), "absent"), new File(tmp.toFile(), "absent-info"), Collections.<File>emptyList()));
    }

    /* ------------------------------------------------------------------------------------- fixtures */

    private List<Finding> find() {
        return InterruptedSendReport.findInterruptedSends(pendingDir, pendingInfoDir, Collections.singletonList(resendDir));
    }

    private File payload(String name) throws Exception {
        File file = new File(pendingDir, name);
        Files.write(file.toPath(), "payload".getBytes(StandardCharsets.UTF_8));
        return file;
    }

    /** A message as far as the sender has built it, which always has headers by the time it is stored. */
    private AS2Message message() {
        AS2Message msg = new AS2Message();
        msg.setHeader("AS2-From", "MyCompany_OID");
        msg.setHeader("AS2-To", "PartnerA_OID");
        return msg;
    }

    /** The ".object" file the sender writes beside the payload on the first send. */
    private void storedMessage(File payload, boolean asyncMdn) throws Exception {
        AS2Message msg = message();
        if (asyncMdn) {
            msg.getPartnership().setAttribute(Partnership.PA_AS2_RECEIPT_OPTION, "http://localhost:10081");
        }
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(payload.getPath() + ".object"))) {
            out.writeObject(msg);
        }
    }

    /** The pending information file the sender writes immediately before transmitting, in its format. */
    private void pendingInfo(String messageId, File payload) throws Exception {
        try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(new File(pendingInfoDir, messageId)))) {
            out.writeObject("mic, sha-256");
            out.writeObject("0");
            out.writeObject(payload.getName());
            out.writeObject(payload.getName());
            out.writeObject(pendingDir.getPath() + "/" + payload.getName());
            out.writeObject("error");
            out.writeObject("");
            out.writeObject(new HashMap<String, String>());
        }
    }
}
