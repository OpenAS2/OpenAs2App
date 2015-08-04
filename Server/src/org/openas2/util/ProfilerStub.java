package org.openas2.util;

import java.util.Date;


public class ProfilerStub {
    private Date endStamp;
    private Date startStamp;

    public ProfilerStub(Date startStamp) {
        this.startStamp = startStamp;
    }

    public void setEndStamp(Date date) {
        endStamp = date;
    }

    public Date getEndStamp() {
        return endStamp;
    }
	
	public long getDifference() {
		return getEndStamp().getTime() - getStartStamp().getTime();
	}
    public String getMilliseconds() {        
        return Long.toString(getDifference()) + " milliseconds";
    }

    public String getSeconds() {
        long diff = getDifference() / 1000;
        
        return Long.toString(diff) + " seconds";
    }
	
	public String getCombined() {
		long diff = getDifference();
		
		return Long.toString(diff / 1000) + "." + Long.toString(diff % 1000) + " seconds";	
	}
	
    public void setStartStamp(Date date) {
        startStamp = date;
    }

    public Date getStartStamp() {
        return startStamp;
    }
}
