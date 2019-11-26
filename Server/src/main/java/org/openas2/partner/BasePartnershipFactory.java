package org.openas2.partner;

import org.openas2.BaseComponent;
import org.openas2.OpenAS2Exception;
import org.openas2.message.FileAttribute;
import org.openas2.message.Message;
import org.openas2.message.MessageMDN;
import org.openas2.params.MessageParameters;
import org.openas2.params.ParameterParser;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BasePartnershipFactory extends BaseComponent implements PartnershipFactory {
    private List<Partnership> partnerships;

    public Partnership getPartnership(Partnership p, boolean reverseLookup) throws OpenAS2Exception {
        Partnership ps = (p.getName() == null) ? null : getPartnership(p.getName());

        if (ps == null) {
            if (reverseLookup) {
                ps = getPartnership(p.getReceiverIDs(), p.getSenderIDs());
            } else {
                ps = getPartnership(p.getSenderIDs(), p.getReceiverIDs());
            }
        }

        if (ps == null) {
            throw new PartnershipNotFoundException(p);
        }

        return ps;
    }

    public void setPartnerships(List<Partnership> list) {
        partnerships = list;
    }

    public List<Partnership> getPartnerships() {
        if (partnerships == null) {
            partnerships = new ArrayList<Partnership>();
        }

        return partnerships;
    }

    public void updatePartnership(Message msg, boolean overwrite) throws OpenAS2Exception {
        // Fill in any available partnership information
        Partnership partnership = getPartnership(msg.getPartnership(), false);
        msg.getPartnership().copy(partnership);

        processFilenameBasedAttribs(msg);

        // Set attributes
        if (overwrite) {
            String subject = partnership.getAttribute(Partnership.PA_SUBJECT);
            if (subject != null) {
                msg.setSubject(ParameterParser.parse(subject, new MessageParameters(msg)));
            }
        }
    }

    public void processFilenameBasedAttribs(Message msg) throws OpenAS2Exception {
        // Now set dynamic parms based on file name if configured to
        String filename = msg.getAttribute(FileAttribute.MA_FILENAME);
        if (filename == null) {
            // If invoked processing an MDN might be somewhere else...
            filename = msg.getPayloadFilename();
            if (filename == null) {
                return;
            }
        }
        String filenameToParmsList = msg.getPartnership().getAttribute(Partnership.PAIB_NAMES_FROM_FILENAME);
        if (filenameToParmsList == null || filenameToParmsList.length() < 1) {
            return;
        }
        String regex = msg.getPartnership().getAttribute(Partnership.PAIB_VALUES_REGEX_ON_FILENAME);
        if (regex == null) {
            return;
        }

        String[] headerNames = filenameToParmsList.split("\\s*,\\s*");
        Pattern p = Pattern.compile(regex);
        Matcher m = p.matcher(filename);
        if (!m.find() || m.groupCount() != headerNames.length) {
            throw new OpenAS2Exception("Could not match filename (" + filename + ") to parameters required using the regex provided (" + regex + "): " + (m.find() ? ("Mismatch in parameter count to extracted group count: " + headerNames.length + "::" + m.groupCount()) : "No match found in filename"));
        }
        for (int i = 0; i < headerNames.length; i++) {
            msg.setAttribute(headerNames[i], m.group(i + 1));
        }
    }

    public void updatePartnership(MessageMDN mdn, boolean processFilenameAttribs) throws OpenAS2Exception {
        // Fill in any available partnership information
        // the MDN partnership should be the one used to send original message hence reverse lookup
        Partnership partnership = getPartnership(mdn.getPartnership(), true);
        mdn.getPartnership().copy(partnership);
        if (processFilenameAttribs) {
            processFilenameBasedAttribs(mdn.getMessage());
        }
    }

    protected Partnership getPartnership(Map<String, Object> senderIDs, Map<String, Object> receiverIDs) {
        Iterator<Partnership> psIt = getPartnerships().iterator();
        Partnership currentPs;
        Map<String, Object> currentSids;
        Map<String, Object> currentRids;

        while (psIt.hasNext()) {
            currentPs = psIt.next();
            currentSids = currentPs.getSenderIDs();
            currentRids = currentPs.getReceiverIDs();

            if (compareMap(senderIDs, currentSids) && compareMap(receiverIDs, currentRids)) {
                return currentPs;
            }
        }

        return null;
    }

    protected Partnership getPartnership(List<?> partnerships, String name) {
        Iterator<?> psIt = partnerships.iterator();
        Partnership currentPs;
        String currentName;

        while (psIt.hasNext()) {
            currentPs = (Partnership) psIt.next();
            currentName = currentPs.getName();

            if ((currentName != null) && currentName.equals(name)) {
                return currentPs;
            }
        }

        return null;
    }

    protected Partnership getPartnership(String name) throws OpenAS2Exception {
        return getPartnership(getPartnerships(), name);
    }

    // returns true if all values in searchIds match values in partnerIds
    protected boolean compareMap(Map<String, Object> searchIds, Map<String, Object> partnerIds) {
        Iterator<Map.Entry<String, Object>> searchIt = searchIds.entrySet().iterator();

        if (!searchIt.hasNext()) {
            return false;
        }

        Map.Entry<String, Object> searchEntry;
        String searchKey;
        Object searchValue;
        Object partnerValue;

        while (searchIt.hasNext()) {
            searchEntry = searchIt.next();
            searchKey = searchEntry.getKey();
            searchValue = searchEntry.getValue();
            partnerValue = partnerIds.get(searchKey);

            if ((searchValue == null) && (partnerValue != null)) {
                return false;
            } else if ((searchValue != null) && (partnerValue == null)) {
                return false;
            } else if (!searchValue.equals(partnerValue)) {
                return false;
            }
        }

        return true;
    }
}
