package org.openas2.lib.util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openas2.OpenAS2Exception;


/**
 * Supports replacing strings containing environment variable references wiht he value of the env var.
 */
public class StringEnvVarReplacer {
    private static final Pattern ENV_VAR_PATTERN = Pattern.compile("\\$ENV\\{([^\\}]++)\\}");
    private Map<String, String> envVars;
    private final String HOME_DIR_PLACEHOLDER = "%home%";
    private String appHomeDir = null;

    public StringEnvVarReplacer() {
        super();
        this.envVars = System.getenv();
    }

    public StringEnvVarReplacer(Map<String, String> env_vars) {
        super();
        this.envVars = env_vars;
    }

    public void setEnvVarMap(Map<String, String> envVarMap) {
        this.envVars = envVarMap;
    }

    public void setAppHomeDir(String appHomeDir) {
        this.appHomeDir = appHomeDir;
    }

    public String replace(String input) throws OpenAS2Exception {
        if (this.appHomeDir != null) {
            input = input.replace(this.HOME_DIR_PLACEHOLDER, this.appHomeDir);
        }
        StringBuffer strBuf = new StringBuffer();
        Matcher matcher = ENV_VAR_PATTERN.matcher(input);
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = envVars.get(key);

            if (value == null) {
                throw new OpenAS2Exception("Missing environment variable for replacement: " + matcher.group() + " Using key: " + key);
            } else {
                matcher.appendReplacement(strBuf, Matcher.quoteReplacement(value));
            }
        }
        matcher.appendTail(strBuf);

        return strBuf.toString();
    }

}
