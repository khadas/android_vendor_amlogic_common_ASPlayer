package com.amlogic.asplayer.core;

import android.content.Context;
import android.os.Handler;
import android.os.SystemClock;

import com.amlogic.asplayer.api.IASPlayer;


abstract class Renderer {

    static final int RESET_REASON_NEW_POSITION = 1;
    static final int RESET_REASON_NO_MEDIA = 2;
    static final int RESET_REASON_BAD_DATA = 3;
    static final int RESET_REASON_NO_DATA = 4;
    static final int RESET_REASON_DISCONTINUITY = 5;
    static final int RESET_REASON_DECODERS_BLOCKED = 6;
    static final int RESET_REASON_RENDERER_CHANGED = 7;

    // for notification
    protected IASPlayer mPlayer;

    // output paths
    protected AudioOutputPath mAudioOutputPath;
    protected VideoOutputPath mVideoOutputPath;

    // video start or not
    protected boolean mVideoStarted;
    protected boolean mAudioStarted;

    // position
    protected long mRequestedPositionUs;
    protected boolean mRequestedPositionSet;
    private long mLastPositionUpdateMs;
    protected PositionHandler mPositionHandler;

    // speed
    protected double mSpeed;

    Renderer(RendererScheduler rendererScheduler) {
        mPlayer = rendererScheduler.getASPlayer();
        mVideoOutputPath = rendererScheduler.getVideoOutputPath();
        mAudioOutputPath = rendererScheduler.getAudioOutputPath();
        mPositionHandler = rendererScheduler.getPositionHandler();
    }

    void prepare(int id, Context context, Handler handler) {}
    void release() {}

    void startVideo() {
        mVideoStarted = true;
    }

    void stopVideo() {
        mVideoStarted = false;
    }

    boolean isVideoStarted() {
        return mVideoStarted;
    }

    void startAudio() {
        mAudioStarted = true;
    }

    void stopAudio() {
        mAudioStarted = false;
    }

    boolean iAudioStarted() {
        return mAudioStarted;
    }

    void setSpeed(Renderer previousRenderer, double speed) {
        mSpeed = speed;
    }

    void setPositionUs(long positionUs) {
        mRequestedPositionSet = true;
        mRequestedPositionUs = positionUs;
        mPositionHandler.setPositionUs(positionUs);
    }

    abstract long doSomeWork();

    abstract void reset(int reason);

    abstract protected void pumpFeederData();

    protected boolean updateFeederPosition() {
        if (!mRequestedPositionSet)
            return false;

        mRequestedPositionSet = false;
        reset(RESET_REASON_NEW_POSITION);
        return true;
    }

    protected void handleTimelineChange() {
    }

    protected void handleDiscontinuity() {
        reset(RESET_REASON_DISCONTINUITY);
    }

    protected void updatePositionHandler(final PositionHandler positionHandler,
                                         boolean timelineUpdated,
                                         boolean discontinuityDetected) {
        synchronized (positionHandler) {
            if (timelineUpdated || discontinuityDetected) {
                positionHandler.unsetOrigin();
                mLastPositionUpdateMs = 0;
            }
            if (!positionHandler.isOriginSet()) {
                mLastPositionUpdateMs = 0;
            }
            if ((timelineUpdated || (SystemClock.elapsedRealtime() - mLastPositionUpdateMs) > 1000)) {
                mLastPositionUpdateMs = SystemClock.elapsedRealtime();
            }
        }
    }

    protected void pumpFeederInfo() {
        // update read position in position handler
        updatePositionHandler(mPositionHandler, false, false);
    }

    protected void handleFeeding() {
        if (updateFeederPosition())
            return;

        pumpFeederInfo();

        pumpFeederData();
    }
}

