/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
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
