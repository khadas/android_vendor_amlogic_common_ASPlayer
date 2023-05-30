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

    PositionHandler() {
        mTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    synchronized void reset() {
        ASPlayerLog.i("reset");
        mOriginSet = false;
        mPositionUs = 0;

        mLastPositionLog = 0;
        mLastPositionUpdate = 0;
    }

    synchronized void unsetOrigin() {
        ASPlayerLog.i("unsetOrigin");
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
        if (DEBUG) ASPlayerLog.i("positionUs: %d", positionUs);
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
        ASPlayerLog.i(String.format("current: %d, %s",
                mPositionUs, formatTime(mPositionUs / 1000)));
    }

    private String formatTime(long timeMs) {
        return mTimeFormat.format(timeMs);
    }
}