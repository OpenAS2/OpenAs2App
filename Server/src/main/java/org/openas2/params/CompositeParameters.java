package org.openas2.params;


import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;


public class CompositeParameters extends ParameterParser {
    private Map<String, ParameterParser> parameterParsers;
    private boolean ignoreMissingParsers;
    private Log logger = LogFactory.getLog(CompositeParameters.class.getSimpleName());

    public CompositeParameters(boolean ignoreMissingParsers) {
        super();
        this.ignoreMissingParsers = ignoreMissingParsers;
    }

    public CompositeParameters(boolean ignoreMissingParsers, Map<String, ParameterParser> parameterParsers) {
        super();
        this.ignoreMissingParsers = ignoreMissingParsers;
        getParameterParsers().putAll(parameterParsers);
    }

    public CompositeParameters add(String key, ParameterParser param) {
        getParameterParsers().put(key, param);
        return this;
    }

    public void setIgnoreMissingParsers(boolean ignoreMissingParsers) {
        this.ignoreMissingParsers = ignoreMissingParsers;
    }

    public boolean getIgnoreMissingParsers() {
        return ignoreMissingParsers;
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
        } else if (!getIgnoreMissingParsers()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to find a parser for: " + key + "  ::: Parser list: " + getParameterParsers().keySet().toString());
            }
            throw new InvalidParameterException("Invalid parser identifier", this, key, value);
        }
    }

    public String getParameter(String key) throws InvalidParameterException {
        StringTokenizer keyParts = new StringTokenizer(key, ".", false);

        String parserID = keyParts.nextToken();
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
        } else if (!getIgnoreMissingParsers()) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to find a parser for: " + key + "  ::: Parser list: " + getParameterParsers().keySet().toString());
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
