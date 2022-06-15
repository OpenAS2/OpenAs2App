package org.openas2;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.cert.CertificateFactory;
import org.openas2.lib.message.AS2Standards;
import org.openas2.message.MessageFactory;
import org.openas2.partner.PartnershipFactory;
import org.openas2.processor.Processor;
import org.openas2.processor.ProcessorModule;
import org.openas2.processor.receiver.DirectoryPollingModule;
import org.openas2.util.Properties;
import org.openas2.util.XMLUtil;
import org.w3c.dom.Node;

import javax.activation.CommandMap;
import javax.activation.MailcapCommandMap;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public abstract class BaseSession implements Session {
    private Map<String, Component> components = new HashMap<String, Component>();
    private String baseDirectory;

    protected static final Log LOGGER = LogFactory.getLog(XMLSession.class.getSimpleName());

    private Map<String, Map<String, Object>> polledDirectories = new HashMap<String, Map<String, Object>>();

    /**
     * Creates a <code>BaseSession</code> object, then calls the <code>init()</code> method.
     *
     * @throws OpenAS2Exception - - Houston we have a problem
     * @see #init()
     */
    public BaseSession() throws OpenAS2Exception {
        init();
    }

    @Override
    public void start() throws OpenAS2Exception {
        getProcessor().startActiveModules();
        startPartnershipPollers();
    }

    @Override
    public void stop() throws Exception {
        destroyPartnershipPollers();
        for (Component component : components.values()) {
            component.destroy();
        }
    }

    public CertificateFactory getCertificateFactory() throws ComponentNotFoundException {
        return (CertificateFactory) getComponent(CertificateFactory.COMPID_CERTIFICATE_FACTORY);
    }

    public Map<String, Map<String, Object>> getPolledDirectories() {
        return polledDirectories;
    }

    public void setPolledDirectories(Map<String, Map<String, Object>> polledDirectories) {
        this.polledDirectories = polledDirectories;
    }

    /**
     * Registers a component to a specified ID.
     *
     * @param componentID registers the component to this ID
     * @param comp        component to register
     * @see Component
     */
    public void setComponent(String componentID, Component comp) {
        Map<String, Component> objects = getComponents();
        objects.put(componentID, comp);
    }

    public Component getComponent(String componentID) throws ComponentNotFoundException {
        Map<String, Component> comps = getComponents();
        Component comp = comps.get(componentID);

        if (comp == null) {
            throw new ComponentNotFoundException(componentID);
        }

        return comp;
    }

    public Map<String, Component> getComponents() {
        return components;
    }

    public PartnershipFactory getPartnershipFactory() throws ComponentNotFoundException {
        return (PartnershipFactory) getComponent(PartnershipFactory.COMPID_PARTNERSHIP_FACTORY);
    }

    public MessageFactory getMessageFactory() throws ComponentNotFoundException {
        return (MessageFactory) getComponent(MessageFactory.COMPID_MESSAGE_FACTORY);
    }

    public Processor getProcessor() throws ComponentNotFoundException {
        return (Processor) getComponent(Processor.COMPID_PROCESSOR);
    }

    /**
     * This method is called by the <code>BaseSession</code> constructor to set up any global
     * configuration settings.
     *
     * @throws OpenAS2Exception If an error occurs while initializing systems
     */
    protected void init() throws OpenAS2Exception {
        initJavaMail();
    }

    /**
     * Adds a group of content handlers to the Mailcap <code>CommandMap</code>. These handlers are
     * used by the JavaMail API to encode and decode information of specific mime types.
     *
     * @throws OpenAS2Exception If an error occurs while initializing mime types
     */
    private void initJavaMail() throws OpenAS2Exception {
        MailcapCommandMap mc = (MailcapCommandMap) CommandMap.getDefaultCommandMap();
        mc.addMailcap(AS2Standards.DISPOSITION_TYPE + ";; x-java-content-handler=org.openas2.lib.util.javamail.DispositionDataContentHandler");
        CommandMap.setDefaultCommandMap(mc);
    }

    private void checkPollerModuleConfig(String pollerDir) throws OpenAS2Exception {
        if (polledDirectories.containsKey(pollerDir)) {
            Map<String, Object> meta = polledDirectories.get(pollerDir);
            throw new OpenAS2Exception("Directory already being polled from config in " + meta.get("configSource") + " for the " + meta.get("partnershipName") + " partnership: " + pollerDir);
        }
    }

    private void trackPollerModule(String pollerDir, String partnershipName, String configSource, ProcessorModule pollerInstance) {
        Map<String, Object> meta = new HashMap<String, Object>();
        meta.put("partnershipName", partnershipName);
        meta.put("configSource", configSource);
        meta.put("pollerInstance", pollerInstance);
        polledDirectories.put(pollerDir, meta);
    }

    public void startPartnershipPollers() throws OpenAS2Exception {
        for (Map.Entry<String, Map<String, Object>> entry : polledDirectories.entrySet()) {
            Map<String, Object> meta = entry.getValue();
            DirectoryPollingModule poller = (DirectoryPollingModule) meta.get("pollerInstance");
            LOGGER.trace("Starting directory poller:" + meta);
            poller.start();
        }
    }

    public void destroyPartnershipPollers() {
        LOGGER.trace("Destroying partnership pollers...");
        List<String> stoppedPollerKeys = new ArrayList<String>();
        for (Map.Entry<String, Map<String, Object>> entry : polledDirectories.entrySet()) {
            Map<String, Object> meta = entry.getValue();
            DirectoryPollingModule poller = (DirectoryPollingModule) meta.get("pollerInstance");
            try {
                LOGGER.trace("Destroying poller:" + meta);
                if (poller.isRunning()) {
                        poller.stop();
                        poller = null;
                }
                // track the removed pollers keys to update the map after iteration to avoid concurrent modification error
                stoppedPollerKeys.add(entry.getKey());
            } catch (Exception e) {
                // something went wrong stopping it - report and keep going but make sure the key is still removed
                LOGGER.error("Failed to stop a partnership poller for directory " + entry.getKey() + ": " + meta, e);
                stoppedPollerKeys.add(entry.getKey());
            } 
        }
        for (String pollerKey : stoppedPollerKeys) {
            // Remove the poller entry in the map now that we have killed the active poller
            polledDirectories.remove(pollerKey);
            LOGGER.trace("Removed poller from cache map:" + pollerKey);
        }
    }

    public DirectoryPollingModule getPartnershipPoller(String partnershipName) {
        for (Map.Entry<String, Map<String, Object>> entry : polledDirectories.entrySet()) {
            Map<String, Object> meta = entry.getValue();
            if (partnershipName.equals(meta.get("partnershipName"))) {
                return (DirectoryPollingModule) meta.get("pollerInstance");
            }
        }
        return null;
    }

    public DirectoryPollingModule getPartnershipPoller(String senderAs2Id, String receiverAs2Id) {
        // search by the defaults since the partnershipName used as the key is not consistent due to config.xml defined pollers issue
        // so iterate over all and search by the AS2 ID's in the defaults element
        for (Map.Entry<String, Map<String, Object>> entry : polledDirectories.entrySet()) {
            Map<String, Object> meta = entry.getValue();
            DirectoryPollingModule pollerModule = (DirectoryPollingModule)meta.get("pollerInstance");
            String defaults = pollerModule.getParameters().get("defaults");
            if (defaults != null && defaults.contains("receiver.as2_id=" + receiverAs2Id) && defaults.contains("sender.as2_id=" + senderAs2Id)) {
                    return pollerModule;
            }
        }
        return null;
    }

    public void loadPartnershipPoller(Node moduleNode, String partnershipName, String configSource) throws OpenAS2Exception {
        DirectoryPollingModule procmod = (DirectoryPollingModule) XMLUtil.getComponent(moduleNode, this);
        String pollerDir = procmod.getParameters().get(DirectoryPollingModule.PARAM_OUTBOX_DIRECTORY);
        try {
            checkPollerModuleConfig(pollerDir);
        } catch (OpenAS2Exception oae) {
            try {
                procmod.stop();
                procmod = null;
            } catch (Exception e) {
                throw new OpenAS2Exception("Failed to destroy a partnership poller that has config errors", e);
            }
            throw new OpenAS2Exception("Partnership poller cannot be loaded because there is a configuration error: " + partnershipName, oae);
        }
        /*
        Processor proc = (Processor)getComponent(Processor.COMPID_PROCESSOR);
        proc.getModules().add(procmod);
        */
        trackPollerModule(pollerDir, partnershipName, configSource, procmod);
    }

    public String getBaseDirectory() {
        return baseDirectory;
    }

    void setBaseDirectory(String dir) {
        baseDirectory = dir;
        Properties.setProperty(Properties.APP_BASE_DIR_PROP, baseDirectory);
    }

}
