package org.openas2.util;

import java.util.Date;


public class Profiler {

    public static ProfilerStub startProfile() {
        return new ProfilerStub(new Date());
    }

    public static void endProfile(ProfilerStub stub) {
        stub.setEndStamp(new Date());
    }
}
