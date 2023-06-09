package com.amlogic.asplayer.core;

import android.os.SystemClock;


import java.text.SimpleDateFormat;
import java.util.Locale;
import java.util.TimeZone;

class PositionHandler {
    private static final boolean DEBUG = false;
    private static final long INTERVAL_POSITION_LOG = 1000;
    private static final long INTERVAL_POSITION_UPDATE = 200;

    private final SimpleDateFormat mTimeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

    private long mLastPositionLog;
    private long mLastPositionUpdate;

    private boolean mOriginSet;

    private long mPositionUs;

    final int mId;

    PositionHandler(int id) {
        mId = id;
        mTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    synchronized void reset() {
        ASPlayerLog.i("PositionHandler-%d reset", mId);
        mOriginSet = false;
        mPositionUs = 0;

        mLastPositionLog = 0;
        mLastPositionUpdate = 0;
    }

    synchronized void unsetOrigin() {
        ASPlayerLog.i("PositionHandler-%d unsetOrigin", mId);
        mOriginSet = false;
    }

    synchronized boolean isOriginSet() {
        return mOriginSet;
    }

    synchronized boolean isUpdateNeeded() {
        return SystemClock.elapsedRealtime() - mLastPositionUpdate > INTERVAL_POSITION_UPDATE;
    }

    synchronized void setPresentationTimestampUs(long timestampUs) {
        mPositionUs = timestampUs;

        mLastPositionUpdate = SystemClock.elapsedRealtime();
    }

    synchronized void setPositionUs(long positionUs) {
        if (DEBUG) ASPlayerLog.i("PositionHandler-%d positionUs: %d", mId, positionUs);
        mPositionUs = positionUs;
    }

    synchronized long getPositionUs() {
        if (DEBUG || mLastPositionLog + INTERVAL_POSITION_LOG < SystemClock.elapsedRealtime()) {
            logPosition();
            mLastPositionLog = SystemClock.elapsedRealtime();
        }
        return mPositionUs;
    }

    private void logPosition() {
        ASPlayerLog.i(String.format("PositionHandler-%d current: %d, %s",
                mId, mPositionUs, formatTime(mPositionUs / 1000)));
    }

    private String formatTime(long timeMs) {
        return mTimeFormat.format(timeMs);
    }
}