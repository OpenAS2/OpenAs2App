package org.openas2;

import java.util.Map;

import org.openas2.cert.CertificateFactory;
import org.openas2.partner.PartnershipFactory;
import org.openas2.processor.Processor;


/**
 * The <code>Session</code> interface provides configuration and resource information, and a means for
 * components to access the functionality of other components.
 *
 * @author Aaron Silinskas
 *
 * @see Component
 * @see org.openas2.cert.CertificateFactory
 * @see org.openas2.partner.PartnerFactory
 * @see org.openas2.processor.Processor 
 */
public interface Session {
    /** Official OpenAS2 release version */
    public static final String VERSION = "1.3.4";

    /** Official OpenAS2 title */
    public static final String TITLE = "OpenAS2 v" + VERSION;

    /**
     * Short-cut method to retrieve a certificate factory.
     *
     * @return the currently registered <code>CertificateFactory</code> component
     *
     * @throws ComponentNotFound If a <code>CertificateFactory</code> component has not been
     *         registered
     *
     * @see CertificateFactory
     * @see Component
     */
    public CertificateFactory getCertificateFactory() throws ComponentNotFoundException;

    /**
     * Registers a component to a specified ID.
     *
     * @param componentID registers the component to this ID
     * @param comp component to register
     *
     * @see Component
     */
    public void setComponent(String componentID, Component comp);

    /**
     * Gets the <code>Component</code> currently registered with an ID
     *
     * @param componentID ID to search for
     *
     * @return the component registered to the ID or null
     *
     * @throws ComponentNotFound If a component is not registered with the ID
     */
    public Component getComponent(String componentID) throws ComponentNotFoundException;

    /**
     * Return a map of component ID's to <code>Component</code> objects.
     *
     * @return all registered components, mapped by ID
     */
    public Map<String,Component> getComponents();

    /**
     * Short-cut method to retrieve a partner factory.
     *
     * @return the currently registered <code>PartnerFactory</code> component
     *
     * @throws ComponentNotFound If a <code>PartnerFactory</code> component has not been registered
     *
     * @see PartnershipFactory
     * @see Component
     */
    public PartnershipFactory getPartnershipFactory() throws ComponentNotFoundException;

    /**
     * Short-cut method to retrieve a processor.
     *
     * @return the currently registered <code>Processor</code> component
     *
     * @throws ComponentNotFound If a <code>Processor</code> component has not been registered
     *
     * @see Processor
     * @see Component
     */
    public Processor getProcessor() throws ComponentNotFoundException;
}
