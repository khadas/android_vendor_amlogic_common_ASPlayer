package com.amlogic.asplayer.core;

import android.os.SystemClock;


import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

class PositionHandler {
    private static final boolean DEBUG = false;
    private static final int POSITION_OUT_OF_BOUNDS_MARGIN_US = 5000000;
    private static final int POSITION_END_MARGIN_US = 1000000;
    private static final long INTERVAL_POSITION_LOG = 1000;
    private static final long INTERVAL_POSITION_UPDATE = 200;

    private final SimpleDateFormat mTimeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    private long mLastPositionLog;
    private long mLastPositionUpdate;

    private long mStartTimeUs;
    private long mEndTimeUs;

    private boolean mOriginSet;
    private long mOriginTimestampUs;
    private long mOriginTimestampOffsetUs;

    private long mPositionUs;

    private long mEndOfDataTime;
    private long mEndOfDataPositionUs;

    PositionHandler() {
        mTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    synchronized void reset() {
        ASPlayerLog.i("reset");
        mOriginSet = false;
        mOriginTimestampUs = 0;
        mOriginTimestampOffsetUs = 0;
        mPositionUs = 0;
        mEndOfDataTime = 0;

        mLastPositionLog = 0;
        mLastPositionUpdate = 0;
    }

    synchronized void setOrigin(long originTimestampUs, long originOffsetUs) {
        ASPlayerLog.i("timestampUs: " + originTimestampUs + ", offsetUs: " + originOffsetUs);
        mOriginTimestampUs = originTimestampUs;
        mOriginTimestampOffsetUs = originOffsetUs;
        mOriginSet = true;
    }

    synchronized void unsetOrigin() {
        ASPlayerLog.i("unsetOrigin");
        mOriginSet = false;
    }

    synchronized void setEndOfData() {
        ASPlayerLog.i("setEndOfData");
        mEndOfDataTime = SystemClock.elapsedRealtime();
        mEndOfDataPositionUs = mPositionUs;
    }

    synchronized boolean isOriginSet() {
        return mOriginSet;
    }

    synchronized boolean isUpdateNeeded() {
        return SystemClock.elapsedRealtime() - mLastPositionUpdate > INTERVAL_POSITION_UPDATE;
    }

    synchronized void setPresentationTimestampUs(long timestampUs) {
        if (!mOriginSet)
            return;

        if (DEBUG) ASPlayerLog.i("timestampUs: " + timestampUs);

        long deltaDisplayTimeInChunkUs = timestampUs - mOriginTimestampUs;
        long positionUs = deltaDisplayTimeInChunkUs + mOriginTimestampOffsetUs;

        if (DEBUG) ASPlayerLog.i("positionUs: " + positionUs);

        if (positionUs < (mStartTimeUs - POSITION_OUT_OF_BOUNDS_MARGIN_US) ||
                positionUs > (mEndTimeUs + POSITION_OUT_OF_BOUNDS_MARGIN_US)) {
            ASPlayerLog.w("new position seems not correct:%d [%d, %d], timestamp:%d origin-timestamp:%d, delta:%d, origin-offset:%d => ignored",
                    positionUs / 1000, mStartTimeUs / 1000, mEndTimeUs / 1000,
                    timestampUs / 1000, mOriginTimestampUs / 1000,
                    deltaDisplayTimeInChunkUs / 1000,
                    mOriginTimestampOffsetUs / 1000);
        } else {
            mPositionUs = positionUs;
        }

        if (DEBUG) {
            logPosition();
        }

        mLastPositionUpdate = SystemClock.elapsedRealtime();
    }

    synchronized void setPositionUs(long positionUs) {
        if (DEBUG) ASPlayerLog.i("positionUs: " + positionUs);
        mPositionUs = positionUs;
    }

    synchronized void setStartPositionUs(long timestampUs) {
        if (DEBUG) ASPlayerLog.i("timestampUs: " + timestampUs);
        mStartTimeUs = timestampUs;
    }

    synchronized void setEndPositionUs(long timestampUs) {
        if (DEBUG) ASPlayerLog.i("timestampUs: " + timestampUs);
        mEndTimeUs = timestampUs;
    }

    synchronized long getStartPositionUs() {
        if (DEBUG) ASPlayerLog.i("mStartTimeUs: " + formatTime(mStartTimeUs/1000));
        if (mStartTimeUs >= 0)
            return mStartTimeUs;
        else
            return getPositionUs();
    }

    synchronized long getEndPositionUs() {
        if (DEBUG) ASPlayerLog.i("mEndTimeUs: " + formatTime(mEndTimeUs/1000));
        if (mEndTimeUs >= 0)
            return mEndTimeUs;
        else
            return getPositionUs();
    }

    synchronized long getPositionUs() {
        checkEndOfData();

        if (DEBUG ||
                mLastPositionLog + INTERVAL_POSITION_LOG
                        < SystemClock.elapsedRealtime()) {
            logPosition();
            mLastPositionLog = SystemClock.elapsedRealtime();
        }
        if (mEndTimeUs >= 0)
            mPositionUs = Math.min(mPositionUs, mEndTimeUs);
        if (mStartTimeUs >= 0)
            mPositionUs = Math.max(mPositionUs, mStartTimeUs);
        return mPositionUs;
    }

    private void checkEndOfData() {
        if (mEndOfDataTime != 0 && mPositionUs != mEndTimeUs) {
            long timeElapsedFromEndOfData = SystemClock.elapsedRealtime() - mEndOfDataTime;
            long positionDiffFromEndOfData = (mPositionUs - mEndOfDataPositionUs) / 1000;
            ASPlayerLog.i("timeElapsedFromEndOfData: %d, positionDiff: %d",
                    timeElapsedFromEndOfData, positionDiffFromEndOfData);
            if (timeElapsedFromEndOfData - positionDiffFromEndOfData
                    > POSITION_END_MARGIN_US / 1000) {
                mPositionUs = mEndTimeUs;
            }
        }
    }

    private void logPosition() {
        ASPlayerLog.i(String.format("[%s %s] current: %s",
                formatTime(mStartTimeUs / 1000),
                formatTime(mEndTimeUs / 1000),
                formatTime(mPositionUs / 1000)));
        ASPlayerLog.i(String.format("[%s %s] current: %s",
                mStartTimeUs,
                mEndTimeUs,
                mPositionUs));
    }

    private String formatTime(long timeMs) {
        return mTimeFormat.format(timeMs);
    }
}

