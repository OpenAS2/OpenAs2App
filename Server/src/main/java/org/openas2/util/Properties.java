package org.openas2.util;

import java.util.HashMap;
import java.util.Map;

public class Properties {
    public static final String APP_VERSION_PROP = "app.version";
    public static final String APP_TITLE_PROP = "app.title";
    public static final String APP_BASE_DIR_PROP = "app.base.dir";
    public static final String HTTP_USER_AGENT_PROP = "http.user.agent";
    public static final String OPENAS2_PROPERTIES_FILE_PROP = "openas2.properties.file";

    public static final String AS2_MESSAGE_ID_FORMAT = "as2_message_id_format";
    public static final String AS2_MDN_MESSAGE_ID_FORMAT = "as2_mdn_message_id_format";
    public static final String AS2_MESSAGE_ID_ENCLOSE_IN_BRACKETS = "as2_message_id_enclose_in_brackets";
    public static final String AS2_RX_MESSAGE_FILENAME_FALLBACK = "as2_receive_message_filename_fallback";

    public static final String AS2_MDN_RESP_MAX_WAIT_SECS = "as2_mdn_response_max_wait_seconds";

    public static final String LOG_INVALID_HTTP_REQUEST = "log_invalid_http_request";

    private static final Map<String, String> _properties = new HashMap<String, String>();

    private static final java.util.Properties contentTypeMap = new java.util.Properties();

    public static java.util.Properties getContentTypeMap() {
        if (contentTypeMap.isEmpty()) {
            return null;
        }
        return contentTypeMap;
    }

    public static void setContentTypeMap(java.util.Properties contentTypeMappings) {
        contentTypeMap.putAll(contentTypeMappings);
    }

    public static void setProperties(Map<String, String> map) {
        _properties.putAll(map);
    }

    public static void setProperty(String prop, String val) {
        _properties.put(prop, val);
    }

    public static Map<String, String> getProperties() {
        return _properties;
    }

    public static String getProperty(String key, String fallback) {
        String val = _properties.get(key);
        if (val == null) {
            return fallback;
        }
        return val;
    }

}
