package org.openas2.lib.partner;


public interface IPartnerStore {
    IPartner createPartner();
    
    IPartnership createPartnership();
    
    String[] getPartners();

    IPartner getPartner(String alias);

    void setPartner(String alias, IPartner partner);

    String getAlias(IPartner partner);

    void removePartner(String alias);

    String[] getPartnerships();

    IPartnership getPartnership(String alias);

    void setPartnership(String alias, IPartnership partnership);

    String getAlias(IPartnership partnership);

    void removePartnership(String alias);
}
