package org.openas2.params;

import java.text.DecimalFormat;

import org.openas2.util.RandomUtil;

public class RandomParameters extends ParameterParser {
    public void setParameter(String key, String value) throws InvalidParameterException {
        throw new InvalidParameterException("Set not supported", this, key, value);
    }

    public String getParameter(String key) throws InvalidParameterException {
        if (key == null) {
            throw new InvalidParameterException("Invalid key", this, key, null);
        }
        
        int wanted = key.length();
        String fmt = "";
        int max = 1;
        
        while (wanted-- > 0) {
        	fmt += "0";
        	max *= 10;
        }

        DecimalFormat randomFormatter = new DecimalFormat(fmt);
        return randomFormatter.format(RandomUtil.getRandomGenerator().nextInt(max));
    }
}
 	  	 
