package org.openas2.lib.partner;

import java.util.HashMap;
import java.util.Map;

import org.openas2.lib.util.GeneralUtil;

public class BasicPartnerStore implements IPartnerStore {

	private Map<String, Object> partners;
	private Map<String, Object> partnerships;

	public BasicPartnerStore() {
		super();
	}

	public IPartner createPartner() {
		return new BasicPartner();
	}

	public IPartnership createPartnership() {
		return new BasicPartnership();
	}

	public String[] getPartners() {
		return GeneralUtil.convertKeys(getPartnersMap());
	}

	public IPartner getPartner(String alias) {
		return (IPartner) getPartnersMap().get(alias);
	}

	public void setPartner(String alias, IPartner partner) {
		getPartnersMap().put(alias, partner);
	}

	public String getAlias(IPartner partner) {
		return (String) GeneralUtil.getKey(getPartnersMap(), partner);
	}

	public void removePartner(String alias) {
		getPartnersMap().remove(alias);
	}

	public String[] getPartnerships() {
		return GeneralUtil.convertKeys(getPartnershipsMap());
	}

	public IPartnership getPartnership(String alias) {
		return (IPartnership) getPartnershipsMap().get(alias);
	}

	public void setPartnership(String alias, IPartnership partnership) {
		getPartnershipsMap().put(alias, partnership);
	}

	public String getAlias(IPartnership partnership) {
		return (String) GeneralUtil.getKey(getPartnershipsMap(), partnership);
	}

	public void removePartnership(String alias) {
		getPartnershipsMap().remove(alias);
	}

	protected Map<String, Object> getPartnersMap() {
		if (partners == null) {
			partners = new HashMap<String, Object>();
		}
		return partners;
	}

	protected Map<String, Object> getPartnershipsMap() {
		if (partnerships == null) {
			partnerships = new HashMap<String, Object>();
		}
		return partnerships;
	}
}
