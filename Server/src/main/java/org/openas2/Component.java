package org.openas2;

import java.util.Map;


/**
 * The Component interface provides a standard way to dynamically create and
 * initialize an object. Component-based objects also have access to a
 * Session, which allow each component to access all components  registered to it's
 * Session.   Parameters for a component are defined as static strings   Note: Any
 * object that implements this interface must have a constructor with  no parameters, as these
 * parameters should be passed to the init  method.
 *
 * @author Aaron Silinskas
 * @see BaseComponent
 * @see Session
 */
public interface Component {
    /**
     * Returns a name for the component. These names are not guaranteed to  be unique, and are
     * intended for display and logging. Generally this is the class name of the
     * Component object, without package  information.
     *
     * @return name of the component
     */
    String getName();

    /**
     * Returns the parameters used to initialize this Component, and can also be used
     * to modify parameters.
     *
     * @return map of parameter name to parameter value
     */
    Map<String, String> getParameters();

    /**
     * Returns the Session used to initialize this Component. The
     * returned session is also used to locate other components if needed.
     *
     * @return this component's session
     */
    Session getSession();

    /**
     * Component lifecycle hook. After creating a Component object, this method should be called to set any
     * parameters used by the component. Component implementations typically have
     * required parameter checking and code to start timers and threads within this method.
     *
     * @param session    the component uses this object to access other components
     * @param parameters configuration values for the component
     * @throws OpenAS2Exception If an error occurs while initializing the component
     * @see Session
     */
    void init(Session session, Map<String, String> parameters) throws OpenAS2Exception;

    /**
     * Component lifecycle hook. If lifecycle of {@link Component} requires a destroy function this method can be used.
     *
     * @throws Exception Something went wrong
     * @see #init(Session, Map)
     * @see Session
     */
    void destroy() throws Exception;
}
