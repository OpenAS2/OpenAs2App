package org.openas2.processor.sender;

import java.util.HashMap;
import java.util.Map;

import org.openas2.ComponentNotFoundException;
import org.openas2.OpenAS2Exception;
import org.openas2.cert.CertificateFactory;
import org.openas2.cert.X509CertificateFactory;
import org.openas2.partner.Partnership;
import org.openas2.util.HTTPUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class HttpSenderModule extends BaseSenderModule {

    /*
     * The original name for the socket timeout, still used by the shipped config.xml and the HowTo.
     * It was mapped to the wrong HttpClient setting and then replaced by "sockettimeout", which left it
     * read but never applied, so it is honoured as the socket timeout when that is not set.
     */
    public static final String PARAM_READ_TIMEOUT = "readtimeout";
    public static final String PARAM_CONNECT_TIMEOUT = "connecttimeout";
    public static final String PARAM_SOCKET_TIMEOUT = "sockettimeout";
    public static final String DEFAULT_TIMEOUT_MILLIS = "60000";
    public static final String PARAM_CUSTOM_SSL_TRUST_STORE = "custom_ssl_trust_store";

    private static final Logger LOG = LoggerFactory.getLogger(HttpSenderModule.class);

    public Map<String, Object> getHttpOptions() throws OpenAS2Exception {
        Map<String, Object> options = new HashMap<String, Object>();
        options.put(HTTPUtil.PARAM_READ_TIMEOUT, getParameter(PARAM_READ_TIMEOUT, DEFAULT_TIMEOUT_MILLIS));
        options.put(HTTPUtil.PARAM_CONNECT_TIMEOUT, getParameter(PARAM_CONNECT_TIMEOUT, DEFAULT_TIMEOUT_MILLIS));
        options.put(HTTPUtil.PARAM_SOCKET_TIMEOUT, getParameter(PARAM_SOCKET_TIMEOUT, getParameter(PARAM_READ_TIMEOUT, DEFAULT_TIMEOUT_MILLIS)));
        try {
            X509CertificateFactory cf = (X509CertificateFactory) getSession().getCertificateFactory(CertificateFactory.COMPID_SSL_TRUST_CERTIFICATE_FACTORY);
            if (cf != null) {
                options.put(PARAM_CUSTOM_SSL_TRUST_STORE, cf.getKeyStore());
            }
        } catch (ComponentNotFoundException e) {
        }
        return options;
    }

    /**
     * Builds the HTTP options for sending to the partner of a partnership: the module's settings, with
     * the connect and socket timeouts overridden by the partnership's own "connecttimeout" and
     * "sockettimeout" attributes where it sets them, so one slow partner can be given longer without
     * raising the timeout for every partner.
     *
     * @param partnership - the partnership being sent on
     * @return the options to pass to HTTPUtil
     * @throws OpenAS2Exception if the module's own options cannot be built
     */
    public Map<String, Object> getHttpOptions(Partnership partnership) throws OpenAS2Exception {
        Map<String, Object> options = getHttpOptions();
        overrideTimeout(options, partnership, PARAM_CONNECT_TIMEOUT, HTTPUtil.PARAM_CONNECT_TIMEOUT);
        overrideTimeout(options, partnership, PARAM_SOCKET_TIMEOUT, HTTPUtil.PARAM_SOCKET_TIMEOUT);
        return options;
    }

    private void overrideTimeout(Map<String, Object> options, Partnership partnership, String attribute, String option) {
        String value = partnership.getAttribute(attribute);
        if (value == null || value.trim().isEmpty()) {
            return;
        }
        try {
            if (Integer.parseInt(value.trim()) < 0) {
                throw new NumberFormatException("negative");
            }
        } catch (NumberFormatException e) {
            // A bad value would otherwise fail every send to this partner when the request is built
            LOG.warn("Ignoring the \"" + attribute + "\" attribute of partnership " + partnership.getName() + ": \"" + value
                    + "\" is not a number of milliseconds. Using " + options.get(option) + " instead.");
            return;
        }
        options.put(option, value.trim());
    }
}
