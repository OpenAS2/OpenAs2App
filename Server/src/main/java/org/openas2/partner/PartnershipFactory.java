package org.openas2.partner;

import org.openas2.Component;
import org.openas2.OpenAS2Exception;
import org.openas2.message.Message;
import org.openas2.message.MessageMDN;

import java.util.List;
import java.util.Map;

/**
 * original author unknown
 * <p>
 * added getPartners method
 *
 * @author joseph mcverry
 */
public interface PartnershipFactory extends Component {
    String COMPID_PARTNERSHIP_FACTORY = "partnershipfactory";

    // throws an exception if the partnership doesn't exist
    Partnership getPartnership(Partnership p, boolean reverseLookup) throws OpenAS2Exception;

    // looks up and fills in any header info for a specific msg's partnership
    void updatePartnership(Message msg, boolean overwrite) throws OpenAS2Exception;

    // looks up and fills in any header info for a specific msg's partnership
    void updatePartnership(MessageMDN mdn, boolean overwrite) throws OpenAS2Exception;

    void setPartnerships(List<Partnership> list);

    List<Partnership> getPartnerships();

    Map<String, Object> getPartners();
}
