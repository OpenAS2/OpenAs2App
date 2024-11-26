package org.openas2.processor.sender;

import java.util.HashMap;
import java.util.Map;

import org.openas2.ComponentNotFoundException;
import org.openas2.OpenAS2Exception;
import org.openas2.cert.CertificateFactory;
import org.openas2.cert.X509CertificateFactory;
import org.openas2.util.HTTPUtil;

public abstract class HttpSenderModule extends BaseSenderModule {

    public static final String PARAM_READ_TIMEOUT = "readtimeout";
    public static final String PARAM_CONNECT_TIMEOUT = "connecttimeout";
    public static final String PARAM_SOCKET_TIMEOUT = "sockettimeout";
    public static final String PARAM_CUSTOM_SSL_TRUST_STORE = "custom_ssl_trust_store";

    // private Logger logger = LoggerFactory.getLogger(HttpSenderModule.class);

    public Map<String, Object> getHttpOptions() throws OpenAS2Exception {
        Map<String, Object> options = new HashMap<String, Object>();
        options.put(HTTPUtil.PARAM_READ_TIMEOUT, getParameter(PARAM_READ_TIMEOUT, "60000"));
        options.put(HTTPUtil.PARAM_CONNECT_TIMEOUT, getParameter(PARAM_CONNECT_TIMEOUT, "60000"));
        options.put(HTTPUtil.PARAM_SOCKET_TIMEOUT, getParameter(PARAM_SOCKET_TIMEOUT, "60000"));
        try {
            X509CertificateFactory cf = (X509CertificateFactory) getSession().getCertificateFactory(CertificateFactory.COMPID_SSL_TRUST_CERTIFICATE_FACTORY);
            if (cf != null) {
                options.put(PARAM_CUSTOM_SSL_TRUST_STORE, cf.getKeyStore());
            }
        } catch (ComponentNotFoundException e) {
        }
        return options;
    }
}
