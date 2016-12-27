package org.openas2.partner;

import java.util.List;
import java.util.Map;

import org.openas2.Component;
import org.openas2.OpenAS2Exception;
import org.openas2.message.Message;
import org.openas2.message.MessageMDN;

/**
 * original author unknown
 * 
 * added getPartners method
 * @author joseph mcverry
 *
 */
public interface PartnershipFactory extends Component {
    public static final String COMPID_PARTNERSHIP_FACTORY = "partnershipfactory";

    // throws an exception if the partnership doesn't exist
    public Partnership getPartnership(Partnership p, boolean reverseLookup) throws OpenAS2Exception;

    // looks up and fills in any header info for a specific msg's partnership
    public void updatePartnership(Message msg, boolean overwrite) throws OpenAS2Exception;

    // looks up and fills in any header info for a specific msg's partnership
    public void updatePartnership(MessageMDN mdn, boolean overwrite) throws OpenAS2Exception;

    public void setPartnerships(List<Partnership> list);

    public List<Partnership> getPartnerships();
    
    public Map<String,Object> getPartners();
}