package org.openas2;

import org.openas2.app.OpenAS2Server;
import org.openas2.message.AS2Message;
import org.openas2.message.AS2MessageMDN;
import org.openas2.partner.Partnership;
import org.openas2.processor.ProcessorModule;
import org.openas2.processor.receiver.DirectoryPollingModule;
import org.openas2.processor.storage.BaseStorageModule;
import org.openas2.processor.storage.MDNFileModule;
import org.openas2.processor.storage.MessageFileModule;
import org.openas2.processor.storage.StorageModule;
import org.openas2.util.Properties;

import java.io.File;
import java.util.List;

/**
 * Class to manage a partners general metadata when communicating with a specific partner for the purposes of a test.
 * The setup assumes there is a partner who is the "Company" partner and another partner that the "Company" exchanges files with.
 * This includes information such as inbox, outbox, sent and received MDN's etc.
 */
public class TestPartner {
    private String name = null;
    private String partnerName = null;
    private String as2Id = null;
    private String partnerAS2Id = null;
    private Partnership partnership = null;
    private File storageBaseDir = null;
    private File outbox = null;
    private File inbox = null;
    private File sentMDNs = null;
    private File rxdMDNs = null;
    private OpenAS2Server server = null;

    public TestPartner(OpenAS2Server server, Partnership partnership, DirectoryPollingModule dirPollMod) throws Exception {
        this.server = server;
    	this.partnership = partnership;
        this.name = partnership.getSenderID("name");
        this.as2Id = partnership.getSenderID(Partnership.PID_AS2);
        this.partnerName = partnership.getReceiverID("name");
        this.partnerAS2Id = partnership.getReceiverID(Partnership.PID_AS2);
        if (this.partnership == null) {
        	throw new Exception("Require a partnership to configure a test partner.");
        }
        XMLSession session = (XMLSession)server.getSession();
        if (dirPollMod != null) {
        	// Must be a sender so set up the relevant folders for test
                setOutbox(new File(dirPollMod.getParameter(DirectoryPollingModule.PARAM_OUTBOX_DIRECTORY, true)));
        }
        // Create a fake AS2 message to build the directory structures so we know where the files will be stored
        AS2Message msg = new AS2Message();
        msg.setPartnership(partnership);
        // Add an MDN to the msg to be able to configure directories
        new AS2MessageMDN(msg, true);
        List<ProcessorModule> msgStorageModules = session.getProcessor().getModulesSupportingAction(StorageModule.DO_STORE);
        // Just use the first one even if there is more than 1
        MessageFileModule msgFileModule = (MessageFileModule)msgStorageModules.get(0);
        setInbox(msgFileModule.getFile(msg, msgFileModule.getParameter(BaseStorageModule.PARAM_FILENAME, true), "").getAbsoluteFile().getParentFile());
        List<ProcessorModule> mdnStorageModules = session.getProcessor().getModulesSupportingAction(StorageModule.DO_STOREMDN);
        // Just use the first one even if there is more than 1
        MDNFileModule mdnFileModule = (MDNFileModule)mdnStorageModules.get(0);
        File mdnDir = mdnFileModule.getFile(msg, mdnFileModule.getParameter(BaseStorageModule.PARAM_FILENAME, true), "").getAbsoluteFile().getParentFile();
        setSentMDNs(mdnDir);
        setRxdMDNs(mdnDir);

    }

    public String getName() {
        return name;
    }

    public Partnership getPartnership() {
        return partnership;
    }

    public String getPartnerName() {
        return partnerName;
    }

    public File getHome() {
        if (storageBaseDir == null) {
                storageBaseDir = new File(Properties.getProperty("storageBaseDir", null));
        }
        return storageBaseDir;
    }

    public String getAs2Id() {
        return as2Id;
    }

    public void setAs2Id(String as2Id) {
        this.as2Id = as2Id;
    }

    public String getPartnerAS2Id() {
        return partnerAS2Id;
    }

    public File getOutbox() {
        return outbox;
    }

    public void setOutbox(File outbox) {
        this.outbox = outbox;
    }

    public File getInbox() {
        return inbox;
    }

    public void setInbox(File inbox) {
        this.inbox = inbox;
    }

    public File getSentMDNs() {
        return sentMDNs;
    }

    public void setSentMDNs(File sentMDNs) {
        this.sentMDNs = sentMDNs;
    }

    public File getRxdMDNs() {
        return rxdMDNs;
    }

    public void setRxdMDNs(File rxdMDNs) {
        this.rxdMDNs = rxdMDNs;
    }

    public OpenAS2Server getServer() {
        return server;
    }

    public void setServer(OpenAS2Server server) {
        this.server = server;
    }

}
