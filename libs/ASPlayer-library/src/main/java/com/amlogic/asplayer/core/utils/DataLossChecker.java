/*
 * Copyright (c) 2019 Amlogic, Inc. All rights reserved.
 *
 * This source code is subject to the terms and conditions defined in the
 * file 'LICENSE' which is part of this source code package.
 *
 * Description:
 */
package com.amlogic.asplayer.core.utils;

import android.os.Handler;

import com.amlogic.asplayer.core.ASPlayerLog;

import java.util.concurrent.atomic.AtomicInteger;

public class DataLossChecker {

    private Handler mHandler;
    private long mCheckPeriodMilliSecond;
    private long mDataLossGapMilliSecond;
    private String mLogTag;
    private String mHandlerToken;
    private DataLossListener mDataLossListener;

    private CheckLossRunnable mCheckLossRunnable;

    private long mStartCheckTimestamp;
    private long mLastFrameRenderTime;
    private long mLastFrameTimestampMillisecond;
    private boolean mDataLossReported;
    private long mLastDataLossReportTimestamp;

    private static AtomicInteger sId = new AtomicInteger();
    private final int mId;

    public interface DataLossListener {
        void onDataLossFound();
        void onDataResumeFound();
    }

    public DataLossChecker(Handler handler) {
        super();
        mHandler = handler;
        mId = sId.incrementAndGet();
        mHandlerToken = String.format("DataLossChecker-%d", mId);
    }

    public void start(DataLossListener listener,
                      String logTag,
                      long checkPeriodMilliSecond,
                      long dataLossGapMilliSecond) {
        if (mHandler == null || !(checkPeriodMilliSecond > 0)) {
            return;
        }

        mHandler.postDelayed(() -> {
            startCheckDataLoss(listener, logTag, checkPeriodMilliSecond, dataLossGapMilliSecond);
        }, mHandlerToken, 0);
    }

    private void startCheckDataLoss(DataLossListener listener,
                                    String logTag,
                                    long checkPeriodMilliSecond,
                                    long dataLossGapMilliSecond) {
        resetState();

        mDataLossListener = listener;
        mLogTag = logTag;
        mCheckPeriodMilliSecond = checkPeriodMilliSecond;
        mDataLossGapMilliSecond = dataLossGapMilliSecond;

        mStartCheckTimestamp = System.nanoTime() / 1000000;

        if (mCheckLossRunnable != null) {
            mHandler.removeCallbacks(mCheckLossRunnable);
        } else {
            mCheckLossRunnable = new CheckLossRunnable();
        }

        if (mCheckPeriodMilliSecond > 0) {
            mHandler.postDelayed(mCheckLossRunnable, mHandlerToken, mCheckPeriodMilliSecond);
        }
    }

    public void stop() {
        stopCheckDataLoss();

        mDataLossListener = null;
        mLogTag = null;

        resetState();
    }

    private void resetState() {
        mStartCheckTimestamp = -1;
        mLastFrameRenderTime = -1;
        mLastFrameTimestampMillisecond = -1;
        mDataLossReported = false;
        mLastDataLossReportTimestamp = -1;
    }

    private void stopCheckDataLoss() {
        if (mCheckLossRunnable != null) {
            if (mHandler != null) {
                mHandler.removeCallbacks(mCheckLossRunnable);
            }
            mCheckLossRunnable = null;
        }
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(mHandlerToken);
        }
    }

    public void release() {
        stop();

        mCheckLossRunnable = null;
        mHandler = null;
    }

    public void onFrameArrived(long renderTime) {
        if (mHandler != null) {
            mHandler.postDelayed(() -> {
                if (renderTime == mLastFrameRenderTime) {
                    // same frame?
                    return;
                }

                mLastFrameRenderTime = renderTime;
                mLastFrameTimestampMillisecond = System.nanoTime() / 1000000;

                if (mDataLossReported) {
                    ASPlayerLog.i("%s data resume", mLogTag);
                    notifyDataResume();
                    mDataLossReported = false;
                }
            }, mHandlerToken, 0);
        }
    }

    private void notifyDataLoss() {
        if (mDataLossListener != null) {
            mDataLossListener.onDataLossFound();
        }
    }

    private void notifyDataResume() {
        if (mDataLossListener != null) {
            mDataLossListener.onDataResumeFound();
        }
    }

    private class CheckLossRunnable implements Runnable {

        @Override
        public void run() {
            boolean isDataLoss = false;

            long currentTime = System.nanoTime() / 1000000;
            if (mLastFrameTimestampMillisecond == -1) {
                // no frame rendered
//                ASPlayerLog.i("%s check data loss, no last frame info", mLogTag);
                long startTime = mStartCheckTimestamp >= 0 ? mStartCheckTimestamp : 0;
                long delayTime = currentTime - startTime;
                if (delayTime >= mDataLossGapMilliSecond) {
                    isDataLoss = true;
                }
            } else if (mLastFrameTimestampMillisecond > 0
                    && currentTime >= (mLastFrameTimestampMillisecond + mDataLossGapMilliSecond)) {
                ASPlayerLog.i("%s check data loss, frame gap: %d", mLogTag, currentTime - mLastFrameTimestampMillisecond);
                isDataLoss = true;
            }

            if (isDataLoss) {
                ASPlayerLog.i("%s check data loss, data loss found", mLogTag);
                boolean reportDataLoss = !mDataLossReported;
                if (currentTime >= (mLastDataLossReportTimestamp + mDataLossGapMilliSecond)) {
                    reportDataLoss = true;
                }

                if (reportDataLoss) {
                    notifyDataLoss();
                    mDataLossReported = true;
                    mLastDataLossReportTimestamp = currentTime;
                }
            }

            if (mHandler != null && mCheckPeriodMilliSecond > 0) {
                if (mHandler.hasCallbacks(this)) {
                    mHandler.removeCallbacks(this);
                }
                mHandler.postDelayed(this, mHandlerToken, mCheckPeriodMilliSecond);
            }
        }
    }
}
