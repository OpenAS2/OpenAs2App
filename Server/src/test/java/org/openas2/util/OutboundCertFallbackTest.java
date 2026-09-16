package org.openas2.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.openas2.DispositionException;
import org.openas2.message.AS2Message;
import org.openas2.message.Message;
import org.openas2.partner.Partnership;

/**
 * Verifies how a partner's rejection of an outbound message is mapped onto a decision to retry with a
 * fallback certificate.
 * <p>
 * Signing and encrypting always succeed locally whichever certificate is used, so the only signal that
 * the wrong one was chosen is the partner reporting it in the MDN disposition. These tests pin the
 * mapping from the reported reason to the side whose certificate is implicated, and the two guards that
 * keep a message failing for some other reason behaving exactly as it did before: a fallback must
 * actually be configured, and the switch happens at most once.
 */
public class OutboundCertFallbackTest {

    private static final String PRIMARY_OURS = "mycompany";
    private static final String FALLBACK_OURS = "mycompany_new";
    private static final String PRIMARY_THEIRS = "partnera";
    private static final String FALLBACK_THEIRS = "partnera_new";

    /* ---------------------------------------------- the partner could not decrypt: their certificate */

    @Test
    public void partnerCannotDecryptSwitchesToTheirFallbackCertificate() throws Exception {
        Message msg = message(true, true);

        assertTrue(AS2Util.switchToFallbackCertificate(msg, rejection("decryption-failed")));

        assertEquals(Message.FALLBACK_STATE_IN_USE, msg.getAttribute(Message.MA_RECEIVER_ALIAS_FALLBACK_STATE));
        assertNull(msg.getAttribute(Message.MA_SENDER_ALIAS_FALLBACK_STATE),
                "a decryption failure says nothing about our own signing certificate");
        assertEquals(FALLBACK_THEIRS,
                msg.getPartnership().getAliasOrFallback(Partnership.PTYPE_RECEIVER, true),
                "the resend must encrypt to the partner's fallback certificate");
    }

    @Test
    public void partnerCannotDecryptWithNoFallbackConfiguredChangesNothing() throws Exception {
        Message msg = message(true, false);

        assertFalse(AS2Util.switchToFallbackCertificate(msg, rejection("decryption-failed")));

        assertNull(msg.getAttribute(Message.MA_RECEIVER_ALIAS_FALLBACK_STATE));
        assertEquals(PRIMARY_THEIRS, msg.getPartnership().getAliasOrFallback(Partnership.PTYPE_RECEIVER, true),
                "with nothing to fall back to the primary must still be used");
    }

    /* ------------------------------------------- the partner could not authenticate: our certificate */

    @Test
    public void partnerCannotAuthenticateSwitchesToOurFallbackCertificate() throws Exception {
        Message msg = message(true, true);

        assertTrue(AS2Util.switchToFallbackCertificate(msg, rejection("authentication-failed")));

        assertEquals(Message.FALLBACK_STATE_IN_USE, msg.getAttribute(Message.MA_SENDER_ALIAS_FALLBACK_STATE));
        assertNull(msg.getAttribute(Message.MA_RECEIVER_ALIAS_FALLBACK_STATE),
                "an authentication failure says nothing about the partner's encryption certificate");
        assertEquals(FALLBACK_OURS, msg.getPartnership().getAliasOrFallback(Partnership.PTYPE_SENDER, true),
                "the resend must sign with our fallback certificate");
    }

    @Test
    public void integrityCheckFailureIsTreatedAsASignatureProblem() throws Exception {
        // Some implementations report a MIC mismatch this way rather than a signature problem, which is
        // why the switch is gated on a fallback being configured and only happening once
        Message msg = message(true, true);

        assertTrue(AS2Util.switchToFallbackCertificate(msg, rejection("integrity-check-failed")));

        assertEquals(Message.FALLBACK_STATE_IN_USE, msg.getAttribute(Message.MA_SENDER_ALIAS_FALLBACK_STATE));
    }

    @Test
    public void integrityCheckFailureWithNoSenderFallbackChangesNothing() throws Exception {
        // The common case for a MIC mismatch: nobody is rotating, so nothing should change
        Message msg = message(false, true);

        assertFalse(AS2Util.switchToFallbackCertificate(msg, rejection("integrity-check-failed")));

        assertNull(msg.getAttribute(Message.MA_SENDER_ALIAS_FALLBACK_STATE));
    }

    /* ------------------------------------------------------------------------------------- the guards */

    @Test
    public void anUnrelatedRejectionReasonIsLeftAlone() throws Exception {
        Message msg = message(true, true);

        assertFalse(AS2Util.switchToFallbackCertificate(msg, rejection("unexpected-processing-error")));

        assertNull(msg.getAttribute(Message.MA_SENDER_ALIAS_FALLBACK_STATE));
        assertNull(msg.getAttribute(Message.MA_RECEIVER_ALIAS_FALLBACK_STATE));
    }

    @Test
    public void aRejectedFallbackRevertsToThePrimaryAndIsNeverTriedAgain() throws Exception {
        Message msg = message(true, true);

        // First rejection: try the fallback
        assertTrue(AS2Util.switchToFallbackCertificate(msg, rejection("decryption-failed")));
        assertEquals(Message.FALLBACK_STATE_IN_USE, msg.getAttribute(Message.MA_RECEIVER_ALIAS_FALLBACK_STATE));
        assertEquals(FALLBACK_THEIRS, AS2Util.resolveOutboundAlias(msg, Partnership.PTYPE_RECEIVER));

        // Second rejection: the fallback is wrong too, so go back to the primary rather than spending
        // every remaining retry on a certificate the partner has also refused
        assertFalse(AS2Util.switchToFallbackCertificate(msg, rejection("decryption-failed")));
        assertEquals(Message.FALLBACK_STATE_EXHAUSTED, msg.getAttribute(Message.MA_RECEIVER_ALIAS_FALLBACK_STATE));
        assertEquals(PRIMARY_THEIRS, AS2Util.resolveOutboundAlias(msg, Partnership.PTYPE_RECEIVER),
                "the remaining retries must use the primary certificate");

        // Third rejection: must not start the cycle over
        assertFalse(AS2Util.switchToFallbackCertificate(msg, rejection("decryption-failed")),
                "switching again would ping-pong between the two certificates");
        assertEquals(Message.FALLBACK_STATE_EXHAUSTED, msg.getAttribute(Message.MA_RECEIVER_ALIAS_FALLBACK_STATE));
        assertEquals(PRIMARY_THEIRS, AS2Util.resolveOutboundAlias(msg, Partnership.PTYPE_RECEIVER));
    }

    @Test
    public void theFallbackStateSurvivesTheMessageBeingRestoredForAResend() throws Exception {
        /*
         * A resend after the first one replaces the message with the copy serialised when it was first
         * sent, which predates any fallback decision. Without the state being carried forward the
         * resend would quietly go back to the primary certificate while the log said otherwise.
         */
        Message live = message(true, true);
        AS2Util.switchToFallbackCertificate(live, rejection("decryption-failed"));

        Message restoredFromDisk = message(true, true);
        assertNull(restoredFromDisk.getAttribute(Message.MA_RECEIVER_ALIAS_FALLBACK_STATE),
                "the stored copy predates the decision");

        AS2Util.carryFallbackState(live, restoredFromDisk);

        assertEquals(Message.FALLBACK_STATE_IN_USE, restoredFromDisk.getAttribute(Message.MA_RECEIVER_ALIAS_FALLBACK_STATE));
        assertEquals(FALLBACK_THEIRS, AS2Util.resolveOutboundAlias(restoredFromDisk, Partnership.PTYPE_RECEIVER),
                "the restored message must still use the fallback certificate");
    }

    @Test
    public void carryingStateDoesNotInventOneThatWasNeverSet() throws Exception {
        Message live = message(true, true);
        Message restored = message(true, true);

        AS2Util.carryFallbackState(live, restored);

        assertNull(restored.getAttribute(Message.MA_RECEIVER_ALIAS_FALLBACK_STATE));
        assertNull(restored.getAttribute(Message.MA_SENDER_ALIAS_FALLBACK_STATE));
        assertEquals(PRIMARY_THEIRS, AS2Util.resolveOutboundAlias(restored, Partnership.PTYPE_RECEIVER));
    }

    @Test
    public void aRejectionWithNoParsableReasonIsLeftAlone() throws Exception {
        Message msg = message(true, true);

        DispositionException noDescription = new DispositionException(
                new DispositionType("automatic-action", "MDN-sent-automatically", "processed"), "no reason given");
        assertFalse(AS2Util.switchToFallbackCertificate(msg, noDescription));
        assertNull(msg.getAttribute(Message.MA_RECEIVER_ALIAS_FALLBACK_STATE));
    }

    @Test
    public void theReasonIsMatchedRegardlessOfCase() throws Exception {
        // Implementations differ on the casing of the disposition text they return
        Message msg = message(true, true);

        assertTrue(AS2Util.switchToFallbackCertificate(msg, rejection("Decryption-Failed")));

        assertEquals(Message.FALLBACK_STATE_IN_USE, msg.getAttribute(Message.MA_RECEIVER_ALIAS_FALLBACK_STATE));
    }

    /* ------------------------------------------- reporting that a fallback is what made a send work */

    @Test
    public void aMessageThatNeededNoFallbackReportsNothing() throws Exception {
        Message msg = message(true, true);

        assertNull(AS2Util.fallbackCertificateInUseMessage(msg, "PartnerA"),
                "a normal send must not tell the operator to complete a switchover");
    }

    @Test
    public void aSendRescuedByTheirFallbackReportsThatTheyHaveSwitched() throws Exception {
        Message msg = message(true, true);
        AS2Util.switchToFallbackCertificate(msg, rejection("decryption-failed"));

        String advisory = AS2Util.fallbackCertificateInUseMessage(msg, "PartnerA");

        assertEquals(AS2Util.LOG_MSG_PARTNER_CERT_SWITCHED + "PartnerA", advisory);
    }

    @Test
    public void aSendRescuedByOurFallbackReportsThatTheyHaveOurNewCertificate() throws Exception {
        Message msg = message(true, true);
        AS2Util.switchToFallbackCertificate(msg, rejection("authentication-failed"));

        String advisory = AS2Util.fallbackCertificateInUseMessage(msg, "PartnerA");

        assertEquals(AS2Util.LOG_MSG_OUR_CERT_SWITCHED + "PartnerA", advisory);
    }

    @Test
    public void theAdvisoryWordingIsSharedWithTheInboundPathsSoOneSearchFindsEveryOverlap() {
        // The inbound handler logs these same two strings, so changing either here without changing
        // the other would split what operators have to search for
        assertTrue(AS2Util.LOG_MSG_OUR_CERT_SWITCHED.startsWith("Partner has updated our certificate."));
        assertTrue(AS2Util.LOG_MSG_PARTNER_CERT_SWITCHED.startsWith("Partner has updated their certificate."));
        assertTrue(AS2Util.LOG_MSG_OUR_CERT_SWITCHED.endsWith("for the partner: "));
        assertTrue(AS2Util.LOG_MSG_PARTNER_CERT_SWITCHED.endsWith("for the partner: "));
    }

    /* ------------------------------------------------------------------ the resolver the sender uses */

    @Test
    public void theResolverOnlyUsesTheFallbackWhenItIsAskedFor() throws Exception {
        Partnership p = partnership(true, true);

        assertEquals(PRIMARY_OURS, p.getAliasOrFallback(Partnership.PTYPE_SENDER, false));
        assertEquals(FALLBACK_OURS, p.getAliasOrFallback(Partnership.PTYPE_SENDER, true));
        assertEquals(PRIMARY_THEIRS, p.getAliasOrFallback(Partnership.PTYPE_RECEIVER, false));
        assertEquals(FALLBACK_THEIRS, p.getAliasOrFallback(Partnership.PTYPE_RECEIVER, true));
    }

    @Test
    public void theResolverFallsBackToThePrimaryWhenNoFallbackIsConfigured() throws Exception {
        Partnership p = partnership(false, false);

        assertEquals(PRIMARY_OURS, p.getAliasOrFallback(Partnership.PTYPE_SENDER, true),
                "asking for a fallback that is not configured must not fail");
        assertEquals(PRIMARY_THEIRS, p.getAliasOrFallback(Partnership.PTYPE_RECEIVER, true));
    }

    /* --------------------------------------- the resolver the sender actually calls for each side */

    @Test
    public void theSenderResolvesThePrimaryUntilSomethingIsFlagged() throws Exception {
        Message msg = message(true, true);

        assertEquals(PRIMARY_OURS, AS2Util.resolveOutboundAlias(msg, Partnership.PTYPE_SENDER));
        assertEquals(PRIMARY_THEIRS, AS2Util.resolveOutboundAlias(msg, Partnership.PTYPE_RECEIVER));
    }

    @Test
    public void flaggingOneSideDoesNotChangeTheOther() throws Exception {
        // The side and its flag are paired in one place precisely so this cannot cross-wire
        Message msg = message(true, true);
        AS2Util.switchToFallbackCertificate(msg, rejection("decryption-failed"));

        assertEquals(FALLBACK_THEIRS, AS2Util.resolveOutboundAlias(msg, Partnership.PTYPE_RECEIVER),
                "the rejected side must switch");
        assertEquals(PRIMARY_OURS, AS2Util.resolveOutboundAlias(msg, Partnership.PTYPE_SENDER),
                "our signing certificate must be left alone by a decryption failure");
    }

    @Test
    public void flaggingOurSideSwitchesOnlyTheSigningCertificate() throws Exception {
        Message msg = message(true, true);
        AS2Util.switchToFallbackCertificate(msg, rejection("authentication-failed"));

        assertEquals(FALLBACK_OURS, AS2Util.resolveOutboundAlias(msg, Partnership.PTYPE_SENDER));
        assertEquals(PRIMARY_THEIRS, AS2Util.resolveOutboundAlias(msg, Partnership.PTYPE_RECEIVER));
    }

    @Test
    public void aFlaggedSideWithNoFallbackStillResolvesThePrimary() throws Exception {
        // The fallback could be removed from the configuration between the rejection and the resend,
        // which must not leave the send unable to resolve a certificate at all
        Message msg = message(true, true);
        AS2Util.switchToFallbackCertificate(msg, rejection("decryption-failed"));
        msg.getPartnership().getReceiverIDs().remove(Partnership.PID_X509_ALIAS_FALLBACK);

        assertEquals(PRIMARY_THEIRS, AS2Util.resolveOutboundAlias(msg, Partnership.PTYPE_RECEIVER));
    }

    /* ------------------------------------------------------------------------------------- fixtures */

    private DispositionException rejection(String reason) {
        return new DispositionException(
                new DispositionType("automatic-action", "MDN-sent-automatically", "processed", "Error", reason),
                "Partner rejected the message");
    }

    private Message message(boolean withSenderFallback, boolean withReceiverFallback) {
        AS2Message msg = new AS2Message();
        msg.getPartnership().copy(partnership(withSenderFallback, withReceiverFallback));
        return msg;
    }

    private Partnership partnership(boolean withSenderFallback, boolean withReceiverFallback) {
        Partnership p = new Partnership();
        p.setName("MyCompany-to-PartnerA");
        p.getSenderIDs().put(Partnership.PID_X509_ALIAS, PRIMARY_OURS);
        p.getReceiverIDs().put(Partnership.PID_X509_ALIAS, PRIMARY_THEIRS);
        if (withSenderFallback) {
            p.getSenderIDs().put(Partnership.PID_X509_ALIAS_FALLBACK, FALLBACK_OURS);
        }
        if (withReceiverFallback) {
            p.getReceiverIDs().put(Partnership.PID_X509_ALIAS_FALLBACK, FALLBACK_THEIRS);
        }
        return p;
    }
}
