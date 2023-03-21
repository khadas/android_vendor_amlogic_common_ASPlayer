package com.amlogic.asplayer.core.ts;

public class TimelinePcr {
    public long position;
    public long timeUs;

    public TimelinePcr() {
        reset();
    }

    public void reset() {
        position = -1;
        timeUs = -1;
    }

    public void set(TimelinePcr pcr) {
        this.position = pcr.position;
        this.timeUs = pcr.timeUs;
    }

    public boolean isValid() {
        return position>=0 && timeUs>=0;
    }
}
