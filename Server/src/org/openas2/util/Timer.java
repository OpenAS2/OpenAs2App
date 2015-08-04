package org.openas2.util;

import java.util.Date;


public class Timer {
    private Date startTime;
    private long timeOut;

    public Timer(long timeOut) {
        this.timeOut = timeOut;
        startTime = new Date();
    }

    public void setStartTime(Date start) {
        startTime = start;
    }

    public Date getStartTime() {
        return startTime;
    }

    public void setTimeOut(long timeOut) {
        this.timeOut = timeOut;
    }

    public long getTimeOut() {
        return timeOut;
    }

    public void reset() {
        startTime = new Date();
    }

    public boolean isTimedOut() {
        Date timeOutDate = new Date(startTime.getTime() + (timeOut * 1000));

        return timeOutDate.before(new Date());
    }
}
