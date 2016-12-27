package org.openas2.lib.partner;

import org.openas2.lib.message.AS1Message;
import org.openas2.lib.message.AS2Message;
import org.openas2.lib.message.EDIINTMessage;

public class BasicPartnershipChooser implements IPartnershipChooser {
    private IPartnerStore partnerStore;

    public BasicPartnershipChooser(IPartnerStore partnerStore) {
        super();
        this.partnerStore = partnerStore;
    }

    public IPartnerStore getPartnerStore() {
        return partnerStore;
    }

    public IPartnership getPartnership(EDIINTMessage msg) throws PartnerException {
        // search all partnerships for a match
        IPartnerStore store = getPartnerStore();
        String[] aliases = store.getPartnerships();
        for (int i = 0; i < aliases.length; i++) {
            IPartnership partnership = store.getPartnership(aliases[i]);
            if (matches(partnership, msg)) {
                return partnership;
            }
        }

        throw new PartnerException("Partnership not found");
    }

    protected boolean matches(IPartnership partnership, EDIINTMessage msg) {
        // get the ID's from the message
        String senderId = msg.getSenderID();
        String receiverId = msg.getReceiverID();

        // get the protocol ID's from the partnership
        String partnerSenderId = null;
        String partnerReceiverId = null;
        if (msg instanceof AS1Message) {
            partnerSenderId = partnership.getSender().getAs1Id();
            partnerReceiverId = partnership.getReceiver().getAs1Id();
        } else if (msg instanceof AS2Message) {
            partnerSenderId = partnership.getSender().getAs2Id();
            partnerReceiverId = partnership.getReceiver().getAs2Id();
        }

        // check for a match
        if (partnerSenderId != null && partnerSenderId.equals(senderId) && partnerReceiverId != null
                && partnerReceiverId.equals(receiverId)) {
            return true;
        }
        return false;
    }

}