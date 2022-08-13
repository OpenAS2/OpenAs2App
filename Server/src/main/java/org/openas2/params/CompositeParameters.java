package org.openas2.params;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.openas2.util.Properties;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;


public class CompositeParameters extends ParameterParser {
    private Map<String, ParameterParser> parameterParsers;

    private Log logger = LogFactory.getLog(CompositeParameters.class.getSimpleName());

    public CompositeParameters(boolean returnEmptyStringForMissingParsers) {
        super();
        setReturnEmptyStringForMissingParsers(returnEmptyStringForMissingParsers);
    }

    public CompositeParameters(boolean returnEmptyStringForMissingParsers, Map<String, ParameterParser> parameterParsers) {
        super();
        setReturnEmptyStringForMissingParsers(returnEmptyStringForMissingParsers);
        getParameterParsers().putAll(parameterParsers);
    }

    public CompositeParameters add(String key, ParameterParser param) {
        getParameterParsers().put(key, param);
        return this;
    }

    public void setParameter(String key, String value) throws InvalidParameterException {
        StringTokenizer keyParts = new StringTokenizer(key, ".", false);

        keyParts.nextToken();
        ParameterParser parser = getParameterParsers().get(keyParts);

        if (parser != null) {
            if (!keyParts.hasMoreTokens()) {
                throw new InvalidParameterException("Invalid key format", this, key, null);
            }

            StringBuffer keyBuf = new StringBuffer(keyParts.nextToken());

            while (keyParts.hasMoreTokens()) {
                keyBuf.append(".");
                keyBuf.append(keyParts.nextToken());
            }

            parser.setParameter(keyBuf.toString(), value);
        } else if (!getReturnEmptyStringForMissingParsers()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to find a parser for: " + key + "  ::: Parser list: " + getParameterParsers().keySet().toString());
            }
            throw new InvalidParameterException("Invalid parser identifier", this, key, value);
        }
    }

    public String getParameter(String key) throws InvalidParameterException {
        StringTokenizer keyParts = new StringTokenizer(key, ".", false);

        String parserID = keyParts.nextToken();
        // support "properties" key for all parser calls
        if ("properties".equals(parserID)) {
            // The property value could be a period separated string so get the original and drop "properties."
            String propKey = key.replace("properties.", "");
            if (propKey == null) {
                throw new InvalidParameterException("Invalid property key format. Missing a property name.", this, key, null);
            }
            String val = Properties.getProperty(propKey, null);
            if (val == null) {
                throw new InvalidParameterException("Property is null when parsing property string to value", this, propKey, null);
            }
            return val;
        }
        ParameterParser parser = getParameterParsers().get(parserID);

        if (parser != null) {
            if (!keyParts.hasMoreTokens()) {
                throw new InvalidParameterException("Invalid key format", this, key, null);
            }

            StringBuffer keyBuf = new StringBuffer(keyParts.nextToken());

            while (keyParts.hasMoreTokens()) {
                keyBuf.append(".");
                keyBuf.append(keyParts.nextToken());
            }

            return parser.getParameter(keyBuf.toString());
        } else if (!getReturnEmptyStringForMissingParsers()) {
            if (logger.isInfoEnabled()) {
                logger.info("Failed to find a parser for: " + key + "  ::: Available parser list: " + getParameterParsers().keySet().toString());
            }
            throw new InvalidParameterException("Invalid parser identifier", this, key, null);
        } else {
            return "";
        }
    }

    public void setParameterParsers(Map<String, ParameterParser> parameterParsers) {
        this.parameterParsers = parameterParsers;
    }

    protected Map<String, ParameterParser> getParameterParsers() {
        if (parameterParsers == null) {
            parameterParsers = new HashMap<String, ParameterParser>();
        }

        return parameterParsers;
    }
}
