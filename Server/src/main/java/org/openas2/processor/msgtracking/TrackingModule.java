/* Copyright Uhuru Technology 2016 https://www.uhurutechnology.com
 * Distributed under the GPLv3 license or a commercial license must be acquired.
 */
package org.openas2.processor.msgtracking;

import org.openas2.processor.ActiveModule;


public interface TrackingModule extends ActiveModule {
    String DO_TRACK_MSG = "track_msg";
    String TRACK_MSG_TCP_SERVER = "track_msg_tcp_server";
}
