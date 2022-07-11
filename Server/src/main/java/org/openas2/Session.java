package org.openas2;

import org.openas2.message.MessageFactory;
import org.openas2.cert.CertificateFactory;
import org.openas2.partner.PartnershipFactory;
import org.openas2.processor.Processor;
import org.w3c.dom.Node;

import java.util.Map;


/**
 * The <code>Session</code> interface provides configuration and resource information, and a means for
 * components to access the functionality of other components.
 * The <code>Session</code> has its own lifecycle controlled by two methods {@link #start()} and {@link #stop()}.
 *
 * @author Aaron Silinskas
 * @see Component
 * @see org.openas2.cert.CertificateFactory
 * @see org.openas2.partner.PartnershipFactory
 * @see org.openas2.processor.Processor
 */
public interface Session {

    String DEFAULT_CONTENT_TRANSFER_ENCODING = "binary";
    String LOG_LEVEL_OVERRIDE_KEY = "logging.level.override";

    /**
     * Lifecycle control method.
     *
     * @throws Exception - - Houston we have a problem
     */
    void start() throws Exception;

    /**
     * Lifecycle control method.
     *
     * @throws Exception - - Houston we have a problem
     */
    void stop() throws Exception;

    /**
     * Short-cut method to retrieve a certificate factory.
     *
     * @return the currently registered <code>CertificateFactory</code> component
     * @throws ComponentNotFoundException If a <code>CertificateFactory</code> component has not been
     *                                    registered
     * @see CertificateFactory
     * @see Component
     */
    CertificateFactory getCertificateFactory() throws ComponentNotFoundException;

    /**
     * Gets the <code>Component</code> currently registered with an ID
     *
     * @param componentID ID to search for
     * @return the component registered to the ID or null
     * @throws ComponentNotFoundException If a component is not registered with the ID
     */
    Component getComponent(String componentID) throws ComponentNotFoundException;

    /**
     * Return a map of component ID's to <code>Component</code> objects.
     *
     * @return all registered components, mapped by ID
     */
    Map<String, Component> getComponents();

    /**
     * Short-cut method to retrieve a partner factory.
     *
     * @return the currently registered <code>PartnerFactory</code> component
     * @throws ComponentNotFoundException If a <code>PartnerFactory</code> component has not been registered
     * @see PartnershipFactory
     * @see Component
     */
    PartnershipFactory getPartnershipFactory() throws ComponentNotFoundException;

    /**
     * Short-cut method to retrieve a message factory.
     *
     * @return the currently registered <code>MessageFactory</code> component
     * @throws ComponentNotFoundException If a <code>MessageFactory</code> component has not been registered
     * @see MessageFactory
     * @see Component
     */
    MessageFactory getMessageFactory() throws ComponentNotFoundException;

    /**
     * Short-cut method to retrieve a processor.
     *
     * @return the currently registered <code>Processor</code> component
     * @throws ComponentNotFoundException If a <code>Processor</code> component has not been registered
     * @see Processor
     * @see Component
     */
    Processor getProcessor() throws ComponentNotFoundException;

    /**
     * Ability to load a poller module from a partnership loader.
     *
     * @param moduleNode - XML Element node containing the config for the poller
     * @param partnershipName - name attribute value for the partnership node
     * @param configSource - will be "partnership" or "configModule"
     * @return void
     * @throws OpenAS2Exception If there are issues with the definition of the poller
     */
    public void loadPartnershipPoller(Node moduleNode, String partnershipName, String configSource) throws OpenAS2Exception;
    
    public void startPartnershipPollers() throws OpenAS2Exception;
    public void destroyPartnershipPollers();

    String getBaseDirectory();

    String getAppVersion();

    String getAppTitle();

}
