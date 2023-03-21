/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.core;

import android.media.tv.tuner.Tuner;
import android.media.tv.tuner.dvr.DvrPlayback;
import android.media.tv.tuner.dvr.DvrSettings;
import android.media.tv.tuner.dvr.OnPlaybackStatusChangedListener;
import android.media.tv.tuner.filter.Filter;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;

import java.util.concurrent.TimeUnit;


public class TsPlayback {

    private static final String TAG = Constant.LOG_TAG + "_TsPlayback";

    public interface OnPlaybackStatusChangeListener {
        void onPlaybackStatusChanged(int status);
    }

    private Tuner mTuner;
    private DvrPlayback mDvrPlayback;

    private int mId;

    public TsPlayback(int id, Tuner tuner, long bufferSize) {
        mId = id;
        mTuner = tuner;

        mDvrPlayback = mTuner.openDvrPlayback(bufferSize, Runnable::run, new OnPlaybackStatusChangedListener() {
            final long LOG_DURATION = TimeUnit.SECONDS.toMillis(1);
            long mLastStatusLog;
            int mLastStatus;

            @Override
            public void onPlaybackStatusChanged(int status) {
                if (mLastStatus != status ||
                        SystemClock.elapsedRealtime() - mLastStatusLog > LOG_DURATION) {
                    ASPlayerLog.i("TsPlayback-%d onPlaybackStatusChanged, status: %d", mId, status);
                    mLastStatusLog = SystemClock.elapsedRealtime();
                }
                mLastStatus = status;
            }
        });
    }

    public int attachFilter(Filter filter) {
        if (mDvrPlayback != null) {
            return mDvrPlayback.attachFilter(filter);
        }
        return -1;
    }

    public int detachFilter(Filter filter) {
        if (mDvrPlayback != null) {
            return mDvrPlayback.detachFilter(filter);
        }
        return -1;
    }

    public int configure(DvrSettings dvrSettings) {
        ASPlayerLog.i("%s-%d configure called", TAG, mId);
        if (mDvrPlayback != null) {
            return mDvrPlayback.configure(dvrSettings);
        } else {
            ASPlayerLog.e("%s-%d configure failed DvrPlayback is null", TAG, mId);
        }
        return -1;
    }

    public int start() {
        ASPlayerLog.i("%s-%d start called", TAG, mId);
        if (mDvrPlayback != null) {
            return mDvrPlayback.start();
        } else {
            ASPlayerLog.e("%s-%d start failed DvrPlayback is null", TAG, mId);
        }
        return -1;
    }

    public int stop() {
        ASPlayerLog.i("%s-%d stop called", TAG, mId);
        if (mDvrPlayback != null) {
            return mDvrPlayback.stop();
        } else {
            ASPlayerLog.e("%s-%d stop failed DvrPlayback is null", TAG, mId);
        }
        return -1;
    }

    public int flush() {
        ASPlayerLog.i("%s-%d flush called", TAG, mId);
        if (mDvrPlayback != null) {
            return mDvrPlayback.flush();
        } else {
            ASPlayerLog.e("%s-%d flush failed DvrPlayback is null", TAG, mId);
        }
        return -1;
    }

    private void close() {
        ASPlayerLog.i("%s-%d close called", TAG, mId);
        if (mDvrPlayback != null) {
            mDvrPlayback.stop();
            mDvrPlayback.flush();
            mDvrPlayback.close();
            mDvrPlayback = null;
            ASPlayerLog.i("%s-%d close DvrPlayback", TAG, mId);
        }

        mTuner = null;
    }

    public void release() {
        close();

        mTuner = null;
    }

    public void setFileDescriptor(ParcelFileDescriptor fd) {
        if (mDvrPlayback != null) {
            mDvrPlayback.setFileDescriptor(fd);
        }
    }

    public long write(long size) {
        if (mDvrPlayback != null) {
            return mDvrPlayback.read(size);
        } else {
            ASPlayerLog.e("%s-%d write failed DvrPlayback is null, size: %d", TAG, mId, size);
        }
        return -1;
    }

    public long write(byte[] bytes, long offset, long size) {
        if (mDvrPlayback != null) {
            long ret = mDvrPlayback.read(bytes, offset, size);
//            ASPlayerLog.i("TsPlayback-%d write %d bytes, total size: %d", mId, ret, size);
            return ret;
        } else {
            ASPlayerLog.e("%s-%d write failed DvrPlayback is null, size: %d", TAG, mId, size);
        }
        return -1;
    }
}
