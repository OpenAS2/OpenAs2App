package com.greicodex.openas2.plugins.mq;

import java.io.InputStream;
import java.util.Map;

/**
 *
 * @author javier
 */
public interface ConsumerCallback {
    public void onMessage(Map<String,String> params,InputStream inputData);
}
