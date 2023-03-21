package com.amlogic.asplayer.core;

import android.os.SystemClock;

class MediaClock {

    private boolean mStarted;
    private long mOriginTimestampUs;
    private long mOriginToUs;
    private long mOffsetUs;
    private long mOriginSpeedUs;
    private long mOriginSpeedToUs;
    private double mSpeed;

    MediaClock() {
        mSpeed = 1.0;
    }

    /**
     * Stops the clock, and reset origin
     */
    public void reset() {
        mStarted = false;
        mOriginTimestampUs = 0;
        mOriginToUs = 0;
        mSpeed = 1.0;
        mOffsetUs = 0;
        mOriginSpeedUs = 0;
        mOriginSpeedToUs = 0;
    }

    /**
     * Returns if clock is started, aka if origin has been set
     *
     * @return true if clock is started
     */
    public boolean isStarted() {
        return mStarted;
    }

    /**
     * Sets an offset in Us
     *
     * @param offsetUs offset
     */
    public void setOffsetUs(long offsetUs) {
        mOffsetUs = offsetUs;
    }


    /**
     * Gets offset in Us
     *
     * @return offset in us
     */
    public long getOffsetUs() {
        return mOffsetUs;
    }

    /**
     * Sets the origin with timestamp and starts the clock
     *
     * @param timestampUs timestamp in microseconds of the origin of the clock
     *                    it must be positive
     */
    public void setOriginTimestampUs(long timestampUs) {
        mStarted = true;
        mOriginTimestampUs = timestampUs;
        mOriginToUs = SystemClock.elapsedRealtimeNanos() / 1000;
        mOffsetUs = 0;
        mOriginSpeedUs = timestampUs;
        mOriginSpeedToUs = mOriginToUs;
    }

    /**
     * Gets the origin with timestamp if clock is started
     *
     * @return Return timestamp in microseconds of the origin of the clock
     *                    it is positive
     */
    public long getOriginTimestampUs() {
        if (!mStarted)
            return 0;
        return mOriginTimestampUs;
    }


    /**
     * Sets speed
     *
     * @param speed speed factor
     */
    public void setSpeed(double speed) {
        mOriginSpeedUs = timeUs();
        mOriginSpeedToUs = SystemClock.elapsedRealtimeNanos() / 1000;
        mSpeed = speed;
    }

    /**
     * Gets speed
     *
     * @return speed factor
     */
    public double getSpeed() {
        return mSpeed;
    }

    /**
     * Returns current time in Us
     *
     * @return current time in us
     */
    public long timeUs() {
        if (!mStarted)
            return 0;
        long deltaUs = (long) (((SystemClock.elapsedRealtimeNanos() / 1000) - mOriginSpeedToUs) * mSpeed);
        return (mOriginSpeedUs + deltaUs + mOffsetUs);
    }

    /**
     * Time elapsed in us since origin has been set
     * It is a monotonic time
     *
     * @return elapsed time in us
     */
    public long elapsedTimeUs() {
        if (!mStarted)
            return 0;
        return (SystemClock.elapsedRealtimeNanos() / 1000 - mOriginToUs);
    }
}
