/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.openas2.processor.producer;

import org.openas2.message.AS2Message;

/**
 *
 * @author javier
 */
public interface ProducerModule {
    public AS2Message createMessage();
}
