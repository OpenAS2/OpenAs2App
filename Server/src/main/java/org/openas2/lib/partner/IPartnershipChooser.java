package org.openas2.lib.partner;

import org.openas2.lib.message.EDIINTMessage;


public interface IPartnershipChooser {
    IPartnerStore getPartnerStore();
    
    IPartnership getPartnership(EDIINTMessage msg) throws PartnerException;
}
