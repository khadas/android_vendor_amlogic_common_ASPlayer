package com.amlogic.asplayer.core.ts;

public class TimelineChunk {
    public TimelineChunk() {
        startPcr = new TimelinePcr();
        endPcr = new TimelinePcr();
    }

    public TimelinePcr startPcr;
    public TimelinePcr endPcr;

    public long startTimeUs;

    public long durationUs;
}
