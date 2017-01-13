package org.openas2.osgi;

import org.openas2.app.OpenAS2Server;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceRegistration;

public class Activator implements BundleActivator {

	private static BundleContext context;
	private ServiceRegistration<?> openAS2Registration;

	static BundleContext getContext() {
		return context;
	}

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#start(org.osgi.framework.BundleContext)
	 */
	public void start(BundleContext bundleContext) throws Exception {
		Activator.context = bundleContext;
		OpenAS2Server openAS2Service = new OpenAS2Server();
		openAS2Registration = bundleContext.registerService(OpenAS2Server.class.getName(), openAS2Service, null);
		openAS2Service.start(null);
	}

	/*
	 * (non-Javadoc)
	 * @see org.osgi.framework.BundleActivator#stop(org.osgi.framework.BundleContext)
	 */
	public void stop(BundleContext bundleContext) throws Exception {
		openAS2Registration.unregister();
		Activator.context = null;
	}

}
