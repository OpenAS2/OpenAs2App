package org.openas2.partner;

import org.openas2.OpenAS2Exception;


public interface RefreshablePartnershipFactory {
    public void refresh() throws OpenAS2Exception;
}
